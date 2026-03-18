package app.simple.felicity.engine.processors

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import app.simple.felicity.engine.services.PcmUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A custom [AudioProcessor] that downmixes any multi-channel PCM stream (1 to 24 channels)
 * to stereo (2 channels), supporting all four common PCM encodings via [app.simple.felicity.engine.services.PcmUtils].
 *
 * This replaces [androidx.media3.common.audio.ChannelMixingAudioProcessor] for downmixing,
 * which only supports [androidx.media3.common.C.ENCODING_PCM_16BIT] and fails in Hi-Res mode.
 *
 * Mixing coefficients per input channel index:
 *  - 0 (Front Left)   → [1.0, 0.0]   full left output.
 *  - 1 (Front Right)  → [0.0, 1.0]   full right output.
 *  - 2 (Center)       → [0.707, 0.707] equal mix at -3 dB to both outputs.
 *  - 3 (LFE/Sub)      → [0.0, 0.0]   dropped entirely to prevent muddy low-end.
 *  - 4+ even indices  → [0.5, 0.0]   left-side surrounds/heights at half gain.
 *  - 4+ odd indices   → [0.0, 0.5]   right-side surrounds/heights at half gain.
 *  - Mono (1 ch)      → [0.707, 0.707] equal split to both outputs.
 *
 * Stereo input (2 channels) is passed through as-is (processor inactive).
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class DownmixAudioProcessor : AudioProcessor {

    private var inputFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var active = false
    private var inputEnded = false
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER

    private var inputChannelCount: Int = 0
    private var leftCoeffs: FloatArray = FloatArray(0)
    private var rightCoeffs: FloatArray = FloatArray(0)

    /**
     * Activates for non-stereo multi-channel streams with a supported PCM encoding.
     * Stereo (2 ch) input returns [AudioProcessor.AudioFormat.NOT_SET] (pass-through, no op).
     * When active, the returned output format has the same sample rate and encoding as the
     * input but with channelCount forced to 2 so subsequent processors see stereo.
     */
    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val chCount = inputAudioFormat.channelCount
        val enc = inputAudioFormat.encoding

        // Pass through if already stereo, or encoding is not supported.
        if (chCount == 2 || !PcmUtils.isEncodingSupported(enc)) {
            active = false
            inputFormat = AudioProcessor.AudioFormat.NOT_SET
            outputFormat = AudioProcessor.AudioFormat.NOT_SET
            return AudioProcessor.AudioFormat.NOT_SET
        }

        active = true
        inputFormat = inputAudioFormat
        inputChannelCount = chCount
        buildCoefficients(chCount)

        outputFormat = AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, enc)
        Log.d(TAG, "Downmix configured: $chCount ch → 2 ch, enc=$enc")
        return outputFormat
    }

    override fun isActive(): Boolean = active

    /**
     * Reads each multichannel frame from [inputBuffer], applies the mixing coefficients,
     * and writes the resulting stereo frame to the internal output buffer.
     * When inactive, the input is forwarded as-is.
     *
     * Output buffer size = (inputFrames × bytesPerSample × 2).
     */
    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!active) {
            outputBuffer = inputBuffer
            return
        }

        val encoding = inputFormat.encoding
        val bps = PcmUtils.bytesPerSample(encoding)
        val frameSize = bps * inputChannelCount
        val inputFrames = inputBuffer.remaining() / frameSize
        val buf = acquireOutputBuffer(inputFrames * bps * 2)

        val lCoeffs = leftCoeffs
        val rCoeffs = rightCoeffs

        repeat(inputFrames) {
            var leftOut = 0f
            var rightOut = 0f
            for (i in 0..inputChannelCount.minus(1)) {
                val s = PcmUtils.readFloat(inputBuffer, encoding)
                leftOut += s * lCoeffs[i]
                rightOut += s * rCoeffs[i]
            }
            PcmUtils.writeFloat(buf, leftOut, encoding)
            PcmUtils.writeFloat(buf, rightOut, encoding)
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
        outputFormat = AudioProcessor.AudioFormat.NOT_SET
    }

    /**
     * Pre-computes the per-channel left and right mixing coefficients for [channelCount].
     * Called once in [configure] so [queueInput] only does array lookups, not branching.
     */
    private fun buildCoefficients(channelCount: Int) {
        leftCoeffs = FloatArray(channelCount)
        rightCoeffs = FloatArray(channelCount)
        when (channelCount) {
            1 -> {
                leftCoeffs[0] = 0.707f
                rightCoeffs[0] = 0.707f
            }
            else -> {
                for (i in 0 until channelCount) {
                    when (i) {
                        0 -> {
                            leftCoeffs[0] = 1f; rightCoeffs[0] = 0f
                        }
                        1 -> {
                            leftCoeffs[1] = 0f; rightCoeffs[1] = 1f
                        }
                        2 -> {
                            leftCoeffs[2] = 0.707f; rightCoeffs[2] = 0.707f
                        }
                        3 -> {
                            leftCoeffs[3] = 0f; rightCoeffs[3] = 0f
                        }
                        else -> {
                            if (i % 2 == 0) {
                                leftCoeffs[i] = 0.5f; rightCoeffs[i] = 0f
                            } else {
                                leftCoeffs[i] = 0f; rightCoeffs[i] = 0.5f
                            }
                        }
                    }
                }
            }
        }
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
        private const val TAG = "DownmixAudioProcessor"
    }
}

