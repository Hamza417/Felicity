package app.simple.felicity.engine.managers

import app.simple.felicity.engine.managers.EqualizerManager.attachProcessor
import app.simple.felicity.engine.managers.EqualizerManager.bandGainsFlow
import app.simple.felicity.engine.managers.EqualizerManager.eqModeFlow
import app.simple.felicity.engine.managers.EqualizerManager.peqBandsFlow
import app.simple.felicity.engine.managers.EqualizerManager.preampFlow
import app.simple.felicity.engine.managers.EqualizerManager.resetAllBands
import app.simple.felicity.engine.processors.NativeDspAudioProcessor
import app.simple.felicity.preferences.EqualizerPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton manager for the Felicity 10-band graphic equalizer.
 *
 * Bridges the UI/preference layer and the real-time [NativeDspAudioProcessor] that lives
 * inside the ExoPlayer audio-processor chain. All EQ math runs inline on ExoPlayer's audio
 * thread inside the native DSP engine, so no Android hardware
 * [android.media.audiofx.Equalizer] effect or audio-session ID is required.
 *
 * Responsibilities:
 *  - Restores all 10-band gains and preamp from [EqualizerPreferences] at cold-boot by
 *    calling [attachProcessor] once from the player service.
 *  - Exposes [bandGainsFlow] as a [StateFlow] so the UI can observe live updates
 *    regardless of whether a change came from the UI, a preset loader, or [resetAllBands].
 *  - Delegates every gain/enable/preamp mutation to the live [NativeDspAudioProcessor]
 *    reference supplied by the player service via [attachProcessor].
 *
 * Usage:
 * ```kotlin
 * // In FelicityPlayerService.onCreate, after AudioProcessorManager is ready:
 * EqualizerManager.attachProcessor(audioProcessorManager.nativeDspProcessor)
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
     * The live [NativeDspAudioProcessor] registered by the player service.
     * All public methods are safe no-ops when this is null.
     */
    private var processor: NativeDspAudioProcessor? = null

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

    /**
     * Backing mutable flow that always holds the current parametric EQ band list.
     * Initialized from [EqualizerPreferences] at startup so any UI that collects this
     * immediately sees the last saved PEQ curve without having to wait for a user action.
     */
    private val _peqBandsFlow = MutableStateFlow(loadPeqBandsFromPreferences())

    /**
     * Read-only [StateFlow] exposing the active parametric EQ configuration.
     * Each entry is a (gainDb, qFactor, centerFreqHz) triple representing one biquad filter.
     * The equalizer UI should collect this to keep all three PEQ knobs per band in sync.
     */
    val peqBandsFlow: StateFlow<List<Triple<Float, Float, Float>>> = _peqBandsFlow.asStateFlow()

    /**
     * Backing mutable flow that tracks the currently active EQ mode string.
     * Initialized from [EqualizerPreferences] so the UI sees the correct mode immediately
     * on first launch, and automatically switches when a preset changes it.
     */
    private val _eqModeFlow = MutableStateFlow(EqualizerPreferences.getEqMode())

    /**
     * Read-only [StateFlow] of the current EQ mode string — either
     * [EqualizerPreferences.EQ_MODE_GRAPHIC] or [EqualizerPreferences.EQ_MODE_PARAMETRIC].
     * The equalizer UI should collect this to switch between the graphic sliders and
     * the parametric knobs whenever a preset or the user changes the mode.
     */
    val eqModeFlow: StateFlow<String> = _eqModeFlow.asStateFlow()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Registers the [NativeDspAudioProcessor] that lives inside the ExoPlayer pipeline
     * and immediately applies all persisted band gains, preamp, and the enabled state to it.
     *
     * Call this once in [app.simple.felicity.engine.services.FelicityPlayerService.onCreate]
     * after [AudioProcessorManager] is constructed.
     *
     * @param nativeDspProcessor The processor instance owned by [AudioProcessorManager].
     */
    fun attachProcessor(nativeDspProcessor: NativeDspAudioProcessor) {
        processor = nativeDspProcessor
        val savedGains = EqualizerPreferences.getAllBandGains()
        /** Only the 10 EQ bands are managed here; bass and treble are handled separately. */
        nativeDspProcessor.setEqBands(savedGains)
        nativeDspProcessor.eqEnabled = EqualizerPreferences.isEqEnabled()
        val savedPreamp = EqualizerPreferences.getPreampDb()
        nativeDspProcessor.setPreamp(savedPreamp)
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
     * applies it to the live [NativeDspAudioProcessor], and updates [bandGainsFlow].
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

    /** Returns a snapshot of all 10 band gains in dB from the current [bandGainsFlow] value. */
    fun getAllGains(): FloatArray = _bandGainsFlow.value.copyOf()

    /**
     * Resets all 10 graphic EQ bands to 0 dB (flat), persists the flat state, resets the
     * processor, and updates [bandGainsFlow].
     */
    fun resetAllBands() {
        val flat = FloatArray(10)
        EqualizerPreferences.setAllBandGains(flat)
        processor?.resetEqBands()
        _bandGainsFlow.value = flat
    }

    /**
     * Resets the parametric EQ by zeroing the gain on every band while keeping each
     * band's center frequency and Q factor exactly as the user set them.
     *
     * This is the correct "reset" for PEQ mode — the user spent time positioning the
     * bands on the frequency axis, so we only silence them rather than wiping out their
     * layout. The updated bands are persisted and pushed to the DSP engine immediately.
     */
    fun resetPeqGains() {
        val zeroed = _peqBandsFlow.value.map { (_, q, freq) -> Triple(0f, q, freq) }
        setPeqBands(zeroed)
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
        processor?.eqEnabled = enabled
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

    /** Returns the current pre-amplifier gain in dB from [preampFlow]. */
    fun getPreamp(): Float = _preampFlow.value

    // -------------------------------------------------------------------------
    // EQ mode
    // -------------------------------------------------------------------------

    /**
     * Switches the active EQ mode, persists it, and updates [eqModeFlow] so any
     * observing UI immediately switches between the graphic sliders and the PEQ knobs.
     *
     * @param mode Either [EqualizerPreferences.EQ_MODE_GRAPHIC] or [EqualizerPreferences.EQ_MODE_PARAMETRIC].
     */
    fun setEqMode(mode: String) {
        EqualizerPreferences.setEqMode(mode)
        _eqModeFlow.value = mode
    }

    // -------------------------------------------------------------------------
    // Parametric EQ
    // -------------------------------------------------------------------------

    /**
     * Applies a new set of parametric EQ bands, optionally persists them to
     * [EqualizerPreferences], pushes them to the live [NativeDspAudioProcessor], and
     * updates [peqBandsFlow] so any observing UI rebuilds itself automatically.
     *
     * @param bands   List of (gainDb, qFactor, centerFreqHz) triples, one per filter.
     * @param persist Pass false when the caller has already serialized and saved the value,
     *                to avoid a redundant SharedPreferences write.
     */
    fun setPeqBands(bands: List<Triple<Float, Float, Float>>, persist: Boolean = true) {
        if (persist) {
            EqualizerPreferences.setPeqBandsRaw(serializePeqBands(bands))
        }
        val gains = FloatArray(bands.size) { bands[it].first }
        val qValues = FloatArray(bands.size) { bands[it].second }
        val freqs = FloatArray(bands.size) { bands[it].third }
        processor?.setPeqBands(gains, freqs, qValues)
        _peqBandsFlow.value = bands
    }

    /**
     * Reads the saved PEQ bands from [EqualizerPreferences] and applies them to the
     * processor. Called by the player service's preference listener when the UI saves a
     * new PEQ configuration.
     */
    fun applyPeqBandsFromPreference() {
        val bands = loadPeqBandsFromPreferences()
        setPeqBands(bands, persist = false)
    }

    /** Returns a snapshot of the current parametric EQ band list from [peqBandsFlow]. */
    fun getPeqBands(): List<Triple<Float, Float, Float>> = _peqBandsFlow.value

    /**
     * Serializes a list of PEQ bands into the "gain:q:freq|..." pipe-separated format that
     * [EqualizerPreferences] and [EqualizerPreset] both use for storage.
     *
     * @param bands The bands to serialize.
     * @return The compact string representation, or null when [bands] is empty.
     */
    fun serializePeqBands(bands: List<Triple<Float, Float, Float>>): String? {
        if (bands.isEmpty()) return null
        return bands.joinToString("|") { (gain, q, freq) ->
            "%.2f:%.2f:%.2f".format(gain, q, freq)
        }
    }

    /**
     * Parses the "gain:q:freq|..." string from [EqualizerPreferences] into a list of triples.
     * Malformed segments are silently skipped.
     */
    private fun loadPeqBandsFromPreferences(): List<Triple<Float, Float, Float>> {
        val raw = EqualizerPreferences.getPeqBandsRaw() ?: return emptyList()
        return raw.split("|").mapNotNull { segment ->
            val parts = segment.split(":")
            if (parts.size < 3) return@mapNotNull null
            val gain = parts[0].toFloatOrNull() ?: return@mapNotNull null
            val q = parts[1].toFloatOrNull() ?: return@mapNotNull null
            val freq = parts[2].toFloatOrNull() ?: return@mapNotNull null
            Triple(gain, q, freq)
        }
    }
}
