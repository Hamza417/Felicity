package app.simple.felicity.engine.managers

import app.simple.felicity.engine.managers.EqualizerManager.attachProcessor
import app.simple.felicity.engine.managers.EqualizerManager.bandGainsFlow
import app.simple.felicity.engine.managers.EqualizerManager.preampFlow
import app.simple.felicity.engine.managers.EqualizerManager.resetAllBands
import app.simple.felicity.engine.processors.EqualizerAudioProcessor
import app.simple.felicity.preferences.EqualizerPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton manager for the Felicity 10-band graphic equalizer.
 *
 * Bridges the UI/preference layer and the real-time [EqualizerAudioProcessor] that lives
 * inside the ExoPlayer audio-processor chain. All EQ math now happens inline on ExoPlayer's
 * audio thread inside [EqualizerAudioProcessor.queueInput], so no Android hardware
 * [android.media.audiofx.Equalizer] effect or audio-session ID is required.
 *
 * Responsibilities:
 *  - Restores all 10-band gains from [EqualizerPreferences] at cold-boot by calling
 *    [attachProcessor] once from the player service.
 *  - Exposes [bandGainsFlow] as a [StateFlow] so the UI can observe live updates
 *    regardless of whether a change came from the UI, a preset loader, or [resetAllBands].
 *  - Delegates every gain/enable mutation to the live [EqualizerAudioProcessor] reference
 *    supplied by the player service via [attachProcessor].
 *
 * Usage:
 * ```kotlin
 * // In FelicityPlayerService.onCreate, after AudioProcessorManager is ready:
 * EqualizerManager.attachProcessor(audioProcessorManager.equalizerProcessor)
 *
 * // In the equalizer UI fragment:
 * lifecycleScope.launch {
 *     EqualizerManager.bandGainsFlow.collect { gains -> sliders.setAllGains(gains) }
 * }
 * ```
 *
 * @author Hamza417
 */
object EqualizerManager {

    /**
     * The live [EqualizerAudioProcessor] registered by the player service.
     * All public methods are safe no-ops on the processor side when this is null.
     */
    private var processor: EqualizerAudioProcessor? = null

    /**
     * Backing mutable flow holding the latest 10-element band-gain array in dB.
     * Initialized from [EqualizerPreferences] so UI collectors see the persisted state
     * immediately, even before any user interaction.
     */
    private val _bandGainsFlow = MutableStateFlow(EqualizerPreferences.getAllBandGains())

    /**
     * Read-only [StateFlow] of the current 10-band gain array (dB, [-15..+15]).
     * The equalizer UI should collect this to stay in sync with any externally
     * driven changes such as preset loading or [resetAllBands].
     */
    val bandGainsFlow: StateFlow<FloatArray> = _bandGainsFlow.asStateFlow()

    /**
     * Backing mutable flow for the pre-amplifier gain in dB.
     * Initialized from [EqualizerPreferences] so the UI sees the persisted value immediately.
     */
    private val _preampFlow = MutableStateFlow(EqualizerPreferences.getPreampDb())

    /**
     * Read-only [StateFlow] of the current pre-amplifier gain in dB ([-15..+15]).
     * The equalizer UI should collect this to keep the preamp slider in sync with any
     * externally driven change.
     */
    val preampFlow: StateFlow<Float> = _preampFlow.asStateFlow()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Registers the [EqualizerAudioProcessor] that lives inside the ExoPlayer pipeline
     * and immediately applies all persisted band gains and the enabled state to it.
     *
     * Call this once in [app.simple.felicity.engine.services.FelicityPlayerService.onCreate]
     * after [AudioProcessorManager] is constructed.
     *
     * @param equalizerProcessor The processor instance owned by [AudioProcessorManager].
     */
    fun attachProcessor(equalizerProcessor: EqualizerAudioProcessor) {
        processor = equalizerProcessor
        val savedGains = EqualizerPreferences.getAllBandGains()
        equalizerProcessor.setAllBandGains(savedGains)
        equalizerProcessor.isEnabled = EqualizerPreferences.isEqEnabled()
        val savedPreamp = EqualizerPreferences.getPreampDb()
        equalizerProcessor.setPreamp(savedPreamp)
        _bandGainsFlow.value = savedGains
        _preampFlow.value = savedPreamp
    }

    /**
     * Removes the processor reference. Call from
     * [app.simple.felicity.engine.services.FelicityPlayerService.onDestroy] so the
     * manager does not hold a stale reference after the player is torn down.
     */
    fun detachProcessor() {
        processor = null
    }

    // -------------------------------------------------------------------------
    // Band gain control
    // -------------------------------------------------------------------------

    /**
     * Sets the gain for a single EQ band, optionally persists it to [EqualizerPreferences],
     * applies it to the live [EqualizerAudioProcessor], and updates [bandGainsFlow].
     *
     * @param band    Zero-based band index in [0..9] (31 Hz → 16 kHz).
     * @param gainDb  Gain in dB, clamped to [-15..+15].
     * @param persist Pass false to skip the SharedPreferences write when the value was
     *                already saved by the caller (e.g., during a batch preset load).
     */
    fun setBandGain(band: Int, gainDb: Float, persist: Boolean = true) {
        if (band !in 0..9) return
        val clamped = gainDb.coerceIn(-15f, 15f)

        if (persist) {
            EqualizerPreferences.setBandGain(band, clamped)
        }

        processor?.setBandGain(band, clamped)

        val updated = _bandGainsFlow.value.copyOf()
        updated[band] = clamped
        _bandGainsFlow.value = updated
    }

    /**
     * Reads the persisted gain for [band] from [EqualizerPreferences] and applies it to the
     * processor. Called by the player service's
     * [android.content.SharedPreferences.OnSharedPreferenceChangeListener] when a UI-driven
     * preference write arrives so the engine stays in sync.
     *
     * @param band Zero-based band index in [0..9].
     */
    fun applyBandFromPreference(band: Int) {
        if (band !in 0..9) return
        val gainDb = EqualizerPreferences.getBandGain(band)
        setBandGain(band, gainDb, persist = false)
    }

    /**
     * Returns the current gain for [band] in dB, sourced from [bandGainsFlow].
     *
     * @param band Zero-based band index in [0..9].
     */
    fun getBandGain(band: Int): Float {
        if (band !in 0..9) return 0f
        return _bandGainsFlow.value[band]
    }

    /**
     * Returns a snapshot of all 10 band gains in dB from the current [bandGainsFlow] value.
     */
    fun getAllGains(): FloatArray = _bandGainsFlow.value.copyOf()

    /**
     * Resets all 10 bands to 0 dB (flat EQ), persists the flat state, resets the
     * processor, and updates [bandGainsFlow].
     */
    fun resetAllBands() {
        val flat = FloatArray(10)
        EqualizerPreferences.setAllBandGains(flat)
        processor?.resetAllBands()
        _bandGainsFlow.value = flat
    }

    // -------------------------------------------------------------------------
    // Enable / disable
    // -------------------------------------------------------------------------

    /**
     * Enables or disables the equalizer effect and persists the new state.
     * When disabled the processor uses a zero-cost bypass path; no biquad math is executed.
     *
     * @param enabled True to activate the EQ bands, false to bypass them entirely.
     */
    fun setEnabled(enabled: Boolean) {
        EqualizerPreferences.setEqEnabled(enabled)
        processor?.isEnabled = enabled
    }

    /**
     * Returns whether the equalizer is currently marked as enabled in [EqualizerPreferences].
     */
    fun isEnabled(): Boolean = EqualizerPreferences.isEqEnabled()

    // -------------------------------------------------------------------------
    // Preamp
    // -------------------------------------------------------------------------

    /**
     * Sets the pre-amplifier gain, optionally persists it, applies it to the processor,
     * and updates [preampFlow].
     *
     * @param db      Gain in dB, clamped to [-15..+15]. 0 dB = unity.
     * @param persist Pass false to skip the SharedPreferences write when the caller
     *                has already saved the value.
     */
    fun setPreamp(db: Float, persist: Boolean = true) {
        val clamped = db.coerceIn(-15f, 15f)
        if (persist) {
            EqualizerPreferences.setPreampDb(clamped)
        }
        processor?.setPreamp(clamped)
        _preampFlow.value = clamped
    }

    /**
     * Reads the persisted preamp gain from [EqualizerPreferences] and applies it to the
     * processor. Called by the player service's preference listener when the UI saves a
     * new preamp value.
     */
    fun applyPreampFromPreference() {
        val db = EqualizerPreferences.getPreampDb()
        setPreamp(db, persist = false)
    }

    /**
     * Returns the current pre-amplifier gain in dB from [preampFlow].
     */
    fun getPreamp(): Float = _preampFlow.value
}
