package app.simple.felicity.engine.processors

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import app.simple.felicity.engine.utils.PcmUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A custom [AudioProcessor] that applies stereo widening or narrowing via a
 * mid/side (M/S) matrix, operating directly on the raw PCM sample data.
 *
 * Unlike [androidx.media3.common.audio.ChannelMixingAudioProcessor], this processor
 * supports negative cross-channel coefficients. These are required for widening
 * beyond natural stereo (width > 1.0) and cannot be expressed in a
 * [androidx.media3.common.audio.ChannelMixingMatrix], which enforces non-negative values.
 *
 * Supported encodings: [C.ENCODING_PCM_16BIT] and [C.ENCODING_PCM_FLOAT].
 * Non-stereo or unsupported-encoding streams are passed through unchanged (processor inactive).
 *
 * The default state is neutral (width = 1.0, identity passthrough, no audible change).
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class StereoWideningProcessor : AudioProcessor {

    private var inputFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var active = false
    private var inputEnded = false
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER

    /**
     * Direct gain component of the M/S matrix (applied to the same channel).
     * Default 1.0 = full signal of the original channel (neutral/passthrough).
     */
    @Volatile
    private var directGain: Float = 1f

    /**
     * Cross gain component of the M/S matrix (applied to the opposite channel).
     * Default 0.0 = no cross-feed (neutral/passthrough).
     * May be negative for width values greater than 1.0.
     */
    @Volatile
    private var crossGain: Float = 0f

    /**
     * Applies stereo widening via M/S matrix math directly on PCM samples.
     *
     * The audio is conceptually split into a Mid signal (L+R, the "mono" center image)
     * and a Side signal (L-R, the "stereo difference"). Scaling the Side signal controls
     * the perceived stereo image width:
     *
     *  - [width] = 0.0 → full mono (Side = 0, only the center image remains).
     *  - [width] = 1.0 → natural stereo passthrough (identity matrix, no audible change).
     *  - [width] = 2.0 → maximum widening (Side signal doubled).
     *
     * Resulting 2×2 row-major coefficient matrix:
     *   out_L = in_L × directGain + in_R × crossGain
     *   out_R = in_L × crossGain  + in_R × directGain
     *
     * Note that [crossGain] is negative for width > 1.0. This is valid here because this class
     * processes raw PCM bytes directly rather than going through
     * [androidx.media3.common.audio.ChannelMixingMatrix], which forbids negative coefficients.
     *
     * @param width Target stereo width, clamped to [0.0, 2.0].
     */
    fun applyStereoWidth(width: Float) {
        val w = width.coerceIn(0f, 2f)
        val midGain = 0.5f
        val sideGain = 0.5f * w
        directGain = midGain + sideGain  // range: [0.5 .. 1.5]
        crossGain = midGain - sideGain   // range: [0.5 .. -0.5] — negative is intentional here
        Log.d(TAG, "Stereo width applied: width=$w → direct=$directGain, cross=$crossGain")
    }

    /**
     * Activates the processor only for stereo [C.ENCODING_PCM_16BIT] or [C.ENCODING_PCM_FLOAT]
     * streams. All other formats return [AudioProcessor.AudioFormat.NOT_SET] which signals
     * the audio pipeline to bypass this processor entirely.
     */
    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        active = inputAudioFormat.channelCount == 2 &&
                PcmUtils.isEncodingSupported(inputAudioFormat.encoding)
        return if (active) {
            inputFormat = inputAudioFormat
            inputAudioFormat
        } else {
            inputFormat = AudioProcessor.AudioFormat.NOT_SET
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun isActive(): Boolean = active

    /**
     * Reads each stereo frame from [inputBuffer], applies the M/S matrix, and writes the
     * result into an internal output buffer. When inactive, the input buffer is forwarded
     * as-is (pass-through, no allocation).
     *
     * PCM 16-bit samples are clamped to [-32768, 32767] after the matrix multiply to
     * prevent overflow. PCM float samples are not clamped.
     */
    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!active) {
            outputBuffer = inputBuffer
            return
        }

        val remaining = inputBuffer.remaining()
        val buf = acquireOutputBuffer(remaining)

        val d = directGain
        val c = crossGain
        val encoding = inputFormat.encoding
        val frameSize = PcmUtils.bytesPerSample(encoding) * 2

        while (inputBuffer.remaining() >= frameSize) {
            val l = PcmUtils.readFloat(inputBuffer, encoding)
            val r = PcmUtils.readFloat(inputBuffer, encoding)
            PcmUtils.writeFloat(buf, l * d + r * c, encoding)
            PcmUtils.writeFloat(buf, l * c + r * d, encoding)
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

    @Deprecated("Deprecated in Java")
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
    }

    /**
     * Returns a [ByteBuffer] of at least [capacity] bytes with native byte order.
     * Reuses the existing [outputBuffer] when it is large enough; allocates a new one otherwise.
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

    private companion object {
        private const val TAG = "StereoWideningProcessor"
    }
}
