package app.simple.felicity.engine.processors

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import app.simple.felicity.preferences.EqualizerPreferences

/**
 * Manages all audio processing pipelines for the Felicity playback engine.
 *
 * Processor chain order (applied in sequence by DefaultAudioSink):
 *  1. [DownmixAudioProcessor]        Optional multichannel → stereo reduction.
 *  2. [KaraokeAudioProcessor]        Optional center-channel (vocal) removal via L−R subtraction.
 *  3. [TapeSaturationProcessor]      Tape-style harmonic coloring.
 *  4. [StereoWideningAudioProcessor] M/S stereo image width.
 *  5. [BalanceAudioProcessor]        Constant-power channel routing.
 *  6. [NightModeAudioProcessor]      Dynamic compressor/limiter for late-night listening.
 *
 * All processors are custom AudioProcessor implementations that support
 * PCM_16BIT, PCM_24BIT, PCM_32BIT, and PCM_FLOAT (Hi-Res pipeline output).
 * None rely on ChannelMixingAudioProcessor, which only supports 16-bit PCM and
 * crashes the entire chain via UnhandledAudioFormatException in Hi-Res mode.
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class AudioProcessorManager {

    /**
     * 10-band graphic equalizer built from biquad peaking-EQ IIR filters.
     * Band center frequencies follow the ISO 10-band standard (31 Hz → 16 kHz).
     * This processor is always present in the pipeline; when all band gains are 0 dB or the
     * EQ is disabled it takes the fast bypass path with zero processing cost.
     * Registered with [app.simple.felicity.engine.managers.EqualizerManager] in the player
     * service so that UI-driven gain changes propagate to the audio thread in real time.
     */
    val equalizerProcessor: EqualizerAudioProcessor = EqualizerAudioProcessor()

    /**
     * Removes leading and trailing silence from the audio stream. Always active in the chain.
     * The threshold can be adjusted via [SilenceTrimmingProcessor.setThreshold] if needed.
     * Default threshold is 0.001f (roughly -60dB), which should trim most silent sections without
     * affecting quiet music.
     */
    val silenceTrimmingProcessor: SilenceTrimmingProcessor = SilenceTrimmingProcessor()

    /**
     * Downmixes any multichannel stream (1–24 ch) to stereo.
     * Inactive for stereo input (pass-through). Added to the chain only when
     * forced stereo downmix is enabled in AudioPreferences.
     */
    val downmixProcessor: DownmixAudioProcessor = DownmixAudioProcessor()

    /**
     * Center-channel (vocal) removal via mid/side L−R subtraction. Starts in bypass state.
     * Requires a stereo PCM source; mono sources are passed through unchanged.
     * Updated via [applyKaraokeMode].
     */
    val karaokeProcessor: KaraokeAudioProcessor = KaraokeAudioProcessor()

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
     * Dynamic compressor/limiter for comfortable late-night listening. Starts in bypass state.
     * Squashes loud peaks and applies makeup gain so quiet passages are more audible.
     * Updated via [applyNightMode].
     */
    val nightModeProcessor: NightModeAudioProcessor = NightModeAudioProcessor()

    /**
     * Passthrough processor that performs a Hanning-windowed FFT on the final processed audio
     * and delivers 40 log-spaced frequency band magnitudes to any attached
     * [VisualizerAudioProcessor.VisualizerListener].
     *
     * Must be placed last in the chain so visualization reflects the fully processed signal.
     * The listener is wired in [app.simple.felicity.engine.services.FelicityPlayerService].
     */
    val visualizerProcessor: VisualizerAudioProcessor = VisualizerAudioProcessor()

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

    /**
     * Enables or disables the [karaokeProcessor].
     * When enabled the processor subtracts the right channel from the left to cancel
     * center-panned vocals; the result is duplicated to both channels.
     *
     * @param enabled True to activate center-channel removal, false to bypass.
     */
    fun applyKaraokeMode(enabled: Boolean) {
        karaokeProcessor.setKaraokeModeEnabled(enabled)
    }

    /**
     * Enables or disables the [nightModeProcessor].
     * When enabled a soft compressor with makeup gain reduces loud transients and
     * raises quiet passages for comfortable low-volume listening.
     *
     * @param enabled True to activate the dynamic compressor, false to bypass.
     */
    fun applyNightMode(enabled: Boolean) {
        nightModeProcessor.setNightModeEnabled(enabled)
    }

    /**
     * Applies the persisted 10-band EQ state (all band gains and the enabled flag) to
     * [equalizerProcessor].
     *
     * Called once from
     * [app.simple.felicity.engine.services.FelicityPlayerService] whenever the audio
     * pipeline is (re)built so the saved EQ settings are always honoured from the first
     * decoded frame.
     */
    fun applyEqualizerState() {
        equalizerProcessor.setAllBandGains(EqualizerPreferences.getAllBandGains())
        equalizerProcessor.isEnabled = EqualizerPreferences.isEqEnabled()
    }
}
