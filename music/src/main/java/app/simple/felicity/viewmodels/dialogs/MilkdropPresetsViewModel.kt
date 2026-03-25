package app.simple.felicity.viewmodels.dialogs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.milkdrop.managers.PresetManager
import app.simple.felicity.milkdrop.models.MilkdropPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the [app.simple.felicity.dialogs.player.MilkdropPresets] dialog.
 *
 * Delegates all asset discovery to [PresetManager], which recursively scans every
 * subdirectory under [PresetManager.PRESETS_ROOT] and caches the result so that
 * this ViewModel and [MilkdropViewModel][app.simple.felicity.viewmodels.panels.MilkdropViewModel]
 * share a single in-memory preset list without rescanning the asset filesystem.
 *
 * @author Hamza417
 */
class MilkdropPresetsViewModel(application: Application) : AndroidViewModel(application) {

    private val _presets = MutableStateFlow<List<MilkdropPreset>>(emptyList())

    /**
     * Sorted flat list of all `.milk` presets found across every subdirectory of
     * [PresetManager.PRESETS_ROOT].  Emits an empty list until the initial scan
     * completes.
     */
    val presets: StateFlow<List<MilkdropPreset>> = _presets.asStateFlow()

    init {
        loadPresets()
    }

    private fun loadPresets() {
        viewModelScope.launch(Dispatchers.IO) {
            _presets.value = PresetManager.listAll(getApplication<Application>().assets)
        }
    }
}
