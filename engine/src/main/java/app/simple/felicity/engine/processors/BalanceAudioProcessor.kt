package app.simple.felicity.engine.processors

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import app.simple.felicity.engine.services.PcmUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/**
 * A custom [AudioProcessor] that applies constant-power stereo balance (panning) directly
 * on raw PCM samples.
 *
 * This replaces [androidx.media3.common.audio.ChannelMixingAudioProcessor] for balance,
 * which only supports PCM_16BIT and throws UnhandledAudioFormatException for float and
 * high-resolution formats, crashing the entire processor chain in Hi-Res mode.
 *
 * Supports all four PCM encodings via [app.simple.felicity.engine.services.PcmUtils]: PCM_16BIT, PCM_24BIT, PCM_32BIT,
 * and PCM_FLOAT (Hi-Res pipeline output).
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class BalanceAudioProcessor : AudioProcessor {

    private var inputFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var active = false
    private var inputEnded = false
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER

    /**
     * Left-channel gain derived from the constant-power panning law.
     * Default 1.0 = center (full left signal passes through).
     */
    @Volatile
    private var leftGain: Float = 1f

    /**
     * Right-channel gain derived from the constant-power panning law.
     * Default 1.0 = center (full right signal passes through).
     */
    @Volatile
    private var rightGain: Float = 1f

    /**
     * Updates the balance using a constant-power panning law.
     *
     * [pan] in [-1 .. 1]: -1 = full left, 0 = center (default), +1 = full right.
     *
     * Constant-power law: θ = (pan + 1) / 2 × π/2
     *   leftGain  = cos(θ) → 1.0 at center, 0.0 at full right
     *   rightGain = sin(θ) → 1.0 at full right, 0.0 at full left
     * This keeps perceived loudness constant across the pan range.
     *
     * @param pan Pan value clamped to [-1.0, 1.0].
     */
    fun applyBalance(pan: Float) {
        val p = pan.coerceIn(-1f, 1f)
        val theta = ((p + 1f) / 2f) * (Math.PI / 2.0)
        leftGain = cos(theta).toFloat()
        rightGain = sin(theta).toFloat()
        Log.d(TAG, "Balance applied: pan=$p → L=$leftGain, R=$rightGain")
    }

    /**
     * Activates for stereo streams with a supported PCM encoding.
     * Mono and surround streams are passed through unchanged (processor inactive).
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
     * Reads each stereo frame from [inputBuffer], applies the per-channel gains, and writes
     * the result to the internal output buffer. When inactive, the input is forwarded as-is.
     */
    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!active) {
            outputBuffer = inputBuffer
            return
        }

        val encoding = inputFormat.encoding
        val frameSize = PcmUtils.bytesPerSample(encoding) * 2
        val buf = acquireOutputBuffer(inputBuffer.remaining())

        // Snapshot gains to avoid torn reads if applyBalance is called concurrently.
        val lGain = leftGain
        val rGain = rightGain

        while (inputBuffer.remaining() >= frameSize) {
            val l = PcmUtils.readFloat(inputBuffer, encoding)
            val r = PcmUtils.readFloat(inputBuffer, encoding)
            PcmUtils.writeFloat(buf, l * lGain, encoding)
            PcmUtils.writeFloat(buf, r * rGain, encoding)
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
        private const val TAG = "BalanceAudioProcessor"
    }
}

