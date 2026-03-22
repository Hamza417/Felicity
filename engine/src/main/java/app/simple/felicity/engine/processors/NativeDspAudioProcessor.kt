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
     * For [C.ENCODING_PCM_FLOAT] with unity preamp, the ByteBuffer is read as a FloatBuffer
     * directly, skipping per-sample conversion — the fastest possible path.
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
         * Fast path for float PCM with unity gain: use the ByteBuffer's FloatBuffer view
         * to bulk-read all samples without per-element conversion or scaling.
         */
        if (encoding == C.ENCODING_PCM_FLOAT && preamp == 1f) {
            inputBuffer.asFloatBuffer().get(workBuf)
            inputBuffer.position(inputBuffer.limit())
        } else {
            for (i in 0 until numSamples) {
                workBuf[i] = PcmUtils.readFloat(inputBuffer, encoding) * preamp
            }
        }

        /** Apply the full DSP chain in-place via a single JNI call. */
        dsp.processAudio(workBuf)

        /** Re-encode the processed floats back to the original PCM format. */
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

    /** Pushes all stored parameter fields to the native [DspProcessor] after (re)creation. */
    private fun pushAllParameters() {
        val dsp = dspProcessor ?: return
        dsp.setEqBands(bandGains, bassDb, trebleDb)
        dsp.setEqEnabled(eqEnabled)
        dsp.setStereoWidth(stereoWidth)
        dsp.setBalance(pan)
        dsp.setSaturation(saturationDrive)
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

