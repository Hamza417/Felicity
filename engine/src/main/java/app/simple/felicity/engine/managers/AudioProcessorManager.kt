package app.simple.felicity.engine.managers

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import app.simple.felicity.engine.processors.DownmixProcessor
import app.simple.felicity.engine.processors.KaraokeProcessor
import app.simple.felicity.engine.processors.NativeDspAudioProcessor
import app.simple.felicity.engine.processors.NightModeProcessor
import app.simple.felicity.engine.processors.SilenceTrimmingProcessor
import app.simple.felicity.engine.processors.VisualizerProcessor
import app.simple.felicity.preferences.EqualizerPreferences

/**
 * Manages all audio processing pipelines for the Felicity playback engine.
 *
 * Processor chain order (applied in sequence by DefaultAudioSink):
 *  1. [SilenceTrimmingProcessor]    Optional leading/trailing silence removal.
 *  2. [DownmixProcessor]            Optional multichannel → stereo reduction.
 *  3. [KaraokeProcessor]            Optional center-channel (vocal) removal via L−R subtraction.
 *  4. [NativeDspAudioProcessor]     Unified native DSP: 10-band EQ, bass/treble shelves,
 *                                   stereo widening (M/S), constant-power balance,
 *                                   and tape-style saturation — all in one JNI call.
 *  5. [NightModeProcessor]          Dynamic compressor/limiter for late-night listening.
 *  6. [VisualizerProcessor]         Hann-windowed FFT spectrum capture on the final signal.
 *
 * All processors support PCM_16BIT, PCM_24BIT, PCM_32BIT, and PCM_FLOAT (Hi-Res output).
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class AudioProcessorManager {

    /**
     * Passthrough processor that trims leading and trailing digital silence.
     * Always present in the chain; the threshold can be tuned via
     * [SilenceTrimmingProcessor.setThreshold]. Default: −60 dB (~0.001 linear).
     */
    val silenceTrimmingProcessor: SilenceTrimmingProcessor = SilenceTrimmingProcessor()

    /**
     * Downmixes any multichannel stream (1–24 ch) to stereo.
     * Inactive for stereo input (pass-through). Added to the chain only when
     * forced stereo downmix is enabled in AudioPreferences.
     */
    val downmixProcessor: DownmixProcessor = DownmixProcessor()

    /**
     * Center-channel (vocal) removal via mid/side L−R subtraction. Starts in bypass state.
     * Requires a stereo PCM source; mono sources are passed through unchanged.
     */
    val karaokeProcessor: KaraokeProcessor = KaraokeProcessor()

    /**
     * Passthrough processor that performs a Hanning-windowed FFT on the final processed audio
     * and delivers 40 log-spaced frequency band magnitudes to any attached
     * [VisualizerProcessor.VisualizerListener] or via the lock-free twin-buffer path.
     *
     * Must be the last processor in the chain so visualization reflects the fully
     * processed signal. The listener is wired in [FelicityPlayerService].
     */
    val visualizerProcessor: VisualizerProcessor = VisualizerProcessor()

    /**
     * Unified native DSP processor that replaces the six individual Kotlin-based effect
     * processors (EQ, bass, treble, widening, balance, saturation). Delegates the entire
     * chain to native ARM NEON–optimized C++ code in a single JNI hot-path call.
     *
     * Shares the [VisualizerProcessor]'s [FFTContext] so the spectrum display always
     * reflects the post-effects signal even without an extra FFT pass.
     */
    val nativeDspProcessor: NativeDspAudioProcessor = NativeDspAudioProcessor(visualizerProcessor)

    /**
     * Dynamic compressor/limiter for comfortable late-night listening. Starts in bypass state.
     * Squashes loud peaks and applies makeup gain so quiet passages are more audible.
     */
    val nightModeProcessor: NightModeProcessor = NightModeProcessor()

    /**
     * Applies a new stereo balance pan to [nativeDspProcessor].
     *
     * @param pan Pan value in [-1.0, 1.0]. 0.0 = center (no change).
     */
    fun applyBalance(pan: Float) {
        nativeDspProcessor.setBalance(pan)
    }

    /**
     * Applies a new stereo width to [nativeDspProcessor].
     *
     * @param width Width in [0.0, 2.0]. 0.0 = mono, 1.0 = natural stereo, 2.0 = max wide.
     */
    fun applyStereoWidth(width: Float) {
        nativeDspProcessor.setStereoWidth(width)
    }

    /**
     * Applies a new saturation drive to [nativeDspProcessor].
     *
     * @param drive Drive in [0.0, 4.0]. 0.0 = off (bypass), 4.0 = maximum saturation.
     */
    fun applyTapeSaturationDrive(drive: Float) {
        nativeDspProcessor.setSaturation(drive)
    }

    /**
     * Enables or disables the [karaokeProcessor].
     *
     * @param enabled True to activate center-channel removal, false to bypass.
     */
    fun applyKaraokeMode(enabled: Boolean) {
        karaokeProcessor.setKaraokeModeEnabled(enabled)
    }

    /**
     * Enables or disables the [nightModeProcessor].
     *
     * @param enabled True to activate the dynamic compressor, false to bypass.
     */
    fun applyNightMode(enabled: Boolean) {
        nightModeProcessor.setNightModeEnabled(enabled)
    }

    /**
     * Applies a new bass low-shelf gain to [nativeDspProcessor].
     *
     * @param db Gain in dB in [-12.0, +12.0]. 0.0 = flat bypass.
     */
    fun applyBass(db: Float) {
        nativeDspProcessor.setBassDb(db)
    }

    /**
     * Applies a new treble high-shelf gain to [nativeDspProcessor].
     *
     * @param db Gain in dB in [-12.0, +12.0]. 0.0 = flat bypass.
     */
    fun applyTreble(db: Float) {
        nativeDspProcessor.setTrebleDb(db)
    }

    /**
     * Applies the persisted 10-band EQ state (all band gains, preamp, and the enabled flag)
     * plus bass and treble shelf gains to [nativeDspProcessor].
     *
     * Called once from [FelicityPlayerService] when the audio pipeline is (re)built so the
     * saved settings are honored from the very first decoded frame.
     */
    fun applyEqualizerState() {
        nativeDspProcessor.setEqBands(
                EqualizerPreferences.getAllBandGains(),
                EqualizerPreferences.getBassDb(),
                EqualizerPreferences.getTrebleDb()
        )
        nativeDspProcessor.setPreamp(EqualizerPreferences.getPreampDb())
        nativeDspProcessor.eqEnabled = EqualizerPreferences.isEqEnabled()
    }
}