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
     * Linear replay gain applied alongside [preampLinearGain] before the native DSP chain.
     * Both are multiplied together in a single pass so there is no extra processing cost.
     * Default 1.0 = unity (0 dB). Updated by [setReplayGainDb].
     */
    @Volatile
    private var replayGainLinearGain: Float = 1f

    /**
     * Linear gain derived from the current track's embedded ReplayGain tag.
     * Applied in the same single multiply as [preampLinearGain] and [replayGainLinearGain].
     * Reset to 1.0 (unity) on each track transition when auto-RG is disabled.
     * Updated by [setTagReplayGainDb] when a new track starts playing with auto-RG on.
     */
    @Volatile
    private var tagReplayGainLinearGain: Float = 1f

    /**
     * When set to `true`, [queueInput] becomes a transparent passthrough — the buffer is
     * returned from [getOutput] unchanged and no DSP is applied. This lets [FelicityAudioSink]
     * own the DSP execution when USB or AAudio is the active output, while still keeping this
     * processor in [DefaultAudioSink]'s chain so the AudioTrack fallback path is unaffected.
     *
     * [FelicityAudioSink] sets this flag at the top of each [handleBuffer] call based on
     * which output is currently live, so the correct path is always used for every buffer
     * without any race conditions.
     */
    @Volatile
    var isBypassedForDirectOutput: Boolean = false
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
     * Stores the most recent PEQ (parametric EQ) gain array so it can be re-pushed to the
     * native engine whenever a new [DspProcessor] context is created after a format change.
     * Empty when the app is in graphic EQ mode.
     */
    private var peqGains: FloatArray = FloatArray(0)

    /**
     * Stores the most recent PEQ center-frequency array — one entry per parametric band, in Hz.
     * Empty when the app is in graphic EQ mode.
     */
    private var peqFreqs: FloatArray = FloatArray(0)

    /**
     * Stores the most recent PEQ Q-factor array — one entry per parametric band.
     * Higher Q means a narrower peak; lower Q affects a wider frequency range.
     * Empty when the app is in graphic EQ mode.
     */
    private var peqQValues: FloatArray = FloatArray(0)

    /**
     * Hardware audio output latency in milliseconds last reported by the service layer.
     *
     * Stored here so that [pushAllParameters] can re-apply the correct delay when a new
     * native [DspProcessor] context is created after a sample-rate change. The conversion
     * from milliseconds to samples is done inside [DspProcessor.setOutputLatency] using
     * the current sample rate, so this field only needs to store the raw millisecond value.
     *
     * Default 0 = no pre-delay (immediate visualizer response).
     */
    @Volatile
    private var outputLatencyMs: Int = 0

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
        } else {
            /**
             * The native engine reconfigures its biquad filter delay states during this call,
             * which can silently wipe the gain coefficients back to flat defaults depending on
             * the native implementation. We always re-push all parameters afterwards so the
             * user's saved settings survive any pipeline reconfiguration without requiring them
             * to touch a knob to "reload" the effects.
             */
            dspProcessor?.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        }

        /**
         * Always push all stored parameters to the native engine after any configure path.
         * This covers three cases:
         *  1. Fresh DspProcessor created — native context starts at flat defaults.
         *  2. Same-format reconfigure (else branch) — nativeDspConfigure may reset coefficients.
         *  3. Multiple renderers sharing this processor (e.g., FFmpeg + native codec with
         *     EXTENSION_RENDERER_MODE_PREFER) — the second renderer's configure() used to
         *     leave the DSP in an un-initialized state until the user adjusted a control.
         */
        pushAllParameters()

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
        /**
         * When [FelicityAudioSink] is driving a USB DAC or AAudio stream, it processes
         * the audio itself via [processInPlace] and only uses [DefaultAudioSink] as a
         * silent timekeeper. In that scenario we turn this into a transparent passthrough
         * so [DefaultAudioSink]'s chain doesn't process (and waste CPU on) the same
         * audio that the intercept branch already processed.
         */
        if (!active || isBypassedForDirectOutput) {
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

        val preamp = preampLinearGain * replayGainLinearGain * tagReplayGainLinearGain

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

        /**
         * Re-push all DSP parameters on every flush. ExoPlayer calls flush() after configure()
         * and also on seeks and pipeline restarts — in all of those cases the native biquad
         * delay lines are cleared, and depending on the native implementation the gain
         * coefficients can drift back to flat defaults as well. Pushing here guarantees the
         * user's saved EQ, bass, treble, and all other effects are always live from the very
         * first audio frame after any pipeline event, not just after a manual knob touch.
         *
         * If DspProcessor creation failed during configure() (e.g., because VisualizerProcessor
         * was not yet configured when the chain fired in order), we also retry creation here.
         * flush() is always called AFTER configure() on the full chain, so by this point the
         * visualizer has finished its own configure() pass and its FFT handle is valid.
         */
        if (active && inputFormat != AudioProcessor.AudioFormat.NOT_SET) {
            if (dspProcessor == null) {
                val retryDsp = DspProcessor(
                        visualizerProcessor,
                        inputFormat.sampleRate,
                        inputFormat.channelCount
                )
                if (retryDsp.isReady) {
                    dspProcessor = retryDsp
                }
            }
            pushAllParameters()
        }
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
     * Processes a float array in-place using the full native DSP chain (EQ, bass, treble,
     * stereo widening, balance, saturation, reverb) plus pre-amplification.
     *
     * This is the direct-output hot path called by [FelicityAudioSink] when USB or AAudio
     * is active. Because [isBypassedForDirectOutput] makes [queueInput] a passthrough in
     * that mode, the same [DspProcessor] context is used exclusively by this call — there
     * is no risk of processing the same audio twice.
     *
     * The native engine also updates the shared [FFTContext] on every call, so the
     * spectrum visualizer keeps working even when [DefaultAudioSink]'s chain is bypassed.
     *
     * @param samples Interleaved stereo (or mono) float PCM samples, modified in-place.
     *                The array must contain exactly the frame count × channel count samples.
     */
    fun processInPlace(samples: FloatArray) {
        processInPlace(samples, samples.size)
    }

    /**
     * Same as [processInPlace] but only treats the first [length] elements of [samples]
     * as valid audio. When [samples] is exactly [length] elements the array is processed
     * directly with zero allocation — this is the steady-state fast path.
     *
     * When [samples] is larger than [length] (the reusable scratch buffer was grown for a
     * bigger previous frame), we trim to a copy, run the DSP, then write the results back
     * into [samples[0..[length])] so the caller sees fully processed data. Without the
     * copy-back step the caller would push the original unprocessed bytes to hardware and
     * EQ / preamp / bass / treble would appear to have no effect at all.
     *
     * @param samples  Buffer that holds the audio data (may be larger than [length]).
     * @param length   Number of valid samples at the start of [samples].
     */
    fun processInPlace(samples: FloatArray, length: Int) {
        val dsp = dspProcessor ?: return
        val preamp = preampLinearGain * replayGainLinearGain * tagReplayGainLinearGain

        if (samples.size == length) {
            if (preamp != 1f) {
                for (i in 0 until length) samples[i] *= preamp
            }
            dsp.processAudio(samples)
        } else {
            // Work on a correctly-sized copy so the native engine only sees valid frames,
            // then write the processed values back so the caller's push uses correct data.
            val slice = samples.copyOf(length)
            if (preamp != 1f) {
                for (i in 0 until length) slice[i] *= preamp
            }
            dsp.processAudio(slice)
            slice.copyInto(samples, endIndex = length)
        }
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
     * Sets the manual replay gain offset and recomputes the internal linear scale factor.
     * This gain is multiplied together with the preamp before the DSP chain, so there is
     * no extra processing step — it costs nothing at runtime beyond a single float multiply.
     *
     * @param db Gain offset in dB, clamped to [-15, +15]. 0 dB = unity (no change).
     */
    fun setReplayGainDb(db: Float) {
        val clamped = db.coerceIn(-15f, 15f)
        replayGainLinearGain = 10f.pow(clamped / 20f)
    }

    /**
     * Applies the gain value extracted from the current track's embedded ReplayGain tag.
     * This is separate from the manual [setReplayGainDb] knob so that the two can coexist —
     * the tag value corrects inter-track loudness differences automatically while the manual
     * knob acts as a user-controlled trim on top.
     *
     * Pass 0.0 to restore unity gain (e.g. when a track has no RG tag or auto-RG is off).
     *
     * @param db Gain in dB parsed from the REPLAYGAIN_TRACK_GAIN or REPLAYGAIN_ALBUM_GAIN tag.
     */
    fun setTagReplayGainDb(db: Float) {
        tagReplayGainLinearGain = 10f.pow(db.coerceIn(-30f, 30f) / 20f)
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
     * Pushes a parametric EQ configuration to the native engine, overriding the current
     * fixed-frequency 10-band state. Each band can have its own center frequency and Q,
     * giving the user full control over the filter shape — ideal for surgical corrections
     * that the fixed graphic bands can't target precisely.
     *
     * Calling this while in graphic mode is safe but won't take audible effect until
     * [eqEnabled] is true and the DSP engine applies the bands.
     *
     * @param gains    Per-band gain in dB.
     * @param freqs    Per-band center frequency in Hz.
     * @param qValues  Per-band Q factor.
     */
    fun setPeqBands(gains: FloatArray, freqs: FloatArray, qValues: FloatArray) {
        peqGains = gains.copyOf()
        peqFreqs = freqs.copyOf()
        peqQValues = qValues.copyOf()
        dspProcessor?.setPeqBands(peqGains, peqFreqs, peqQValues)
    }

    /** Sets the treble high-shelf gain. Internally passes the full band state so the
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

    /**
     * Updates the hardware output latency used to pre-delay the FFT visualizer input.
     *
     * Storing the value here (in addition to forwarding it to [DspProcessor]) ensures that
     * the correct delay is automatically re-applied via [pushAllParameters] whenever a new
     * native context is created after a sample-rate or channel-count change. The native
     * engine converts the millisecond value to a sample count at the current sample rate
     * so the temporal alignment remains correct across format transitions.
     *
     * @param latencyMs Total audio output latency in milliseconds (>= 0). 0 = disable pre-delay.
     */
    fun setOutputLatency(latencyMs: Int) {
        outputLatencyMs = latencyMs.coerceAtLeast(0)
        dspProcessor?.setOutputLatency(outputLatencyMs)
    }

    /** Pushes all stored parameter fields to the native [DspProcessor] after (re)creation. */
    private fun pushAllParameters() {
        val dsp = dspProcessor ?: return
        // Re-apply the appropriate EQ state: if PEQ bands are loaded, push them; otherwise
        // push the standard 10-band graphic EQ so the two modes don't step on each other.
        if (peqGains.isNotEmpty()) {
            dsp.setPeqBands(peqGains, peqFreqs, peqQValues)
        } else {
            dsp.setEqBands(bandGains, bassDb, trebleDb)
        }
        dsp.setEqEnabled(eqEnabled)
        dsp.setStereoWidth(stereoWidth)
        dsp.setBalance(pan)
        dsp.setSaturation(saturationDrive)
        dsp.setReverb(reverbMix, reverbDecay, reverbDamp, reverbSize)
        // Re-apply the output latency so the pre-delay ring buffer is seeded at the correct
        // sample count for the new audio format. Must be last so it uses the final sample rate.
        dsp.setOutputLatency(outputLatencyMs)
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

