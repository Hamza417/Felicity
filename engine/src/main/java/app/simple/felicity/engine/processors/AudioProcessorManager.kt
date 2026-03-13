package app.simple.felicity.engine.processors

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import kotlin.math.cos
import kotlin.math.sin

/**
 * Manages all audio processing pipelines for the Felicity playback engine.
 *
 * Responsibilities include:
 *  - Maintaining a reusable [ChannelMixingAudioProcessor] for stereo balance panning.
 *  - Building and owning an all-rounder downmix [ChannelMixingAudioProcessor] that handles
 *    any input channel layout from mono (1 ch) up to 24 channels, mixing everything to stereo.
 *  - Owning a [StereoWideningAudioProcessor] that applies M/S widening directly on PCM samples,
 *    supporting the full width range [0.0, 2.0] including negative cross-gain coefficients.
 *
 * Keeping processor construction out of the service makes the service smaller and makes
 * audio processing logic independently testable.
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class AudioProcessorManager {

    /**
     * Reusable processor that applies stereo balance (pan) without rebuilding the whole pipeline.
     * Matrices are updated in-place via [applyBalance].
     */
    val balanceProcessor: ChannelMixingAudioProcessor = ChannelMixingAudioProcessor()

    /**
     * All-rounder downmix processor that maps any channel layout (1 to 24 channels) to stereo.
     * Built once at construction time since its matrices are static.
     */
    val downmixProcessor: ChannelMixingAudioProcessor = buildDownmixProcessor()

    /**
     * Custom M/S processor for stereo widening/narrowing.
     * Starts in neutral state (width = 1.0, identity passthrough). Updated via [applyStereoWidth].
     * Uses [StereoWideningAudioProcessor] instead of [ChannelMixingAudioProcessor] because
     * widening beyond natural stereo requires negative cross-channel coefficients, which
     * [androidx.media3.common.audio.ChannelMixingMatrix] explicitly forbids.
     */
    val wideningProcessor: StereoWideningAudioProcessor = StereoWideningAudioProcessor()

    /**
     * Updates [balanceProcessor] with a constant-power panning matrix.
     *
     * [pan] in [-1 .. 1]: -1 = full left, 0 = center, +1 = full right.
     *
     * Constant-power law: θ = (pan + 1) / 2 * π/2
     *   leftGain  = cos(θ)   → 1.0 at center, 0.707 at extremes, 0.0 at full right
     *   rightGain = sin(θ)   → same mirrored
     * This keeps the perceived loudness constant while panning.
     *
     * @param pan Stereo pan value clamped to [-1.0, 1.0].
     */
    fun applyBalance(pan: Float) {
        val p = pan.coerceIn(-1f, 1f)
        val theta = ((p + 1f) / 2f) * (Math.PI / 2.0)
        val l = cos(theta).toFloat()
        val r = sin(theta).toFloat()
        // 2-in / 2-out mixing matrix (row-major):
        //   [0] out_L <- in_L (left gain),   [1] out_L <- in_R (no cross-talk)
        //   [2] out_R <- in_L (no cross-talk), [3] out_R <- in_R (right gain)
        val mixingMatrix = ChannelMixingMatrix(
                /* inputChannelCount = */ 2,
                /* outputChannelCount = */ 2,
                /* coefficients = */ floatArrayOf(l, 0f, 0f, r)
        )
        balanceProcessor.putChannelMixingMatrix(mixingMatrix)
        Log.d(TAG, "Constant-power pan applied: pan=$p → L=$l, R=$r")
    }

    /**
     * Delegates stereo width to [wideningProcessor].
     * See [StereoWideningAudioProcessor.applyStereoWidth] for the full M/S math.
     *
     * @param width Stereo width in [0.0, 2.0]. 0.0 = mono, 1.0 = natural stereo, 2.0 = max wide.
     */
    fun applyStereoWidth(width: Float) {
        wideningProcessor.applyStereoWidth(width)
    }

    /**
     * Builds a [ChannelMixingAudioProcessor] that can downmix any channel layout
     * (from mono up to 24 channels) to a standard stereo output.
     *
     * Channel assignment strategy:
     *  - Index 0 (Front Left)  → Left output only.
     *  - Index 1 (Front Right) → Right output only.
     *  - Index 2 (Center)      → Equal mix to both outputs at -3 dB (0.707).
     *  - Index 3 (LFE/Sub)     → Dropped entirely to prevent muddy low-end.
     *  - Index 4+ (Surrounds)  → Tucked at 0.5 gain to the side matching their parity.
     *
     * @return A fully configured [ChannelMixingAudioProcessor].
     */
    private fun buildDownmixProcessor(): ChannelMixingAudioProcessor {
        val processor = ChannelMixingAudioProcessor()

        // Handle Mono (1 to 2) - Splits equally to Left and Right
        processor.putChannelMixingMatrix(
                ChannelMixingMatrix(1, 2, floatArrayOf(0.707f, 0.707f))
        )

        // Handle Stereo (2 to 2) - True passthrough
        processor.putChannelMixingMatrix(
                ChannelMixingMatrix(2, 2, floatArrayOf(
                        1f, 0f, // Left input -> Left output
                        0f, 1f  // Right input -> Right output
                ))
        )

        // Dynamically handle EVERYTHING else (3 up to 24 channels)
        for (inputChannels in 3..24) {
            // We need 2 output values (Left, Right) for every input channel
            val coefficients = FloatArray(inputChannels * 2)

            for (i in 0 until inputChannels) {
                val leftOutIndex = i * 2
                val rightOutIndex = i * 2 + 1

                when (i) {
                    0 -> { // Front Left
                        coefficients[leftOutIndex] = 1f
                        coefficients[rightOutIndex] = 0f
                    }
                    1 -> { // Front Right
                        coefficients[leftOutIndex] = 0f
                        coefficients[rightOutIndex] = 1f
                    }
                    2 -> { // Center (Vocals) -> Send equally to Left and Right at -3 dB
                        coefficients[leftOutIndex] = 0.707f
                        coefficients[rightOutIndex] = 0.707f
                    }
                    3 -> { // LFE (Subwoofer) -> Dropped to prevent blown-out/muddy stereo bass
                        coefficients[leftOutIndex] = 0f
                        coefficients[rightOutIndex] = 0f
                    }
                    else -> {
                        // Surrounds, Rears, Heights, and Atmos Objects.
                        // In Android, even indices are generally Left side, odd are Right side.
                        if (i % 2 == 0) {
                            coefficients[leftOutIndex] = 0.5f
                            coefficients[rightOutIndex] = 0f
                        } else {
                            coefficients[leftOutIndex] = 0f
                            coefficients[rightOutIndex] = 0.5f
                        }
                    }
                }
            }

            processor.putChannelMixingMatrix(
                    ChannelMixingMatrix(
                            /* inputChannelCount = */ inputChannels,
                            /* outputChannelCount = */ 2,
                            /* coefficients = */ coefficients
                    )
            )
        }

        return processor
    }

    private companion object {
        private const val TAG = "AudioProcessorManager"
    }
}
