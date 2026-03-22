package app.simple.felicity.engine.processors

import android.util.Log
import kotlin.math.abs

/**
 * JNI wrapper for the Felicity native DSP processing engine.
 *
 * Owns a single native [DspContext] that applies a complete, zero-allocation DSP chain
 * to a caller-provided [FloatArray] of interleaved PCM samples in a single JNI call:
 *
 *   1. 10-band peaking EQ (ISO standard: 31 Hz through 16 kHz, RBJ biquad)
 *   2. Bass low-shelf  (250 Hz, S = 1)
 *   3. Treble high-shelf (4000 Hz, S = 1)
 *   4. Stereo widening via M/S matrix
 *   5. Constant-power pan / balance
 *   6. Tape-style soft saturation (algebraic sigmoid, no tanh)
 *   7. Mono downmix → visualizer FFT backfeed
 *
 * The hot path ([processAudio]) pins the JVM array with GetFloatArrayElements,
 * modifies it in-place through the NEON-accelerated C++ chain, and releases it
 * with mode 0 — committing the modified samples back to the JVM without any
 * intermediate copy or allocation.
 *
 * The [DspContext] shares the [FFTContext] that was created by [VisualizerProcessor];
 * it is supplied at construction time via [VisualizerProcessor.getNativeHandle].
 * After each completed FFT window [readBandMagnitudes] returns true and populates
 * the caller's pre-allocated [FloatArray] so the spectrum view can be refreshed.
 *
 * Thread safety: all [set*] methods may be called from any thread. The native engine
 * snapshots every parameter at the top of each [processAudio] invocation so partial
 * updates from a concurrent setter can never corrupt a processing frame.
 *
 * @author Hamza417
 */
class DspProcessor(
        private val visualizerProcessor: VisualizerProcessor,
        sampleRate: Int,
        channelCount: Int
) {

    private var nativeHandle: Long = 0L

    init {
        val fftHandle = visualizerProcessor.getNativeHandle()
        if (fftHandle != 0L) {
            nativeHandle = nativeDspCreate(fftHandle, sampleRate, channelCount)
            if (nativeHandle == 0L) {
                Log.e(TAG, "nativeDspCreate returned 0 — check logcat for native errors")
            }
        } else {
            Log.e(TAG, "VisualizerProcessor FFT context not ready; DspProcessor not initialized")
        }
    }

    /** True when the native context was successfully created and is ready to process audio. */
    val isReady: Boolean
        get() = nativeHandle != 0L

    /**
     * Notifies the engine that the audio format has changed.
     *
     * Must be called before [processAudio] whenever the decoder emits a new sample rate or
     * channel configuration. Internally this recomputes all biquad coefficients at the new
     * sample rate and resets all filter delay states to avoid transient artifacts.
     *
     * @param sampleRate   New sample rate in Hz.
     * @param channelCount New number of interleaved audio channels (1 or 2).
     */
    fun configure(sampleRate: Int, channelCount: Int) {
        if (nativeHandle == 0L) return
        nativeDspConfigure(nativeHandle, sampleRate, channelCount)
    }

    /**
     * Applies all 10 peaking EQ band gains together with the bass and treble shelf gains
     * in a single JNI call, recomputing all affected biquad coefficients atomically.
     *
     * @param bandGains 10-element [FloatArray] of dB gains, one per ISO band (31 Hz → 16 kHz).
     *                  Values outside [-15, +15] dB are clamped by the native engine.
     * @param bassDb    Bass low-shelf gain in dB; clamped to [-12, +12].
     * @param trebleDb  Treble high-shelf gain in dB; clamped to [-12, +12].
     */
    fun setEqBands(bandGains: FloatArray, bassDb: Float = 0f, trebleDb: Float = 0f) {
        if (nativeHandle == 0L) return
        nativeDspSetEqBands(nativeHandle, bandGains, bassDb, trebleDb)
    }

    /**
     * Enables or disables the entire EQ stage (10-band, bass, treble).
     *
     * When disabled the EQ biquad chain is completely skipped in [processAudio],
     * reducing CPU load to only the subsequent stereo-widening, pan, and saturation stages.
     *
     * @param enabled True to activate the EQ; false for full EQ bypass.
     */
    fun setEqEnabled(enabled: Boolean) {
        if (nativeHandle == 0L) return
        nativeDspSetEqEnabled(nativeHandle, enabled)
    }

    /**
     * Sets the stereo image width via the M/S matrix.
     *
     *   0.0 → full mono (Side signal removed)
     *   1.0 → natural stereo passthrough (identity matrix)
     *   2.0 → maximum widening (Side signal doubled)
     *
     * @param width Stereo width clamped to [0.0, 2.0].
     */
    fun setStereoWidth(width: Float) {
        if (nativeHandle == 0L) return
        nativeDspSetStereoWidth(nativeHandle, width.coerceIn(0f, 2f))
    }

    /**
     * Applies a constant-power stereo pan.
     *
     * @param pan Pan value in [-1.0, 1.0]. -1.0 = full left, 0.0 = center, +1.0 = full right.
     */
    fun setBalance(pan: Float) {
        if (nativeHandle == 0L) return
        nativeDspSetBalance(nativeHandle, pan.coerceIn(-1f, 1f))
    }

    /**
     * Sets the tape saturation drive using the algebraic sigmoid transfer function.
     *
     *   0.0 → clean bypass (no distortion)
     *   1.0 → subtle harmonic warmth
     *   2.0 → punchy mid-range coloring
     *   4.0 → heavy saturation
     *
     * @param drive Saturation drive clamped to [0.0, 4.0].
     */
    fun setSaturation(drive: Float) {
        if (nativeHandle == 0L) return
        nativeDspSetSaturation(nativeHandle, drive.coerceIn(0f, 4f))
    }

    /**
     * The zero-allocation audio processing hot path.
     *
     * Passes [pcmBuffer] directly into the native DSP chain where it is modified in-place.
     * The buffer must contain exactly [frameCount] × [channelCount] interleaved float samples
     * in the range [-1.0, 1.0]. After this call the buffer holds the fully processed audio,
     * ready to be written to an AudioTrack.
     *
     * @param pcmBuffer Interleaved PCM float array; modified in-place by the native engine.
     */
    fun processAudio(pcmBuffer: FloatArray) {
        if (nativeHandle == 0L) return
        nativeDspProcessAudio(nativeHandle, pcmBuffer)
    }

    /**
     * Copies the latest per-band RMS magnitudes from the native engine into [outBuffer].
     *
     * Returns true when a fresh FFT frame was ready and [outBuffer] has been populated.
     * Returns false when the engine has not completed a new FFT window since the last call,
     * in which case [outBuffer] is not modified.
     *
     * The [outBuffer] must be pre-allocated with at least [VisualizerProcessor.BAND_COUNT]
     * elements. Calling this from the UI thread after a [View.postInvalidate] callback is
     * the intended usage pattern.
     *
     * @param outBuffer Pre-allocated [FloatArray] to receive band magnitudes.
     * @return True if fresh data was copied; false if no new FFT frame was ready.
     */
    fun readBandMagnitudes(outBuffer: FloatArray): Boolean {
        if (nativeHandle == 0L) return false
        return nativeDspReadBandMagnitudes(nativeHandle, outBuffer)
    }

    /**
     * Releases all native resources owned by this processor.
     *
     * The [FFTContext] that was supplied via [VisualizerProcessor] is NOT freed here — it
     * remains owned by [VisualizerProcessor] and must be destroyed via its own lifecycle.
     * After [release] any subsequent call to [processAudio] or any setter is a safe no-op.
     */
    fun release() {
        if (nativeHandle == 0L) return
        nativeDspDestroy(nativeHandle)
        nativeHandle = 0L
        Log.i(TAG, "DspProcessor released")
    }

    /**
     * Convenience helper that resets the EQ to flat, stereo width to natural,
     * pan to center, and saturation to off in one call.
     */
    fun resetToDefaults() {
        setEqBands(FloatArray(10) { 0f }, 0f, 0f)
        setEqEnabled(true)
        setStereoWidth(1f)
        setBalance(0f)
        setSaturation(0f)
    }

    /**
     * Returns true when all 10 band gains plus bass and treble gains are all within
     * the flat threshold, i.e., no EQ coloration would be applied regardless of the
     * [eqEnabled] flag. Useful for optimizing UI state display.
     *
     * @param bandGains Current 10-band EQ gains in dB.
     * @param bassDb    Current bass shelf gain in dB.
     * @param trebleDb  Current treble shelf gain in dB.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun areAllBandsFlat(bandGains: FloatArray, bassDb: Float, trebleDb: Float): Boolean {
        return bandGains.all { abs(it) < FLAT_THRESHOLD_DB }
                && abs(bassDb) < FLAT_THRESHOLD_DB
                && abs(trebleDb) < FLAT_THRESHOLD_DB
    }

    // JNI declarations

    /**
     * Creates a native [DspContext], allocating all internal buffers and binding to the
     * [FFTContext] identified by [fftHandle].
     *
     * @param fftHandle    Opaque pointer from [VisualizerProcessor.getNativeHandle].
     * @param sampleRate   Initial sample rate in Hz.
     * @param channelCount Number of interleaved audio channels (1 or 2).
     * @return Opaque pointer to [DspContext] cast to Long, or 0 on failure.
     */
    private external fun nativeDspCreate(fftHandle: Long, sampleRate: Int, channelCount: Int): Long

    /**
     * Recomputes biquad coefficients and resets all filter states for a new audio format.
     *
     * @param handle       Opaque pointer from [nativeDspCreate].
     * @param sampleRate   New sample rate in Hz.
     * @param channelCount New channel count (1 or 2).
     */
    private external fun nativeDspConfigure(handle: Long, sampleRate: Int, channelCount: Int)

    /**
     * Stores all 10 EQ band gains plus the bass and treble shelf gains, recomputing all
     * biquad coefficients immediately.
     *
     * @param handle    Opaque pointer from [nativeDspCreate].
     * @param bandGains 10-element [FloatArray] of dB gains; index 0 = 31 Hz band.
     * @param bassDb    Bass low-shelf gain in dB.
     * @param trebleDb  Treble high-shelf gain in dB.
     */
    private external fun nativeDspSetEqBands(
            handle: Long,
            bandGains: FloatArray,
            bassDb: Float,
            trebleDb: Float
    )

    /**
     * Enables or disables the EQ/bass/treble processing stage.
     *
     * @param handle  Opaque pointer from [nativeDspCreate].
     * @param enabled True to activate; false to bypass.
     */
    private external fun nativeDspSetEqEnabled(handle: Long, enabled: Boolean)

    /**
     * Stores the stereo width and recomputes the M/S direct and cross gain coefficients.
     *
     * @param handle Opaque pointer from [nativeDspCreate].
     * @param width  Stereo width in [0.0, 2.0].
     */
    private external fun nativeDspSetStereoWidth(handle: Long, width: Float)

    /**
     * Stores the pan value and recomputes the constant-power left and right gains.
     *
     * @param handle Opaque pointer from [nativeDspCreate].
     * @param pan    Pan in [-1.0, 1.0].
     */
    private external fun nativeDspSetBalance(handle: Long, pan: Float)

    /**
     * Stores the saturation drive and pre-computes the compensation factor.
     *
     * @param handle Opaque pointer from [nativeDspCreate].
     * @param drive  Drive in [0.0, 4.0].
     */
    private external fun nativeDspSetSaturation(handle: Long, drive: Float)

    /**
     * Zero-allocation hot-path entrypoint. Applies the full DSP chain to [pcmBuffer]
     * in-place via GetFloatArrayElements / ReleaseFloatArrayElements with mode 0.
     *
     * @param handle    Opaque pointer from [nativeDspCreate].
     * @param pcmBuffer Interleaved float PCM; modified in-place.
     */
    private external fun nativeDspProcessAudio(handle: Long, pcmBuffer: FloatArray)

    /**
     * Atomically copies the latest per-band RMS magnitudes into [outBuffer] and clears the
     * ready flag. Returns true when fresh data was available.
     *
     * @param handle    Opaque pointer from [nativeDspCreate].
     * @param outBuffer Pre-allocated destination array (length >= [VisualizerProcessor.BAND_COUNT]).
     * @return True if a fresh FFT frame was copied; false otherwise.
     */
    private external fun nativeDspReadBandMagnitudes(handle: Long, outBuffer: FloatArray): Boolean

    /**
     * Frees all resources owned by the native [DspContext]. The bound [FFTContext] is
     * not freed and remains valid.
     *
     * @param handle Opaque pointer from [nativeDspCreate].
     */
    private external fun nativeDspDestroy(handle: Long)

    companion object {

        private const val TAG = "DspProcessor"

        /**
         * Gain values (in dB) whose absolute value is below this threshold are treated as
         * flat (0 dB) inside the native engine. Mirrors [EqualizerProcessor.FLAT_THRESHOLD_DB].
         */
        private const val FLAT_THRESHOLD_DB = 0.001f
    }
}

