package app.simple.felicity.viewmodels.panels

import android.app.Application
import androidx.lifecycle.viewModelScope
import app.simple.felicity.engine.managers.EqualizerManager
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.EqualizerPreferences
import app.simple.felicity.repository.database.instances.EqualizerDatabase
import app.simple.felicity.repository.models.EqualizerPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * ViewModel for the [app.simple.felicity.ui.panels.Equalizer] panel.
 *
 * Batch-reads all persisted equalizer preferences on [Dispatchers.IO] during construction
 * so the main thread is never blocked by disk I/O during fragment startup. The result is
 * published via [initialState]; the fragment observes this flow once to populate all
 * controls without any synchronous [EqualizerPreferences] access in `onViewCreated`.
 *
 * Speaker-screen and reverb-screen preferences are included in the same batch so a single
 * background coroutine warms up the entire [android.content.SharedPreferences] instance,
 * making all subsequent reads instant HashMap lookups.
 *
 * It also keeps the full preset list in memory (there are only about 14 built-ins plus
 * however many the user creates, so this is essentially free) and computes [currentPresetName]
 * by comparing the live band gains and preamp against every preset. If nothing matches,
 * it falls back to "Custom" so the user always knows exactly where their EQ stands.
 *
 * @author Hamza417
 */
class EqualizerViewModel(application: Application) : WrappedViewModel(application) {

    private val _initialState = MutableStateFlow<EqualizerInitialState?>(null)

    /**
     * Emits a fully populated [EqualizerInitialState] once all preferences have been read
     * from disk on the IO thread. The value is `null` until that read completes, which
     * typically happens within a single frame on modern devices.
     */
    val initialState: StateFlow<EqualizerInitialState?> = _initialState.asStateFlow()

    /** Lazy reference to the equalizer database — opened only when first needed. */
    private val db: EqualizerDatabase by lazy {
        EqualizerDatabase.getInstance(getApplication())
    }

    /**
     * Live list of every saved preset, ordered built-ins first then alphabetically.
     * Kept entirely in memory because the total count is tiny (think 15–30 entries tops),
     * and having it ready here lets us check for name matches without any extra DB queries.
     */
    val allPresets: StateFlow<List<EqualizerPreset>> = db.equalizerPresetDao()
        .getAllPresetsFlow()
        .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
        )

    /**
     * Reacts to changes in the live band gains, the preamp level, and the preset list
     * all at once. If the current EQ state exactly matches a saved preset it emits that
     * preset's name; otherwise it emits "Custom". Updates happen on the collector's
     * thread (main thread in the fragment), so the UI just observes and updates the label.
     */
    val currentPresetName: StateFlow<String> = combine(
            EqualizerManager.bandGainsFlow,
            EqualizerManager.preampFlow,
            allPresets
    ) { gains, preamp, presets ->
        findMatchingPreset(gains, preamp, presets)?.name ?: CUSTOM_PRESET_NAME
    }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CUSTOM_PRESET_NAME
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _initialState.value = EqualizerInitialState(
                    isEqEnabled = EqualizerPreferences.isEqEnabled(),
                    bandGains = EqualizerPreferences.getAllBandGains(),
                    preampDb = EqualizerPreferences.getPreampDb(),
                    bassDb = EqualizerPreferences.getBassDb(),
                    trebleDb = EqualizerPreferences.getTrebleDb(),
                    balance = EqualizerPreferences.getBalance(),
                    stereoWidth = EqualizerPreferences.getStereoWidth(),
                    tapeSaturationDrive = EqualizerPreferences.getTapeSaturationDrive(),
                    reverbMix = EqualizerPreferences.getReverbMix(),
                    reverbDecay = EqualizerPreferences.getReverbDecay(),
                    reverbSize = EqualizerPreferences.getReverbSize(),
                    reverbDamp = EqualizerPreferences.getReverbDamp()
            )
        }
    }

    /**
     * Tries to save the current EQ settings under [name]. If a preset with that exact name
     * (case-insensitive) already exists, the [onDuplicate] callback is called on the main
     * thread and nothing is written. If the name is free, the preset is inserted and
     * [onSuccess] is called on the main thread. Either way, the operation is fully
     * off-thread — the caller never has to worry about blocking the UI.
     *
     * @param name        The name the user typed in. Leading/trailing spaces are trimmed.
     * @param gains       Snapshot of all 10 band gains in dB to persist.
     * @param preampDb    The current preamp level in dB.
     * @param onDuplicate Called on the main thread when a preset with this name already exists.
     * @param onSuccess   Called on the main thread after the preset is successfully saved.
     */
    fun savePreset(
            name: String,
            gains: FloatArray,
            preampDb: Float,
            onDuplicate: () -> Unit,
            onSuccess: () -> Unit
    ) {
        val trimmedName = name.trim()
        viewModelScope.launch(Dispatchers.IO) {
            val existing = db.equalizerPresetDao().getPresetByName(trimmedName)
            if (existing != null) {
                withContext(Dispatchers.Main) { onDuplicate() }
                return@launch
            }
            val preset = EqualizerPreset.fromGains(
                    name = trimmedName,
                    gains = gains,
                    preampDb = preampDb,
                    isBuiltIn = false
            )
            db.equalizerPresetDao().insertPreset(preset)
            withContext(Dispatchers.Main) { onSuccess() }
        }
    }

    /**
     * Walks the [presets] list and returns the first one whose band gains and preamp level
     * are within floating-point rounding tolerance of [gains] and [preamp]. Returns null
     * if no preset matches, which means the EQ is in a "Custom" state.
     *
     * The tolerance of 0.005 dB is half the smallest step that can be represented after
     * the "%.2f" serialization round-trip, so this check never gives false negatives.
     */
    private fun findMatchingPreset(
            gains: FloatArray,
            preamp: Float,
            presets: List<EqualizerPreset>
    ): EqualizerPreset? {
        return presets.firstOrNull { preset ->
            val presetGains = preset.getBandGains()
            val gainsMatch = gains.size == presetGains.size &&
                    gains.indices.all { i -> abs(gains[i] - presetGains[i]) < GAIN_TOLERANCE }
            val preampMatch = abs(preamp - preset.preampDb) < GAIN_TOLERANCE
            gainsMatch && preampMatch
        }
    }

    /**
     * Immutable snapshot of all equalizer preferences loaded in one IO-thread batch from
     * [EqualizerPreferences]. Passed to the fragment once the background read completes
     * so that view initialization never blocks the main thread.
     *
     * @property isEqEnabled Whether the 10-band graphic equalizer is active.
     * @property bandGains Persisted gains for all 10 EQ bands, in dB.
     * @property preampDb Pre-amplifier gain in dB applied before all EQ band filters.
     * @property bassDb Low-shelf bass gain in dB at 250 Hz.
     * @property trebleDb High-shelf treble gain in dB at 4000 Hz.
     * @property balance Stereo pan value in [-1 .. 1].
     * @property stereoWidth Stereo widening factor in [0 .. 2].
     * @property tapeSaturationDrive Tape saturation drive level in [0 .. 4].
     * @property reverbMix Reverb wet/dry mix in [0 .. 1].
     * @property reverbDecay Reverb decay time in [0 .. 1].
     * @property reverbSize Reverb room size in [0 .. 1].
     * @property reverbDamp Reverb high-frequency damping in [0 .. 1].
     */
    data class EqualizerInitialState(
            val isEqEnabled: Boolean,
            val bandGains: FloatArray,
            val preampDb: Float,
            val bassDb: Float,
            val trebleDb: Float,
            val balance: Float,
            val stereoWidth: Float,
            val tapeSaturationDrive: Float,
            val reverbMix: Float,
            val reverbDecay: Float,
            val reverbSize: Float,
            val reverbDamp: Float
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EqualizerInitialState) return false
            return isEqEnabled == other.isEqEnabled
                    && bandGains.contentEquals(other.bandGains)
                    && preampDb == other.preampDb
                    && bassDb == other.bassDb
                    && trebleDb == other.trebleDb
                    && balance == other.balance
                    && stereoWidth == other.stereoWidth
                    && tapeSaturationDrive == other.tapeSaturationDrive
                    && reverbMix == other.reverbMix
                    && reverbDecay == other.reverbDecay
                    && reverbSize == other.reverbSize
                    && reverbDamp == other.reverbDamp
        }

        override fun hashCode(): Int {
            var result = isEqEnabled.hashCode()
            result = 31 * result + bandGains.contentHashCode()
            result = 31 * result + preampDb.hashCode()
            result = 31 * result + bassDb.hashCode()
            result = 31 * result + trebleDb.hashCode()
            result = 31 * result + balance.hashCode()
            result = 31 * result + stereoWidth.hashCode()
            result = 31 * result + tapeSaturationDrive.hashCode()
            result = 31 * result + reverbMix.hashCode()
            result = 31 * result + reverbDecay.hashCode()
            result = 31 * result + reverbSize.hashCode()
            result = 31 * result + reverbDamp.hashCode()
            return result
        }
    }

    companion object {
        /**
         * The label shown when the current EQ state does not match any saved preset.
         * Using a constant here makes it easy to check against this sentinel value elsewhere.
         */
        const val CUSTOM_PRESET_NAME = "Custom"

        /**
         * Maximum absolute difference in dB that we still consider a "match" between
         * the live gain and a preset's stored gain. Half of 0.01 covers the rounding error
         * introduced by the "%.2f" serialization round-trip used in [EqualizerPreset.gainsToRaw].
         */
        private const val GAIN_TOLERANCE = 0.005f
    }
}
