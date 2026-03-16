package app.simple.felicity.engine.processors

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import app.simple.felicity.engine.processors.EqualizerAudioProcessor.Companion.FLAT_THRESHOLD_DB
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * A custom [AudioProcessor] implementing a strict 10-band graphic equalizer using
 * second-order peaking biquad IIR filters (RBJ Audio EQ Cookbook formulas).
 *
 * Band center frequencies follow the ISO 10-band standard with 1-octave spacing:
 * ```
 *   Band 0 →    31 Hz
 *   Band 1 →    62 Hz
 *   Band 2 →   125 Hz
 *   Band 3 →   250 Hz
 *   Band 4 →   500 Hz
 *   Band 5 → 1 000 Hz
 *   Band 6 → 2 000 Hz
 *   Band 7 → 4 000 Hz
 *   Band 8 → 8 000 Hz
 *   Band 9 → 16 000 Hz
 * ```
 *
 * Each band uses a second-order peaking (bell) filter with Q = √2 ≈ 1.4142, which gives
 * a gentle 1-octave bandwidth typical of hardware graphic equalizers.
 * Gain range per band: [-15 dB, +15 dB]. All bands at 0 dB = identity (flat passthrough).
 *
 * The processor supports all four PCM encodings via [PcmUtils]: 16-bit, 24-bit, 32-bit,
 * and 32-bit float. Filter state is maintained independently per channel so stereo phase
 * coherence is preserved across the entire 10-stage chain.
 *
 * A fast-bypass path (input buffer forwarded as-is, zero processing cost) is taken when
 * [isEnabled] is false OR every band gain is within ±[FLAT_THRESHOLD_DB] of 0 dB.
 *
 * Biquad coefficients are replaced atomically (copy-on-write on the [coefficients]
 * volatile reference) so the audio thread always reads a coherent set of coefficients
 * even while the UI thread is updating gains.
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class EqualizerAudioProcessor : AudioProcessor {

    private var inputFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var active = false
    private var inputEnded = false
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER

    /**
     * Whether the equalizer is currently enabled.
     * False = full bypass; audio is forwarded without modification.
     */
    @Volatile
    var isEnabled: Boolean = true

    /**
     * Pre-amplifier linear gain applied to every sample before the biquad EQ chain.
     * Computed by [setPreamp] as 10^(dB / 20). Default 1.0 = unity (0 dB).
     */
    @Volatile
    private var preampLinearGain: Float = 1f

    /**
     * Sets the pre-amplifier gain in dB and immediately recomputes [preampLinearGain].
     *
     * @param db Gain in dB, clamped to [-15..+15]. 0 dB = unity (no change).
     */
    fun setPreamp(db: Float) {
        val clamped = db.coerceIn(-15f, 15f)
        preampLinearGain = 10f.pow(clamped / 20f)
    }

    /** Returns the current pre-amplifier gain as a linear scale factor (not dB). */
    fun getPreampLinearGain(): Float = preampLinearGain

    /** Current sample rate; coefficient arrays are recomputed whenever this changes. */
    private var sampleRate: Int = DEFAULT_SAMPLE_RATE

    /**
     * Number of audio channels in the configured format.
     * Initialized to 0 (an impossible real value) so the first [configure] call always
     * satisfies the channelChanged condition and allocates [filterState], even for
     * the most common 44100 Hz / stereo format where the rate would otherwise be unchanged.
     */
    private var channelCount: Int = 0

    /**
     * Per-band gain values in dB (index 0 = 31 Hz, index 9 = 16 kHz).
     * Updated by [setBandGain] and [setAllBandGains].
     */
    @Volatile
    private var bandGains: FloatArray = FloatArray(BAND_COUNT)

    /**
     * Biquad filter coefficient arrays, one per band, normalized so a0 = 1.
     * Layout per array: [b0, b1, b2, a1, a2].
     *
     * This reference is replaced atomically (copy-on-write) on every gain update so
     * the audio thread always reads a consistent snapshot.
     */
    @Volatile
    private var coefficients: Array<FloatArray> = Array(BAND_COUNT) { identityCoefficients() }

    /**
     * Per-channel per-band biquad filter state (Direct Form II Transposed).
     * Dimensions: [channelCount][BAND_COUNT][2] where the innermost pair is {w1, w2}.
     * Allocated once in [configure] when the channel count is known.
     */
    private var filterState: Array<Array<FloatArray>> = emptyArray()

    /**
     * True when every band gain is within ±[FLAT_THRESHOLD_DB] of 0 dB.
     * Lets the audio-thread loop skip all biquad math entirely.
     */
    @Volatile
    private var allBandsFlat: Boolean = true

    // -------------------------------------------------------------------------
    // Public band-gain control API
    // -------------------------------------------------------------------------

    /**
     * Sets the gain for a single EQ band and immediately recomputes its biquad coefficients.
     *
     * The [coefficients] reference is replaced atomically (copy-on-write) so the
     * audio thread always sees a fully consistent set of coefficients.
     *
     * @param band   Zero-based band index in [0..9].
     * @param gainDb Gain in dB, clamped to [-15..+15]. 0 dB = flat.
     */
    fun setBandGain(band: Int, gainDb: Float) {
        if (band !in 0 until BAND_COUNT) return
        val newGains = bandGains.copyOf()
        newGains[band] = gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        bandGains = newGains

        val newCoeffs = coefficients.copyOf()
        newCoeffs[band] = computeBandCoefficients(band, sampleRate)
        coefficients = newCoeffs

        updateFlatState(newGains)
    }

    /**
     * Applies all 10 band gains at once and recomputes all biquad coefficients atomically.
     * Values beyond index 9 are ignored; missing entries default to 0 dB.
     *
     * @param gains 10-element [FloatArray] of dB values, one per band.
     */
    fun setAllBandGains(gains: FloatArray) {
        val newGains = FloatArray(BAND_COUNT) { i ->
            (if (i < gains.size) gains[i] else 0f).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        }
        bandGains = newGains
        coefficients = Array(BAND_COUNT) { b -> computeBandCoefficients(b, sampleRate) }
        updateFlatState(newGains)
    }

    /**
     * Returns the current gain for [band] in dB.
     *
     * @param band Zero-based band index in [0..9].
     */
    fun getBandGain(band: Int): Float = if (band in 0 until BAND_COUNT) bandGains[band] else 0f

    /**
     * Returns a copy of all 10 band gains as a [FloatArray].
     */
    fun getAllBandGains(): FloatArray = bandGains.copyOf()

    /**
     * Resets all 10 bands to 0 dB, sets all coefficients to identity, and clears filter state.
     * Safe to call from any thread.
     */
    fun resetAllBands() {
        val flat = FloatArray(BAND_COUNT)
        bandGains = flat
        coefficients = Array(BAND_COUNT) { identityCoefficients() }
        preampLinearGain = 1f
        clearFilterState()
        allBandsFlat = true
    }

    // -------------------------------------------------------------------------
    // AudioProcessor implementation
    // -------------------------------------------------------------------------

    /**
     * Activates for any supported PCM format. Recomputes all band coefficients
     * whenever the sample rate changes to keep the frequency response accurate.
     * Allocates per-channel filter state when the channel count changes.
     */
    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported = PcmUtils.isEncodingSupported(inputAudioFormat.encoding) &&
                inputAudioFormat.channelCount >= 1
        active = supported
        return if (supported) {
            val rateChanged = inputAudioFormat.sampleRate != sampleRate
            val channelChanged = inputAudioFormat.channelCount != channelCount

            if (rateChanged) {
                sampleRate = inputAudioFormat.sampleRate
                coefficients = Array(BAND_COUNT) { b -> computeBandCoefficients(b, sampleRate) }
            }

            if (channelChanged) {
                channelCount = inputAudioFormat.channelCount
                filterState = Array(channelCount) { Array(BAND_COUNT) { FloatArray(2) } }
            }

            inputFormat = inputAudioFormat
            inputAudioFormat
        } else {
            inputFormat = AudioProcessor.AudioFormat.NOT_SET
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun isActive(): Boolean = active

    /**
     * Processes each PCM frame through the 10-stage biquad filter chain.
     * Channels beyond the first [channelCount] are not modified.
     *
     * The fast-bypass path (no allocation, no math) is taken when [isEnabled] is false
     * or [allBandsFlat] is true; the input buffer reference is forwarded as-is.
     */
    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!active) {
            outputBuffer = inputBuffer
            return
        }

        if (!isEnabled || (allBandsFlat && abs(preampLinearGain - 1f) < 0.001f)) {
            outputBuffer = inputBuffer
            return
        }

        val state = filterState
        if (state.isEmpty()) {
            outputBuffer = inputBuffer
            return
        }

        val encoding = inputFormat.encoding
        val bps = PcmUtils.bytesPerSample(encoding)
        val ch = channelCount
        val frameSize = bps * ch
        val remaining = inputBuffer.remaining()
        val buf = acquireOutputBuffer(remaining)

        // Snapshot volatile references once before the hot loop to avoid
        // repeated memory-barrier crossings per sample.
        val coeff = coefficients
        val preamp = preampLinearGain

        while (inputBuffer.remaining() >= frameSize) {
            for (c in 0 until ch) {
                // Preamp is applied before the biquad chain so the EQ curve shape is
                // independent of the input level.
                var sample = PcmUtils.readFloat(inputBuffer, encoding) * preamp
                val chState = state[c]
                for (b in 0 until BAND_COUNT) {
                    sample = applyBiquad(sample, coeff[b], chState[b])
                }
                PcmUtils.writeFloat(buf, sample, encoding)
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
        clearFilterState()
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        active = false
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
    }

    // -------------------------------------------------------------------------
    // Biquad engine
    // -------------------------------------------------------------------------

    /**
     * Applies one second-order IIR filter stage to [input] using the Direct Form II
     * Transposed structure, which is numerically more stable than Direct Form I.
     *
     * Recurrence relations (after normalization, a0 = 1):
     * ```
     *   y    = b0 × x + w1
     *   w1′  = b1 × x − a1 × y + w2
     *   w2′  = b2 × x − a2 × y
     * ```
     *
     * [coeffs] layout: [b0, b1, b2, a1, a2].
     * [state] is a 2-element working array {w1, w2}, modified in-place.
     *
     * @param input  Normalized PCM sample in float space.
     * @param coeffs Coefficient array for this band (length 5).
     * @param state  Two-element filter-state array, updated in-place.
     * @return The filtered output sample.
     */
    private fun applyBiquad(input: Float, coeffs: FloatArray, state: FloatArray): Float {
        val b0 = coeffs[0];
        val b1 = coeffs[1];
        val b2 = coeffs[2]
        val a1 = coeffs[3];
        val a2 = coeffs[4]
        val w1 = state[0];
        val w2 = state[1]

        val output = b0 * input + w1
        state[0] = b1 * input - a1 * output + w2
        state[1] = b2 * input - a2 * output
        return output
    }

    /**
     * Computes normalized biquad peaking-EQ coefficients for [band] using the
     * RBJ Audio EQ Cookbook peaking-EQ formulas:
     * ```
     *   A  = 10^(dBgain / 40)
     *   ω₀ = 2π × f₀ / Fs
     *   α  = sin(ω₀) / (2 × Q)
     *
     *   b0 =   1 + α × A     b1 = −2 × cos(ω₀)     b2 =   1 − α × A
     *   a0 =   1 + α / A     a1 = −2 × cos(ω₀)     a2 =   1 − α / A
     *
     *   Normalized output: [b0/a0, b1/a0, b2/a0, a1/a0, a2/a0]
     * ```
     *
     * Identity coefficients [1, 0, 0, 0, 0] are returned when the gain is within
     * ±[FLAT_THRESHOLD_DB] of 0, avoiding unnecessary floating-point arithmetic.
     *
     * @param band        Zero-based band index in [0..9].
     * @param sampleRateHz Sample rate of the current format in Hz.
     * @return A new 5-element [FloatArray] of normalized biquad coefficients.
     */
    private fun computeBandCoefficients(band: Int, sampleRateHz: Int): FloatArray {
        val gainDb = bandGains[band]

        if (abs(gainDb) < FLAT_THRESHOLD_DB) {
            return identityCoefficients()
        }

        val f0 = CENTER_FREQUENCIES[band]
        val a = 10f.pow(gainDb / 40f)
        val omega = (2.0 * PI * f0 / sampleRateHz).toFloat()
        val sinOmega = sin(omega.toDouble()).toFloat()
        val cosOmega = cos(omega.toDouble()).toFloat()
        val alpha = sinOmega / (2f * BAND_Q)

        val b0 = 1f + alpha * a
        val b1 = -2f * cosOmega
        val b2 = 1f - alpha * a
        val a0 = 1f + alpha / a
        val a1 = -2f * cosOmega
        val a2 = 1f - alpha / a

        val inv = 1f / a0
        return floatArrayOf(b0 * inv, b1 * inv, b2 * inv, a1 * inv, a2 * inv)
    }

    // -------------------------------------------------------------------------
    // State helpers
    // -------------------------------------------------------------------------

    /**
     * Zeros all biquad state cells (w1, w2) without deallocating.
     * Called from [flush] and [reset].
     */
    private fun clearFilterState() {
        for (chState in filterState) {
            for (bandState in chState) {
                bandState[0] = 0f
                bandState[1] = 0f
            }
        }
    }

    /**
     * Updates [allBandsFlat] from [gains] after any gain mutation.
     *
     * @param gains The latest bandGains array snapshot.
     */
    private fun updateFlatState(gains: FloatArray) {
        allBandsFlat = gains.all { abs(it) < FLAT_THRESHOLD_DB }
    }

    /**
     * Returns a fresh identity-filter coefficient array representing a flat (no-op) stage.
     * Identity: b0 = 1, b1 = b2 = a1 = a2 = 0, so the output equals the input on every sample.
     */
    private fun identityCoefficients(): FloatArray = floatArrayOf(1f, 0f, 0f, 0f, 0f)

    /**
     * Returns a [ByteBuffer] of at least [capacity] bytes (native byte order).
     * Reuses [outputBuffer] when large enough to avoid per-chunk heap allocation.
     *
     * @param capacity Minimum required buffer size in bytes.
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

        /** Total number of EQ bands. */
        const val BAND_COUNT = 10

        /** ISO 10-band graphic EQ center frequencies in Hz (1-octave spacing). */
        val CENTER_FREQUENCIES: FloatArray = floatArrayOf(
                31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f
        )

        /** Q factor for each band giving a 1-octave bandwidth (√2 ≈ 1.4142). */
        private const val BAND_Q = 1.4142135f

        /** Minimum gain in dB. */
        const val MIN_GAIN_DB = -15f

        /** Maximum gain in dB. */
        const val MAX_GAIN_DB = 15f

        /**
         * Gains whose absolute value is below this threshold are treated as 0 dB flat,
         * allowing the hot biquad path to be bypassed for that band.
         */
        private const val FLAT_THRESHOLD_DB = 0.001f

        /** Fallback sample rate used before [configure] is called for the first time. */
        private const val DEFAULT_SAMPLE_RATE = 44100
    }
}

