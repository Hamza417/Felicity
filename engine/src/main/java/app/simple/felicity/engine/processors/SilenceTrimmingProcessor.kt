package app.simple.felicity.engine.processors

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs

@OptIn(UnstableApi::class)
class SilenceTrimmingProcessor : BaseAudioProcessor() {

    // The Silence Threshold.
    // 0.0001f is roughly -80dB, which is very quiet and will only trim the most silent sections, leaving quiet music intact.
    // 0.001f is roughly -60dB. Anything quieter than this is considered "silence".
    // 0.01f is roughly -40dB, which is a more aggressive threshold that may trim quiet music but will catch more silent sections.
    @Volatile
    private var silenceThreshold: Float = 0.001f

    fun setThreshold(threshold: Float) {
        this.silenceThreshold = threshold
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
                inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        var isSilence = true

        // Mark our current position so we can rewind the buffer after scanning it
        inputBuffer.mark()

        // Scan the buffer to find if there is ANY loud audio inside it
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            while (inputBuffer.hasRemaining()) {
                val sample = inputBuffer.short.toFloat() / 32768f
                if (abs(sample) > silenceThreshold) {
                    isSilence = false
                    break // We found music! Stop scanning.
                }
            }
        } else if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            while (inputBuffer.hasRemaining()) {
                val sample = inputBuffer.float
                if (abs(sample) > silenceThreshold) {
                    isSilence = false
                    break
                }
            }
        }

        // Rewind the buffer back to the start
        inputBuffer.reset()

        if (isSilence) {
            // THE MAGIC TRICK:
            // We advance the input buffer's position to the very end, tricking ExoPlayer
            // into thinking we processed it, but we return WITHOUT putting anything 
            // into the output buffer. The audio is successfully deleted!
            inputBuffer.position(inputBuffer.limit())
            return
        }

        // It wasn't silence! Pass the whole chunk through exactly as it arrived.
        val buffer = replaceOutputBuffer(remaining)
        buffer.put(inputBuffer)
        buffer.flip()
    }
}