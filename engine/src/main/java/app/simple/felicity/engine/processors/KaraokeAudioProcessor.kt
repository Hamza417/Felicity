package app.simple.felicity.engine.processors

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

@OptIn(UnstableApi::class)
class KaraokeAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var isEnabled: Boolean = false

    fun setKaraokeModeEnabled(enabled: Boolean) {
        this.isEnabled = enabled
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // We absolutely need stereo for this to work. You can't subtract channels on a Mono track!
        if (inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }

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

        val buffer = replaceOutputBuffer(remaining)

        // The Toggle: If disabled, just pass the audio straight through
        if (!isEnabled) {
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val encoding = inputAudioFormat.encoding

        // PcmUtils.bytesPerSample(encoding) * 2 channels
        val frameSize = if (encoding == C.ENCODING_PCM_16BIT) 4 else 8

        while (inputBuffer.remaining() >= frameSize) {
            if (encoding == C.ENCODING_PCM_16BIT) {
                val leftIn = inputBuffer.short.toFloat() / 32768f
                val rightIn = inputBuffer.short.toFloat() / 32768f

                // THE MAGIC MATH: Subtract Right from Left to erase the center.
                // We divide by 2f to prevent the remaining wide instruments from clipping.
                val karaokeSignal = (leftIn - rightIn) / 2f

                buffer.putShort(floatToShort(karaokeSignal))
                buffer.putShort(floatToShort(karaokeSignal))

            } else if (encoding == C.ENCODING_PCM_FLOAT) {
                val leftIn = inputBuffer.float
                val rightIn = inputBuffer.float

                val karaokeSignal = (leftIn - rightIn) / 2f

                buffer.putFloat(karaokeSignal)
                buffer.putFloat(karaokeSignal)
            }
        }

        buffer.flip()
    }

    private fun floatToShort(value: Float): Short {
        return (value * 32767f)
            .coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
            .toInt()
            .toShort()
    }
}