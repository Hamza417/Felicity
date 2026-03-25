package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
 * If no preset has been saved yet, the ViewModel automatically picks the first entry
 * found in the `presets/points/` asset directory and saves it to preferences so that
 * subsequent launches start with a consistent default.
 *
 * Call [reloadFromPreferences] whenever [MilkdropPreferences.LAST_PRESET] changes
 * (e.g. from inside [SharedPreferences.OnSharedPreferenceChangeListener]) to trigger
 * a fresh asset read and re-emission.
 *
 * @author Hamza417
 */
class MilkdropViewModel(application: Application) : AndroidViewModel(application) {

    private val _presetContent = MutableStateFlow<String?>(null)

    /**
     * Raw text content of the currently selected `.milk` preset, or `null` while loading
     * or if the preset file cannot be read.
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
            val path = MilkdropPreferences.getLastPreset()
                .takeIf { it.isNotBlank() }
                ?: resolveFirstPreset()
                ?: return@launch

            try {
                val content = getApplication<Application>().assets
                    .open(path)
                    .bufferedReader()
                    .readText()
                _presetContent.value = content
            } catch (e: Exception) {
                _presetContent.value = null
            }
        }
    }

    /**
     * Picks the first `.milk` file from the asset directory, persists it so future
     * launches have a stable default, and returns its path.
     *
     * @return The asset path of the first preset, or `null` if none are found.
     */
    private fun resolveFirstPreset(): String? {
        val assets = getApplication<Application>().assets
        val first = assets.list(PRESET_DIR)
            ?.filter { it.endsWith(".milk") }
            ?.sorted()
            ?.firstOrNull()
            ?: return null
        val path = "$PRESET_DIR/$first"
        MilkdropPreferences.setLastPreset(path)
        return path
    }

    private companion object {
        private const val PRESET_DIR = "presets/points"
    }
}

