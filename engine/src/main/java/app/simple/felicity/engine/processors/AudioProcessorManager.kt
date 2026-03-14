package app.simple.felicity.engine.processors

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

/**
 * Manages all audio processing pipelines for the Felicity playback engine.
 *
 * Processor chain order (applied in sequence by DefaultAudioSink):
 *  1. [DownmixAudioProcessor]        Optional multichannel → stereo reduction.
 *  2. [TapeSaturationProcessor]      Tape-style harmonic coloring.
 *  3. [StereoWideningAudioProcessor] M/S stereo image width.
 *  4. [BalanceAudioProcessor]        Constant-power channel routing.
 *
 * All four processors are custom AudioProcessor implementations that support
 * PCM_16BIT, PCM_24BIT, PCM_32BIT, and PCM_FLOAT (Hi-Res pipeline output).
 * None rely on ChannelMixingAudioProcessor, which only supports 16-bit PCM and
 * crashes the entire chain via UnhandledAudioFormatException in Hi-Res mode.
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class AudioProcessorManager {

    /**
     * Downmixes any multichannel stream (1–24 ch) to stereo.
     * Inactive for stereo input (pass-through). Added to the chain only when
     * forced stereo downmix is enabled in AudioPreferences.
     */
    val downmixProcessor: DownmixAudioProcessor = DownmixAudioProcessor()

    /**
     * Tape-style soft saturation. Starts in bypass state (drive = 0.0).
     * Updated via [applyTapeSaturationDrive].
     */
    val tapeSaturationProcessor: TapeSaturationProcessor = TapeSaturationProcessor()

    /**
     * M/S stereo widening/narrowing. Starts in neutral state (width = 1.0, passthrough).
     * Updated via [applyStereoWidth].
     */
    val wideningProcessor: StereoWideningAudioProcessor = StereoWideningAudioProcessor()

    /**
     * Constant-power stereo balance/panning. Starts at center (pan = 0.0, equal gain).
     * Updated via [applyBalance].
     */
    val balanceProcessor: BalanceAudioProcessor = BalanceAudioProcessor()

    /**
     * Applies a new stereo balance pan to [balanceProcessor].
     * See [BalanceAudioProcessor.applyBalance] for the constant-power panning details.
     *
     * @param pan Pan value in [-1.0, 1.0]. 0.0 = center (no change).
     */
    fun applyBalance(pan: Float) {
        balanceProcessor.applyBalance(pan)
    }

    /**
     * Applies a new stereo width to [wideningProcessor].
     * See [StereoWideningAudioProcessor.applyStereoWidth] for the M/S math.
     *
     * @param width Width in [0.0, 2.0]. 0.0 = mono, 1.0 = natural stereo, 2.0 = max wide.
     */
    fun applyStereoWidth(width: Float) {
        wideningProcessor.applyStereoWidth(width)
    }

    /**
     * Applies a new saturation drive to [tapeSaturationProcessor].
     * See [TapeSaturationProcessor.applyDrive] for the transfer-function details.
     *
     * @param drive Drive in [0.0, 4.0]. 0.0 = off (bypass), 4.0 = maximum saturation.
     */
    fun applyTapeSaturationDrive(drive: Float) {
        tapeSaturationProcessor.applyDrive(drive)
    }
}
