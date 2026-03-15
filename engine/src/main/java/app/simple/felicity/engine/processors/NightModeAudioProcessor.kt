package app.simple.felicity.engine.processors

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs

@OptIn(UnstableApi::class)
class NightModeAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var isEnabled: Boolean = false

    // Compressor Settings (Hardcoded for "Night Mode" perfection)
    private val threshold = 0.1f       // The limit (roughly -20dB)
    private val ratio = 4f             // Squash loud peaks by a factor of 4
    private val makeupGain = 2.0f      // Boost everything by 2x so quiet parts get louder

    // Smoothing coefficients for 48kHz sample rate
    private val attackCoef = 0.005f    // Fast attack (reacts to loud noises instantly)
    private val releaseCoef = 0.0002f  // Slow release (smoothly lets the volume back up)

    private var currentGain = 1.0f

    fun setNightModeEnabled(enabled: Boolean) {
        this.isEnabled = enabled
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

        val buffer = replaceOutputBuffer(remaining)

        // If Night Mode is off, pass audio through completely untouched
        if (!isEnabled) {
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val encoding = inputAudioFormat.encoding
        val frameSize = if (encoding == C.ENCODING_PCM_16BIT) 2 else 4

        while (inputBuffer.remaining() >= frameSize) {
            val sample = if (encoding == C.ENCODING_PCM_16BIT) {
                inputBuffer.short.toFloat() / 32768f
            } else {
                inputBuffer.float
            }

            val absSample = abs(sample)
            var targetGain = 1.0f

            // If the audio is too loud, calculate how much we need to turn it down
            if (absSample > threshold) {
                val excess = absSample - threshold
                val compressedExcess = excess / ratio
                val targetAmplitude = threshold + compressedExcess
                targetGain = targetAmplitude / absSample
            }

            // Smoothly glide the current volume toward the target volume
            if (targetGain < currentGain) {
                // Attacking (volume is dropping)
                currentGain += attackCoef * (targetGain - currentGain)
            } else {
                // Releasing (volume is recovering)
                currentGain += releaseCoef * (targetGain - currentGain)
            }

            // Apply the squashed gain, then boost the overall track volume
            val processedSample = sample * currentGain * makeupGain

            if (encoding == C.ENCODING_PCM_16BIT) {
                buffer.putShort(floatToShort(processedSample))
            } else {
                buffer.putFloat(processedSample)
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