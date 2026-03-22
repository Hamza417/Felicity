package app.simple.felicity.engine.processors

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import app.simple.felicity.engine.processors.TrebleProcessor.Companion.FLAT_THRESHOLD_DB
import app.simple.felicity.engine.processors.TrebleProcessor.Companion.SHELF_FREQUENCY_HZ
import app.simple.felicity.engine.utils.PcmUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A custom [AudioProcessor] that applies a second-order high-shelf biquad IIR filter for treble
 * tone control, using the RBJ Audio EQ Cookbook high-shelf formula with shelf slope S = 1.0.
 *
 * The shelf frequency is fixed at [SHELF_FREQUENCY_HZ] (4000 Hz), giving a broad treble boost
 * or cut that mirrors the treble knob on a traditional stereo amplifier. Gain can be set
 * anywhere in [-12 dB, +12 dB]; at 0 dB the processor takes a zero-cost bypass path.
 *
 * Supported encodings: PCM_16BIT, PCM_24BIT, PCM_32BIT, and PCM_FLOAT via [PcmUtils].
 * Filter state is maintained independently per channel to preserve stereo phase coherence.
 * Biquad coefficients are updated atomically on every [applyGain] call.
 *
 * RBJ high-shelf coefficients (S = 1):
 * ```
 *   A  = 10^(dBgain / 40)
 *   w0 = 2π × f0 / Fs
 *   α  = sin(w0) / √2
 *
 *   b0 =    A × [(A+1) + (A−1)cos(w0) + 2√A × α]
 *   b1 = −2A × [(A−1) + (A+1)cos(w0)]
 *   b2 =    A × [(A+1) + (A−1)cos(w0) − 2√A × α]
 *   a0 =        [(A+1) − (A−1)cos(w0) + 2√A × α]
 *   a1 =    2 × [(A−1) − (A+1)cos(w0)]
 *   a2 =        [(A+1) − (A−1)cos(w0) − 2√A × α]
 * ```
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class TrebleProcessor : AudioProcessor {

    private var inputFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var active = false
    private var inputEnded = false
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER

    /** Per-channel biquad state arrays {w1, w2}, allocated in [configure]. */
    private var filterState: Array<FloatArray> = emptyArray()

    private var channelCount: Int = 0
    private var sampleRate: Int = DEFAULT_SAMPLE_RATE

    /**
     * Current gain in dB, clamped to [-12..+12].
     * 0 dB = flat passthrough (bypass path, no processing).
     */
    @Volatile
    private var gainDb: Float = 0f

    /**
     * Normalized biquad coefficients [b0, b1, b2, a1, a2] for the high-shelf filter.
     * Replaced atomically on every [applyGain] call so the audio thread always reads
     * a consistent snapshot.
     */
    @Volatile
    private var coefficients: FloatArray = identityCoefficients()

    /**
     * True when [gainDb] is within ±[FLAT_THRESHOLD_DB] of zero.
     * Lets the audio-thread loop forward the input buffer without any allocation or math.
     */
    @Volatile
    private var isFlat: Boolean = true

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Sets the treble shelf gain and immediately recomputes the biquad coefficients.
     *
     * @param db Gain in dB, clamped to [-12..+12]. 0 dB = flat (bypass).
     */
    fun applyGain(db: Float) {
        gainDb = db.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        isFlat = abs(gainDb) < FLAT_THRESHOLD_DB
        coefficients = if (isFlat) identityCoefficients() else computeCoefficients(sampleRate)
    }

    /** Returns the current treble gain in dB. */
    fun getGainDb(): Float = gainDb

    // -------------------------------------------------------------------------
    // AudioProcessor implementation
    // -------------------------------------------------------------------------

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported = PcmUtils.isEncodingSupported(inputAudioFormat.encoding)
                && inputAudioFormat.channelCount >= 1
        active = supported
        return if (supported) {
            if (inputAudioFormat.sampleRate != sampleRate) {
                sampleRate = inputAudioFormat.sampleRate
                if (!isFlat) coefficients = computeCoefficients(sampleRate)
            }
            if (inputAudioFormat.channelCount != channelCount) {
                channelCount = inputAudioFormat.channelCount
                filterState = Array(channelCount) { FloatArray(2) }
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
     * Processes each PCM frame through the high-shelf biquad filter.
     * When [isFlat] is true the input buffer is forwarded as-is (zero cost).
     */
    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!active || isFlat) {
            outputBuffer = inputBuffer
            return
        }

        val state = filterState
        if (state.isEmpty()) {
            outputBuffer = inputBuffer
            return
        }

        val encoding = inputFormat.encoding
        val ch = channelCount
        val frameSize = PcmUtils.bytesPerSample(encoding) * ch
        val buf = acquireOutputBuffer(inputBuffer.remaining())
        val coeff = coefficients

        while (inputBuffer.remaining() >= frameSize) {
            for (c in state.indices) {
                val sample = PcmUtils.readFloat(inputBuffer, encoding)
                PcmUtils.writeFloat(buf, applyBiquad(sample, coeff, state[c]), encoding)
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
        for (chState in filterState) {
            chState[0] = 0f; chState[1] = 0f
        }
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
     * Applies the Direct Form II Transposed biquad to a single sample.
     *
     * @param input  Normalized PCM sample.
     * @param coeffs [b0, b1, b2, a1, a2] normalized coefficients.
     * @param state  Two-element state array {w1, w2}, updated in-place.
     * @return Filtered output sample.
     */
    private fun applyBiquad(input: Float, coeffs: FloatArray, state: FloatArray): Float {
        val output = coeffs[0] * input + state[0]
        state[0] = coeffs[1] * input - coeffs[3] * output + state[1]
        state[1] = coeffs[2] * input - coeffs[4] * output
        return output
    }

    /**
     * Computes normalized high-shelf biquad coefficients for [fs] Hz sample rate using
     * the RBJ high-shelf formulas at shelf slope S = 1.
     *
     * @param fs Sample rate in Hz.
     * @return Normalized coefficient array [b0/a0, b1/a0, b2/a0, a1/a0, a2/a0].
     */
    private fun computeCoefficients(fs: Int): FloatArray {
        val dB = gainDb
        if (abs(dB) < FLAT_THRESHOLD_DB) return identityCoefficients()

        val a = 10f.pow(dB / 40f)
        val w0 = (2.0 * PI * SHELF_FREQUENCY_HZ / fs).toFloat()
        val sinW = sin(w0.toDouble()).toFloat()
        val cosW = cos(w0.toDouble()).toFloat()
        val sqrtA = sqrt(a)
        // α = sin(w0) / √2  (derived from S = 1 in the RBJ shelf formula)
        val alpha = sinW / SQRT2

        val b0 = a * ((a + 1f) + (a - 1f) * cosW + 2f * sqrtA * alpha)
        val b1 = -2f * a * ((a - 1f) + (a + 1f) * cosW)
        val b2 = a * ((a + 1f) + (a - 1f) * cosW - 2f * sqrtA * alpha)
        val a0 = (a + 1f) - (a - 1f) * cosW + 2f * sqrtA * alpha
        val a1 = 2f * ((a - 1f) - (a + 1f) * cosW)
        val a2 = (a + 1f) - (a - 1f) * cosW - 2f * sqrtA * alpha

        val inv = 1f / a0
        return floatArrayOf(b0 * inv, b1 * inv, b2 * inv, a1 * inv, a2 * inv)
    }

    /** Returns fresh identity coefficients representing a flat (no-op) filter stage. */
    private fun identityCoefficients(): FloatArray = floatArrayOf(1f, 0f, 0f, 0f, 0f)

    /**
     * Returns a [ByteBuffer] of at least [capacity] bytes (native byte order).
     * Reuses [outputBuffer] when large enough to avoid per-chunk heap allocation.
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
        /** Shelf corner frequency in Hz. Frequencies above this are boosted or cut. */
        private const val SHELF_FREQUENCY_HZ = 4000.0

        /** Minimum and maximum gain in dB. */
        const val MIN_GAIN_DB = -12f
        const val MAX_GAIN_DB = 12f

        /** Gains within this threshold of 0 dB are treated as flat (bypass). */
        private const val FLAT_THRESHOLD_DB = 0.001f

        /** Fallback sample rate before [configure] is first called. */
        private const val DEFAULT_SAMPLE_RATE = 44100

        /** √2 constant used in the S = 1 RBJ shelf alpha computation. */
        private val SQRT2 = sqrt(2f)
    }
}

