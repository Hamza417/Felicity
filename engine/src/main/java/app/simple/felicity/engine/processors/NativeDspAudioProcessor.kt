package app.simple.felicity.engine.processors

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import app.simple.felicity.engine.utils.PcmUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/**
 * A unified [AudioProcessor] that delegates the entire DSP chain — 10-band peaking EQ,
 * bass low-shelf, treble high-shelf, stereo widening (M/S), constant-power pan, and
 * tape-style saturation — to the native [DspProcessor] via a single JNI hot-path call.
 *
 * This processor consolidates six formerly separate Kotlin AudioProcessor slots (10-band EQ,
 * bass shelf, treble shelf, stereo widening, balance, and tape saturation) into a single
 * chain executed entirely inside the ARM NEON C++ engine — removing all per-effect
 * ByteBuffer allocation and JNI round-trip overhead from the audio hot path.
 *
 * The native engine also accumulates a per-frame mono downmix and, once the ring buffer
 * fills, applies the pre-computed Hann window, runs the PFFFT forward transform, and
 * stores per-band RMS magnitudes in the shared [FFTContext] so that the downstream
 * [VisualizerProcessor] always reflects the fully processed signal.
 *
 * Pre-amplification is applied on the Kotlin side before the native call as a simple
 * linear multiply, keeping the JNI interface lean. All other DSP parameters are stored
 * as Kotlin-side fields so that setter calls made before the first [configure] invocation
 * are remembered and applied atomically when the native context is (re)created.
 *
 * Hot-path allocation strategy: a single [workBuf] [FloatArray] is pre-allocated once
 * per audio format change and reused on every subsequent [queueInput] call. The only
 * reallocation is triggered when the chunk size changes — which is rare in practice.
 *
 * Supported encodings: PCM_16BIT, PCM_24BIT, PCM_32BIT, PCM_FLOAT.
 *
 * @param visualizerProcessor The [VisualizerProcessor] whose native [FFTContext] handle
 *                            is shared with the underlying [DspProcessor] so that the
 *                            spectrum display reflects the post-EQ, post-effects signal.
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class NativeDspAudioProcessor(
        private val visualizerProcessor: VisualizerProcessor
) : AudioProcessor {

    private var inputFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var active = false
    private var inputEnded = false
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER

    private var dspProcessor: DspProcessor? = null

    /**
     * Reusable float work buffer for converting PCM to/from the [FloatArray] expected by
     * [DspProcessor.processAudio]. Only reallocated when the audio chunk size changes.
     */
    private var workBuf = FloatArray(0)

    /**
     * Linear pre-amplifier gain applied to every sample before the native DSP chain.
     * Default 1.0 = unity (0 dB). Updated by [setPreamp].
     */
    @Volatile
    private var preampLinearGain: Float = 1f

    /**
     * Whether the EQ stage is enabled inside the native engine.
     * Mirrored to [DspProcessor] immediately on write.
     */
    @Volatile
    var eqEnabled: Boolean = true
        set(value) {
            field = value
            dspProcessor?.setEqEnabled(value)
        }

    /**
     * Per-band gain snapshot in dB, used to re-apply settings when the native context is
     * recreated after an audio-format change.
     */
    private var bandGains: FloatArray = FloatArray(BAND_COUNT)

    /** Bass low-shelf gain in dB, mirrored to the native engine on each update. */
    @Volatile
    private var bassDb: Float = 0f

    /** Treble high-shelf gain in dB, mirrored to the native engine on each update. */
    @Volatile
    private var trebleDb: Float = 0f

    /** Stereo width value in [0.0, 2.0], mirrored to the native engine on each update. */
    @Volatile
    private var stereoWidth: Float = 1f

    /** Pan value in [-1.0, 1.0], mirrored to the native engine on each update. */
    @Volatile
    private var pan: Float = 0f

    /** Saturation drive in [0.0, 4.0], mirrored to the native engine on each update. */
    @Volatile
    private var saturationDrive: Float = 0f

    /** Reverb wet/dry mix in [0.0, 1.0]; 0 = bypassed. */
    @Volatile
    private var reverbMix: Float = 0f

    /** Reverb decay time parameter in [0.0, 1.0]; 0 = very short, 1 = very long. */
    @Volatile
    private var reverbDecay: Float = 0.5f

    /**
     * High-frequency damping for the reverb tail, independent of [reverbDecay].
     * 0.0 = brightest tail (minimal absorption); 1.0 = darkest tail (heavy damping).
     */
    @Volatile
    private var reverbDamp: Float = 0.3f

    /** Room size parameter in [0.0, 1.0]; 0 = small room, 1 = large hall. */
    @Volatile
    private var reverbSize: Float = 0.5f

    /**
     * Returns the [AudioProcessor.AudioFormat] that this processor is currently configured for.
     *
     * Returns [AudioProcessor.AudioFormat.NOT_SET] when the processor has not yet received a
     * valid audio format (i.e., before [configure] is called or after [reset]).
     * Used by [AudioPipelineManager] to populate the DSP section of [AudioPipelineSnapshot].
     */
    val currentInputFormat: AudioProcessor.AudioFormat
        get() = inputFormat

    /**
     * Returns the current stereo width value in the range [0.0, 2.0].
     *
     * 0.0 = full mono, 1.0 = natural stereo passthrough, 2.0 = maximum widening.
     * Exposed so [AudioPipelineManager] can convert it to a percentage for the snapshot.
     */
    val currentStereoWidth: Float
        get() = stereoWidth

    /**
     * Configures the processor for [inputAudioFormat]. A new [DspProcessor] native context
     * is created (or reconfigured) on every format change; all stored parameters are
     * re-applied atomically so effect settings survive decoder switches and Hi-Res toggles.
     *
     * Activation requires one of the four PCM encodings supported by [PcmUtils].
     * Any other format (e.g., compressed audio) returns [AudioProcessor.AudioFormat.NOT_SET]
     * to signal Media3 to bypass this processor entirely.
     *
     * @param inputAudioFormat The audio format provided by the upstream pipeline.
     * @return The (unchanged) output format when active; [AudioProcessor.AudioFormat.NOT_SET] otherwise.
     */
    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        active = PcmUtils.isEncodingSupported(inputAudioFormat.encoding)

        if (!active) {
            inputFormat = AudioProcessor.AudioFormat.NOT_SET
            releaseNativeContext()
            return AudioProcessor.AudioFormat.NOT_SET
        }

        val formatChanged = inputAudioFormat.sampleRate != inputFormat.sampleRate ||
                inputAudioFormat.channelCount != inputFormat.channelCount

        inputFormat = inputAudioFormat

        if (dspProcessor == null || formatChanged) {
            releaseNativeContext()
            val newDsp = DspProcessor(
                    visualizerProcessor,
                    inputAudioFormat.sampleRate,
                    inputAudioFormat.channelCount
            )
            dspProcessor = if (newDsp.isReady) newDsp else null
            pushAllParameters()
        } else {
            dspProcessor?.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        }

        return inputAudioFormat
    }

    override fun isActive(): Boolean = active

    /**
     * Converts the incoming PCM chunk to a [FloatArray], optionally applies pre-amp, runs the
     * full native DSP chain in-place via [DspProcessor.processAudio], then re-encodes back to the
     * original PCM format.
     *
     * Float32 guarantee — every encoding is widened to 32-bit float BEFORE entering [workBuf]
     * and the native DSP chain. No 16-bit (or any integer) arithmetic is performed inside the
     * processing loop:
     *  - [C.ENCODING_PCM_16BIT]: [PcmUtils.readFloat] widens Short → Float via `/ 32768f` before
     *    the value lands in [workBuf]; the Short is never written back until [PcmUtils.writeFloat]
     *    at the very end.
     *  - [C.ENCODING_PCM_24BIT]: three raw bytes are assembled into an Int then divided by
     *    `8388608f` (Float) — the intermediate Int is only used for bit-shifting, not DSP.
     *  - [C.ENCODING_PCM_32BIT]: raw Int → Float via `/ 2.1474836E9f`; same pattern.
     *  - [C.ENCODING_PCM_FLOAT]: samples are bulk-copied as Float32 with no conversion.
     * All NEON SIMD stages inside [DspProcessor] operate on `float32x2_t` / `float32x4_t`
     * vectors — confirmed by inspection of [dsp-engine.cpp].
     *
     * For [C.ENCODING_PCM_FLOAT] with unity preamp, the [ByteBuffer] is read as a [FloatBuffer]
     * view directly into [workBuf] — the fastest possible path with zero per-sample overhead.
     *
     * @param inputBuffer Raw PCM data from the upstream processor; position is advanced by
     *                    exactly [ByteBuffer.remaining] bytes on return.
     */
    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!active) {
            outputBuffer = inputBuffer
            return
        }

        val dsp = dspProcessor
        if (dsp == null || !dsp.isReady) {
            outputBuffer = inputBuffer
            return
        }

        val encoding = inputFormat.encoding
        val bps = PcmUtils.bytesPerSample(encoding)
        val remaining = inputBuffer.remaining()
        val numSamples = remaining / bps

        if (numSamples == 0) {
            outputBuffer = inputBuffer
            return
        }

        /** Resize the work buffer only when the chunk size changes (rare outside of warmup). */
        if (workBuf.size != numSamples) {
            workBuf = FloatArray(numSamples)
        }

        val preamp = preampLinearGain

        /**
         * Input stage: all PCM encodings are converted to 32-bit float here.
         * After this block [workBuf] contains only Float32 values in the range
         * approximately [-1.0, 1.0] (float PCM may legally exceed this range with headroom).
         * No integer arithmetic crosses into the DSP path below.
         *
         * Fast path: for float PCM + unity gain (the common Hi-Res / DSP bypass case),
         * the [ByteBuffer] is bulk-read via [asFloatBuffer] — no per-sample loop needed.
         * The `preamp == 1f` check is exact because [preampLinearGain] is set to
         * `10f.pow(0f / 20f)` = `1.0f` exactly under IEEE 754 when the dB value is 0.
         */
        if (encoding == C.ENCODING_PCM_FLOAT && preamp == 1f) {
            inputBuffer.asFloatBuffer().get(workBuf)
            inputBuffer.position(inputBuffer.limit())
        } else {
            /**
             * General path: [PcmUtils.readFloat] always returns a 32-bit float.
             * The incoming Short / Int is widened to Float inside [readFloat] before
             * any arithmetic — it is never stored back as an integer.
             */
            for (i in 0 until numSamples) {
                workBuf[i] = PcmUtils.readFloat(inputBuffer, encoding) * preamp
            }
        }

        /**
         * DSP stage: entirely float32 — [workBuf] (FloatArray) is processed in-place by
         * the NEON-accelerated native engine. All biquad states, gain coefficients, and
         * intermediate SIMD vectors are 32-bit throughout (see [dsp-engine.cpp]).
         */
        dsp.processAudio(workBuf)

        /**
         * Output stage: float32 values in [workBuf] are scaled and packed back into the
         * target integer encoding only here — integers never appear earlier in the chain.
         */
        val buf = acquireOutputBuffer(remaining)

        if (encoding == C.ENCODING_PCM_FLOAT) {
            buf.asFloatBuffer().put(workBuf)
            buf.position(remaining)
        } else {
            for (i in 0 until numSamples) {
                PcmUtils.writeFloat(buf, workBuf[i], encoding)
            }
        }

        buf.flip()
        outputBuffer = buf
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    @Suppress("OVERRIDE_DEPRECATION")
    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        active = false
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
        workBuf = FloatArray(0)
        releaseNativeContext()
    }

    /**
     * Sets the pre-amplifier gain and recomputes the internal linear scale factor.
     *
     * @param db Gain in dB, clamped to [-15, +15]. 0 dB = unity (no change).
     */
    fun setPreamp(db: Float) {
        val clamped = db.coerceIn(-15f, 15f)
        preampLinearGain = 10f.pow(clamped / 20f)
    }

    /**
     * Sets all 10 EQ band gains together with the bass and treble shelf gains, then
     * pushes the combined update to the native engine in a single JNI call.
     *
     * @param gains   10-element [FloatArray] of dB gains, one per ISO band.
     * @param bassDb  Bass low-shelf gain in dB.
     * @param trebleDb Treble high-shelf gain in dB.
     */
    fun setEqBands(gains: FloatArray, bassDb: Float = this.bassDb, trebleDb: Float = this.trebleDb) {
        bandGains = gains.copyOf()
        this.bassDb = bassDb
        this.trebleDb = trebleDb
        dspProcessor?.setEqBands(bandGains, bassDb, trebleDb)
    }

    /**
     * Updates a single EQ band gain and pushes the full band state to the native engine.
     * Bass and treble gains are preserved from their last-set values.
     *
     * @param band   Zero-based band index in [0, 9] (31 Hz → 16 kHz).
     * @param gainDb Gain in dB clamped to [-15, +15].
     */
    fun setBandGain(band: Int, gainDb: Float) {
        if (band !in 0 until BAND_COUNT) return
        bandGains[band] = gainDb.coerceIn(-15f, 15f)
        dspProcessor?.setEqBands(bandGains, bassDb, trebleDb)
    }

    /**
     * Returns the current gain for [band] in dB.
     *
     * @param band Zero-based band index in [0, 9].
     */
    fun getBandGain(band: Int): Float = if (band in 0 until BAND_COUNT) bandGains[band] else 0f

    /** Returns a copy of all 10 band gains in dB. */
    fun getAllBandGains(): FloatArray = bandGains.copyOf()

    /**
     * Resets all 10 EQ bands to 0 dB without touching the bass or treble shelf gains.
     */
    fun resetEqBands() {
        bandGains = FloatArray(BAND_COUNT)
        dspProcessor?.setEqBands(bandGains, bassDb, trebleDb)
    }

    /**
     * Sets the bass low-shelf gain. Internally passes the full band state so the
     * native engine always has a consistent coefficient set.
     *
     * @param db Gain in dB, clamped to [-12, +12].
     */
    fun setBassDb(db: Float) {
        bassDb = db.coerceIn(-12f, 12f)
        dspProcessor?.setEqBands(bandGains, bassDb, trebleDb)
    }

    /**
     * Sets the treble high-shelf gain. Internally passes the full band state so the
     * native engine always has a consistent coefficient set.
     *
     * @param db Gain in dB, clamped to [-12, +12].
     */
    fun setTrebleDb(db: Float) {
        trebleDb = db.coerceIn(-12f, 12f)
        dspProcessor?.setEqBands(bandGains, bassDb, trebleDb)
    }

    /**
     * Applies stereo widening via the M/S matrix. See [DspProcessor.setStereoWidth].
     *
     * @param width Stereo width in [0.0, 2.0].
     */
    fun setStereoWidth(width: Float) {
        stereoWidth = width.coerceIn(0f, 2f)
        dspProcessor?.setStereoWidth(stereoWidth)
    }

    /**
     * Applies constant-power stereo pan / balance. See [DspProcessor.setBalance].
     *
     * @param pan Pan value in [-1.0, 1.0]. 0.0 = center.
     */
    fun setBalance(pan: Float) {
        this.pan = pan.coerceIn(-1f, 1f)
        dspProcessor?.setBalance(this.pan)
    }

    /**
     * Sets the tape saturation drive. See [DspProcessor.setSaturation].
     *
     * @param drive Drive in [0.0, 4.0]. 0.0 = bypass.
     */
    fun setSaturation(drive: Float) {
        saturationDrive = drive.coerceIn(0f, 4f)
        dspProcessor?.setSaturation(saturationDrive)
    }

    /**
     * Sets the reverb wet/dry mix, decay time, high-frequency damping, and room size,
     * applying them immediately to the native DSP engine.
     *
     * The reverb is positioned after all EQ and saturation in the chain so it contributes
     * only spatial depth, not tonal coloring. All four parameters are applied with no
     * buffer clearing, making this safe to call continuously during live knob dragging.
     *
     * @param mix   Wet/dry mix in [0.0, 1.0]. 0 = dry only (bypass).
     * @param decay Decay time in [0.0, 1.0]. 0 = very short; 1 = very long hall.
     * @param damp  High-frequency damping in [0.0, 1.0]. 0 = bright tail; 1 = dark tail.
     * @param size  Room size in [0.0, 1.0]. 0 = small; 1 = large hall.
     */
    fun setReverb(mix: Float, decay: Float, damp: Float, size: Float) {
        reverbMix = mix.coerceIn(0f, 1f)
        reverbDecay = decay.coerceIn(0f, 1f)
        reverbDamp = damp.coerceIn(0f, 1f)
        reverbSize = size.coerceIn(0f, 1f)
        dspProcessor?.setReverb(reverbMix, reverbDecay, reverbDamp, reverbSize)
    }

    /** Pushes all stored parameter fields to the native [DspProcessor] after (re)creation. */
    private fun pushAllParameters() {
        val dsp = dspProcessor ?: return
        dsp.setEqBands(bandGains, bassDb, trebleDb)
        dsp.setEqEnabled(eqEnabled)
        dsp.setStereoWidth(stereoWidth)
        dsp.setBalance(pan)
        dsp.setSaturation(saturationDrive)
        dsp.setReverb(reverbMix, reverbDecay, reverbDamp, reverbSize)
    }

    private fun releaseNativeContext() {
        dspProcessor?.release()
        dspProcessor = null
    }

    /**
     * Returns a [ByteBuffer] of at least [capacity] bytes (native byte order).
     * Reuses the existing [outputBuffer] when it is large enough to avoid allocation.
     *
     * @param capacity Minimum required size in bytes.
     */
    private fun acquireOutputBuffer(capacity: Int): ByteBuffer {
        return if (outputBuffer === AudioProcessor.EMPTY_BUFFER || outputBuffer.capacity() < capacity) {
            ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
            outputBuffer.limit(capacity)
            outputBuffer
        }
    }

    companion object {
        /** Number of ISO 10-band EQ bands managed by this processor. */
        const val BAND_COUNT = 10
    }
}

