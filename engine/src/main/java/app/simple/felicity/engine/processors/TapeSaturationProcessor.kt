package app.simple.felicity.engine.processors

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import app.simple.felicity.engine.services.PcmUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * A custom [AudioProcessor] that applies tape-style soft saturation (harmonic distortion)
 * to the audio stream, operating directly on raw PCM samples.
 *
 * The saturation algorithm uses a soft-clipping transfer function derived from the
 * algebraic sigmoid: y = x × drive / (1 + |x × drive|). A gain compensation factor
 * of (1 + drive) / drive is then applied to normalize the output level, ensuring
 * that the subjective loudness remains roughly consistent across all drive settings.
 *
 * Supported encodings: [C.ENCODING_PCM_16BIT] and [C.ENCODING_PCM_FLOAT].
 * Non-stereo or unsupported-encoding streams are passed through unchanged.
 *
 * The default drive is 0.0 (off, identity passthrough, no processing).
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class TapeSaturationProcessor : AudioProcessor {

    private var inputFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var active = false
    private var inputEnded = false
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER

    /**
     * Saturation drive amount in [0.0, 4.0].
     * 0.0 = clean bypass, 1.0 = subtle warmth, 2.0 = punchy, 4.0 = heavy saturation.
     */
    @Volatile
    private var drive: Float = 0f

    /**
     * Applies a new saturation drive level.
     *
     * The drive value is clamped to [0.0, 4.0]:
     *  - 0.0 → clean bypass (no processing, identity passthrough).
     *  - 1.0 → subtle harmonic warmth, characteristic of lightly driven tape.
     *  - 2.0 → punchy mid-range coloring.
     *  - 4.0 → heavy saturation with significant harmonic content.
     *
     * @param newDrive Target drive value, clamped to [0.0, 4.0].
     */
    fun applyDrive(newDrive: Float) {
        drive = newDrive.coerceIn(0f, 4f)
        Log.d(TAG, "Tape saturation drive applied: $drive")
    }

    /**
     * Activates the processor for stereo or mono [C.ENCODING_PCM_16BIT] or
     * [C.ENCODING_PCM_FLOAT] streams. Any other format returns [AudioProcessor.AudioFormat.NOT_SET]
     * to signal the pipeline to bypass this processor entirely.
     */
    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        active = PcmUtils.isEncodingSupported(inputAudioFormat.encoding)
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
     * Processes each PCM sample through the soft-clipping transfer function.
     *
     * When [drive] is effectively zero the input buffer is forwarded as-is, avoiding
     * allocation and the division-by-zero in the compensation formula.
     *
     * Transfer function per sample:
     *   y = x × drive / (1 + |x × drive|) × (1 + drive) / drive
     *
     * PCM 16-bit output is clamped to [-32768, 32767] to prevent hard clipping.
     * PCM float output is not clamped.
     */
    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!active) {
            outputBuffer = inputBuffer
            return
        }

        val d = drive

        if (d < DRIVE_EPSILON) {
            outputBuffer = inputBuffer
            return
        }

        val encoding = inputFormat.encoding
        val bps = PcmUtils.bytesPerSample(encoding)
        val remaining = inputBuffer.remaining()
        val buf = acquireOutputBuffer(remaining)

        val compensation = (1f + d) / d

        while (inputBuffer.remaining() >= bps) {
            var s = PcmUtils.readFloat(inputBuffer, encoding)
            s *= d
            s /= (1f + abs(s))
            s *= compensation
            PcmUtils.writeFloat(buf, s, encoding)
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
    }

    /**
     * Returns a [ByteBuffer] of at least [capacity] bytes with native byte order.
     * Reuses the existing [outputBuffer] when large enough to avoid per-chunk allocation.
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
        private const val TAG = "TapeSaturationProcessor"

        /** Drive values below this threshold are treated as zero (clean bypass). */
        private const val DRIVE_EPSILON = 0.001f
    }
}