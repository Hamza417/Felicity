package app.simple.felicity.viewmodels.dialogs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.milkdrop.models.MilkdropPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the [app.simple.felicity.dialogs.player.MilkdropPresets] dialog.
 *
 * Scans the `presets/points/` asset directory for `.milk` files on a background
 * dispatcher and exposes the sorted result as a [StateFlow] so the dialog's
 * [RecyclerView][androidx.recyclerview.widget.RecyclerView] can react to the list
 * the moment it is ready.
 *
 * @author Hamza417
 */
class MilkdropPresetsViewModel(application: Application) : AndroidViewModel(application) {

    private val _presets = MutableStateFlow<List<MilkdropPreset>>(emptyList())

    /**
     * Sorted list of all `.milk` preset files found under `assets/presets/points/`.
     * Emits an empty list until the initial scan completes.
     */
    val presets: StateFlow<List<MilkdropPreset>> = _presets.asStateFlow()

    init {
        loadPresets()
    }

    private fun loadPresets() {
        viewModelScope.launch(Dispatchers.IO) {
            val assets = getApplication<Application>().assets
            val files = assets.list(PRESET_DIR) ?: emptyArray()
            _presets.value = files
                .filter { it.endsWith(".milk") }
                .sorted()
                .map { filename ->
                    MilkdropPreset(
                            path = "$PRESET_DIR/$filename",
                            name = filename.removeSuffix(".milk")
                    )
                }
        }
    }

    private companion object {
        private const val PRESET_DIR = "presets/points"
    }
}

