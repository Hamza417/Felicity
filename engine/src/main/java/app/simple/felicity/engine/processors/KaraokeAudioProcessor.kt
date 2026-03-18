package app.simple.felicity.engine.processors

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import app.simple.felicity.engine.services.PcmUtils
import java.nio.ByteBuffer

/**
 * An [AudioProcessor] that performs center-channel (vocal) removal using mid/side
 * matrix decomposition.
 *
 * Algorithm:
 *  - Mid  (center): M = (L + R) × 0.5
 *  - Side (stereo): L_side = L − M = (L − R) × 0.5
 *                   R_side = R − M = (R − L) × 0.5
 *  - Output:        L_out = L − M,  R_out = R − M
 *
 * Why not "L_out = L − R"?
 *  L − R can reach amplitude 2.0 for opposite-polarity stereo content.
 *  PcmUtils then hard-clamps that to ±1, turning the audio into buzzing
 *  square-wave distortion ("alien / fractal noise"). Subtracting the center
 *  M = (L+R)/2 from each channel instead keeps both outputs in [−1, 1]
 *  regardless of the input because:
 *    |L − M| = |(L − R) × 0.5| ≤ (|L| + |R|) × 0.5 ≤ 1.0 for PCM-normalized input.
 *
 * Fully center-panned vocals cancel completely. Slightly off-center content
 * is attenuated in proportion to its correlation with the opposite channel.
 * Fully wide (anti-phase) content is preserved at its original level.
 *
 * Requires a stereo source. Mono sources return [AudioProcessor.AudioFormat.NOT_SET]
 * from [onConfigure] and are passed through unchanged (processor inactive).
 * Supports all four PCM encodings via [app.simple.felicity.engine.services.PcmUtils].
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class KaraokeAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var isEnabled: Boolean = false

    /**
     * Enables or disables center-channel removal.
     * Takes effect immediately on the next [queueInput] call without a flush.
     *
     * @param enabled True to activate vocal removal, false to bypass.
     */
    fun setKaraokeModeEnabled(enabled: Boolean) {
        this.isEnabled = enabled
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // L−R subtraction is meaningless on a mono track.
        if (inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return if (PcmUtils.isEncodingSupported(inputAudioFormat.encoding)) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val buffer = replaceOutputBuffer(remaining)

        if (!isEnabled) {
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val encoding = inputAudioFormat.encoding
        val frameSize = PcmUtils.bytesPerSample(encoding) * 2  // L + R

        while (inputBuffer.remaining() >= frameSize) {
            val leftIn = PcmUtils.readFloat(inputBuffer, encoding)
            val rightIn = PcmUtils.readFloat(inputBuffer, encoding)

            // Center (mid) component — this is what vocals occupy.
            val center = (leftIn + rightIn) * 0.5f

            // Subtract the center from each channel.
            // Output magnitude is always ≤ 1.0 for PCM-normalized input:
            //   |leftIn − center| = |(leftIn − rightIn)| × 0.5 ≤ (1 + 1) × 0.5 = 1.0
            val leftOut = leftIn - center    // = (L − R) × 0.5
            val rightOut = rightIn - center  // = (R − L) × 0.5

            PcmUtils.writeFloat(buffer, leftOut, encoding)
            PcmUtils.writeFloat(buffer, rightOut, encoding)
        }

        buffer.flip()
    }
}