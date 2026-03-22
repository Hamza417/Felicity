package app.simple.felicity.engine.processors

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import app.simple.felicity.engine.utils.PcmUtils
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * An [AudioProcessor] that applies a stereo-linked dynamic range compressor tuned for
 * comfortable late-night listening.
 *
 * Algorithm overview:
 *  1. A stereo frame (L + R) is read together so both channels share one gain value.
 *  2. An envelope follower smooths the peak level (max of |L|, |R|) using fast attack /
 *     slow release coefficients. Deriving gain from the smoothed envelope — not from the
 *     raw sample — prevents the gain from oscillating at audio frequency across every
 *     zero crossing, which would produce near-zero net compression.
 *  3. Above the threshold the gain is reduced by the configured ratio (hard-knee).
 *  4. A makeup gain is applied after compression so quiet passages come up to a
 *     comfortable audible level.
 *  5. A brick-wall clamp at ±1.0 guards against the brief overshoot that happens
 *     during the attack ramp before the envelope has caught up to a new loud transient.
 *     This behaves as a transparent mastering limiter — the transient is caught within
 *     the 7 ms attack window and is essentially inaudible.
 *
 * Parameter derivation (48 kHz sample rate):
 *  - attackCoef  = 1 − exp(−1 / (48000 × 0.007)) ≈ 0.00300  → ≈7 ms attack
 *  - releaseCoef = 1 − exp(−1 / (48000 × 0.350)) ≈ 0.0000595 → ≈350 ms release
 *
 * Makeup gain budget (no clipping within normal operating range):
 *  At full-scale input (envelope = 1.0): compressed amplitude = 0.1 + 0.9/8 = 0.2125
 *  makeupGain = 3.0 → peak output = 0.2125 × 3.0 = 0.638 (≈ −4 dBFS, safe headroom).
 *
 * Supports all four PCM encodings via [PcmUtils].
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class NightModeAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var isEnabled: Boolean = false

    /**
     * Level above which compression begins (linear amplitude, −20 dBFS).
     * Chosen to catch the bulk of music peaks while leaving the softest
     * passages uncompressed before makeup gain.
     */
    private val threshold = 0.1f

    /**
     * Compression ratio above the threshold.
     * 8:1 strongly limits loud transients while keeping the overall
     * character musical rather than crushed.
     */
    private val ratio = 8f

    /**
     * Linear gain applied after compression (≈ +9.5 dB).
     * Calculated so that a full-scale (0 dBFS) input peak is held to ≈ −4 dBFS
     * after compression and makeup, leaving clean headroom for the brick-wall
     * output clamp to catch any residual attack overshoot.
     */
    private val makeupGain = 3.0f

    /**
     * Per-sample attack coefficient (≈7 ms at 48 kHz).
     * Fast enough to grab loud transients; slow enough not to add distortion
     * to bass fundamentals.
     */
    private val attackCoef = 0.003f

    /**
     * Per-sample release coefficient (≈350 ms at 48 kHz).
     * Slow release prevents audible gain pumping on rhythmic material.
     */
    private val releaseCoef = 0.0000595f

    /**
     * Running envelope level shared across calls.
     * Reset to 0 when the processor is re-enabled to avoid stale gain state.
     */
    private var envelope = 0f

    /**
     * Pre-allocated sample buffer for one audio frame.
     * Resized only on format changes, never inside the hot sample loop,
     * preventing GC pressure at 48 000 frames per second.
     */
    private var sampleBuffer = FloatArray(2)

    /**
     * Enables or disables the compressor.
     * Resets the envelope follower state so compression starts cleanly
     * each time the mode is switched on.
     *
     * @param enabled True to activate the compressor, false to bypass.
     */
    fun setNightModeEnabled(enabled: Boolean) {
        if (enabled && !this.isEnabled) {
            envelope = 0f
        }
        this.isEnabled = enabled
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Resize the pre-allocated buffer to match the actual channel count.
        if (sampleBuffer.size != inputAudioFormat.channelCount) {
            sampleBuffer = FloatArray(inputAudioFormat.channelCount)
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
        val channelCount = inputAudioFormat.channelCount
        val frameSize = PcmUtils.bytesPerSample(encoding) * channelCount

        while (inputBuffer.remaining() >= frameSize) {
            // Read the full frame into the pre-allocated buffer (no allocation per frame).
            for (i in 0 until channelCount) {
                sampleBuffer[i] = PcmUtils.readFloat(inputBuffer, encoding)
            }

            // Stereo-linked peak detection: use the loudest channel so both channels
            // always receive the same gain and the stereo image is not shifted.
            var inputLevel = 0f
            for (i in 0 until channelCount) {
                val a = abs(sampleBuffer[i])
                if (a > inputLevel) inputLevel = a
            }

            // Envelope follower — fast attack, slow release.
            envelope = if (inputLevel > envelope) {
                envelope + attackCoef * (inputLevel - envelope)
            } else {
                envelope + releaseCoef * (inputLevel - envelope)
            }

            // Gain from the smoothed envelope, not the raw sample.
            val gain = if (envelope > threshold) {
                val compressedAmplitude = threshold + (envelope - threshold) / ratio
                (compressedAmplitude / envelope).coerceIn(0f, 1f)
            } else {
                1.0f
            }

            val totalGain = gain * makeupGain

            // Write every channel with the same gain.
            // coerceIn(-1f, 1f) acts as a brick-wall limiter for the brief
            // transient overshoot that occurs during the initial attack ramp.
            for (i in 0 until channelCount) {
                PcmUtils.writeFloat(buffer, (sampleBuffer[i] * totalGain).coerceIn(-1f, 1f), encoding)
            }
        }

        buffer.flip()
    }
}