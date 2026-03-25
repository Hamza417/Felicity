package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.milkdrop.managers.PresetManager
import app.simple.felicity.preferences.MilkdropPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the [app.simple.felicity.ui.panels.Milkdrop] fragment.
 *
 * Reads the asset path stored in [MilkdropPreferences.LAST_PRESET] and exposes the
 * raw text content of that `.milk` file via [presetContent] so the fragment can pass
 * it directly to [app.simple.felicity.milkdrop.views.MilkdropSurfaceView.loadPreset].
 *
 * All asset discovery and content loading is delegated to [PresetManager], which is
 * shared with [MilkdropPresetsViewModel][app.simple.felicity.viewmodels.dialogs.MilkdropPresetsViewModel]
 * so the preset list is scanned only once and cached for the lifetime of the process.
 *
 * If no preset has been saved yet, the ViewModel calls [PresetManager.firstPresetPath]
 * to pick a stable default and persists it so that subsequent launches start with the
 * same preset.
 *
 * Call [reloadFromPreferences] whenever [MilkdropPreferences.LAST_PRESET] changes
 * (e.g. from inside [SharedPreferences.OnSharedPreferenceChangeListener]) to trigger
 * a fresh content load and re-emission.
 *
 * @author Hamza417
 */
class MilkdropViewModel(application: Application) : AndroidViewModel(application) {

    private val _presetContent = MutableStateFlow<String?>(null)

    /**
     * Raw text content of the currently selected `.milk` preset, or `null` while
     * loading or if the preset file cannot be read.
     */
    val presetContent: StateFlow<String?> = _presetContent.asStateFlow()

    init {
        loadCurrentPreset()
    }

    /**
     * Re-reads the preset path from [MilkdropPreferences] and emits updated content.
     *
     * Call this from [SharedPreferences.OnSharedPreferenceChangeListener] when
     * [MilkdropPreferences.LAST_PRESET] changes.
     */
    fun reloadFromPreferences() {
        loadCurrentPreset()
    }

    private fun loadCurrentPreset() {
        viewModelScope.launch(Dispatchers.IO) {
            val assets = getApplication<Application>().assets

            val path = MilkdropPreferences.getLastPreset()
                .takeIf { it.isNotBlank() }
                ?: PresetManager.firstPresetPath(assets)?.also {
                    // Persist the auto-selected default so future launches are consistent.
                    MilkdropPreferences.setLastPreset(it)
                }
                ?: return@launch

            _presetContent.value = PresetManager.loadContent(assets, path)
        }
    }
}
