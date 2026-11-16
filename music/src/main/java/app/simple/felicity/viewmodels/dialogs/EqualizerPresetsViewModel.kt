package app.simple.felicity.viewmodels.dialogs

import android.app.Application
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.EqualizerPreferences
import app.simple.felicity.repository.database.instances.EqualizerDatabase
import app.simple.felicity.repository.models.EqualizerPreset
import app.simple.felicity.shared.models.ParsedBand
import app.simple.felicity.shared.utils.PEQFileParser
import app.simple.felicity.theme.managers.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel that manages the equalizer preset list and all the database operations
 * (insert, delete) for user-created presets.
 *
 * The preset list is exposed as a [StateFlow] that the dialog collects, so the UI
 * automatically refreshes whenever the user saves or deletes a preset — no manual
 * "reload" button needed. Room's Flow machinery does all the heavy lifting.
 *
 * Built-in presets are seeded lazily the very first time the database is opened.
 * If they are already there from a previous session, the seed operation is skipped
 * to avoid duplicates.
 *
 * @author Hamza417
 */
class EqualizerPresetsViewModel(application: Application) : WrappedViewModel(application) {

    private val db: EqualizerDatabase by lazy {
        EqualizerDatabase.getInstance(getApplication())
    }

    /**
     * Live stream of all equalizer presets ordered by built-in first, then alphabetically.
     * Collecting this flow is all the dialog needs to keep its RecyclerView up to date.
     */
    val presets: StateFlow<List<EqualizerPreset>> = db.equalizerPresetDao()
        .getAllPresetsFlow()
        .map { list ->
            list.filter {
                it.category == EqualizerPreset.CATEGORY_SAVED_PRESETS
            }
        }
        .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
        )

    private val _jsonHeadphoneProfiles = MutableStateFlow<List<EqualizerPreset>>(emptyList())

    val headphoneProfiles: StateFlow<List<EqualizerPreset>> = combine(
            _jsonHeadphoneProfiles,
            db.equalizerPresetDao().getAllPresetsFlow()
                .map { list ->
                    list.filter {
                        it.category == EqualizerPreset.CATEGORY_HEADPHONE_PROFILES
                    }
                }
    ) { json, dbProfiles ->
        dbProfiles + json
    }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
    )

    private val _currentListType = MutableStateFlow(
            try {
                ListType.valueOf(EqualizerPreferences.getPresetsListType())
            } catch (_: Exception) {
                ListType.SAVED_PRESETS
            }
    )
    val currentListType: StateFlow<ListType> = _currentListType

    enum class ListType {
        SAVED_PRESETS, HEADPHONE_PROFILES
    }

    fun switchListType(type: ListType) {
        _currentListType.value = type
        EqualizerPreferences.setPresetsListType(type.name)
    }

    init {
        // Make sure built-in presets are seeded on first launch. If they're already in the
        // database this is a very fast no-op (the DAO uses IGNORE on conflict).
        viewModelScope.launch(Dispatchers.IO) {
            val builtInCount = db.equalizerPresetDao().getBuiltInPresetCount()
            if (builtInCount == 0) {
                db.equalizerPresetDao().insertPresets(EqualizerDatabase.builtInPresets())
            }
            loadHeadphoneProfiles()
        }
    }

    private fun loadHeadphoneProfiles() {
        try {
            val assets = getApplication<Application>().assets
            val files = assets.list("profiles") ?: return
            val profiles = mutableListOf<EqualizerPreset>()

            for (file in files) {
                if (file.endsWith(".txt")) {
                    val content = assets.open("profiles/$file").bufferedReader().use { it.readText() }

                    val parsed = PEQFileParser.parse(content, file, ThemeManager.accent.primaryAccentColor)
                    if (parsed != null && parsed.bands.isNotEmpty()) {
                        profiles.add(EqualizerPreset.fromPeqBands(
                                name = parsed.name,
                                bands = parsed.bands,
                                preampDb = parsed.preampDb,
                                isBuiltIn = true,
                                category = EqualizerPreset.CATEGORY_HEADPHONE_PROFILES
                        ))
                    }
                }
            }

            _jsonHeadphoneProfiles.value = profiles
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Saves the current EQ settings as a new user-created preset with the given name.
     * The operation runs on the IO dispatcher so it never blocks the UI thread.
     *
     * @param name     Display name the user typed in the save dialog.
     * @param gains    The 10 current band gains in dB to snapshot.
     * @param preampDb The current preamp gain in dB.
     */
    fun savePreset(name: String, gains: FloatArray, preampDb: Float, category: String = EqualizerPreset.CATEGORY_SAVED_PRESETS) {
        viewModelScope.launch(Dispatchers.IO) {
            val preset = EqualizerPreset.fromGains(
                    name = name.trim(),
                    gains = gains,
                    preampDb = preampDb,
                    isBuiltIn = false,
                    category = category
            )
            db.equalizerPresetDao().insertPreset(preset)
        }
    }

    /**
     * Saves the current PEQ settings as a new user-created preset with the given name.
     *
     * @param name     Display name the user typed in the save dialog.
     * @param bands    The parsed parametric bands.
     * @param preampDb The preamp gain in dB.
     * @param category The category to save the preset in.
     */
    fun savePeqPreset(name: String, bands: List<ParsedBand>, preampDb: Float, category: String = EqualizerPreset.CATEGORY_SAVED_PRESETS) {
        viewModelScope.launch(Dispatchers.IO) {
            val preset = EqualizerPreset.fromPeqBands(
                    name = name.trim(),
                    bands = bands,
                    preampDb = preampDb,
                    isBuiltIn = false,
                    category = category
            )
            db.equalizerPresetDao().insertPreset(preset)
        }
    }

    /**
     * Permanently removes a preset from the database.
     * Built-in presets should not be passed here — the UI is responsible for
     * checking [EqualizerPreset.isBuiltIn] before calling this.
     *
     * @param preset The preset to delete. Built-in presets are silently ignored.
     */
    fun deletePreset(preset: EqualizerPreset) {
        if (preset.isBuiltIn) return     // Protect the factory presets at all costs.
        viewModelScope.launch(Dispatchers.IO) {
            db.equalizerPresetDao().deletePreset(preset)
        }
    }
}

