package app.simple.felicity.viewmodels.panels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.milkdrop.managers.PresetManager
import app.simple.felicity.milkdrop.models.MilkdropPreset
import app.simple.felicity.preferences.MilkdropPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for the [app.simple.felicity.ui.panels.Milkdrop] fragment.
 *
 * Scans all bundled presets via [PresetManager] and exposes:
 * - [presets]          — the full sorted preset list used to populate the ViewPager2.
 * - [currentIndex]     — zero-based index of the currently active preset in [presets].
 * - [presetContent]    — raw `.milk` file text ready to be pushed into projectM.
 * - [isShuffleEnabled] — whether automatic preset cycling is currently active.
 *
 * Call [loadPresetAtIndex] when the user swipes to a new page in the pager.
 * Call [toggleShuffle] to start or stop automatic preset cycling.
 * Call [reloadFromPreferences] if an external component changes [MilkdropPreferences.LAST_PRESET].
 *
 * All disk I/O is dispatched on [Dispatchers.IO] so the main thread is never blocked.
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

    private val _presets = MutableStateFlow<List<MilkdropPreset>>(emptyList())

    /**
     * Full sorted list of all bundled presets.  Emits an empty list until the
     * initial asset scan completes on the IO thread.
     */
    val presets: StateFlow<List<MilkdropPreset>> = _presets.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)

    /**
     * Zero-based index of the currently active preset within [presets].
     * Defaults to 0 until the first scan completes.
     */
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(MilkdropPreferences.isShuffleEnabled())

    /**
     * `true` while the automatic preset shuffle is running, `false` when stopped.
     * Persisted across process restarts via [MilkdropPreferences].
     */
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private var shuffleJob: Job? = null

    init {
        loadPresetsAndCurrentPreset()
        if (MilkdropPreferences.isShuffleEnabled()) {
            startShuffle()
        }
    }

    /**
     * Loads the preset at [index] from the current [presets] list, persists its path
     * to [MilkdropPreferences], and emits updated [presetContent] and [currentIndex].
     *
     * No-op if [index] is out of bounds.
     *
     * @param index Position within the [presets] list selected by the user.
     */
    fun loadPresetAtIndex(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            loadPresetAtIndexInternal(index)
        }
    }

    /**
     * Applies a preset that was picked from the presets dialog.
     *
     * Stops any running auto-shuffle so the manually chosen preset is not immediately
     * overwritten, then loads the preset whose asset path matches [path].
     *
     * @param path Asset-relative path of the selected preset, as stored in
     *             [MilkdropPreset.path].
     */
    fun selectPreset(path: String) {
        if (_isShuffleEnabled.value) {
            _isShuffleEnabled.value = false
            MilkdropPreferences.setShuffleEnabled(false)
            stopShuffle()
        }
        viewModelScope.launch(Dispatchers.IO) {
            val list = _presets.value
            val index = list.indexOfFirst { it.path == path }.coerceAtLeast(0)
            loadPresetAtIndexInternal(index)
        }
    }

    /**
     * Flips the shuffle state: enables automatic preset cycling if it was off, or
     * stops it if it was on.  The new state is persisted to [MilkdropPreferences].
     */
    fun toggleShuffle() {
        val enabled = !_isShuffleEnabled.value
        _isShuffleEnabled.value = enabled
        MilkdropPreferences.setShuffleEnabled(enabled)
        if (enabled) {
            startShuffle()
        } else {
            stopShuffle()
        }
    }

    /**
     * Re-reads the last preset path from [MilkdropPreferences] and reloads content.
     *
     * Useful when an external component (e.g. a dialog) changes the saved path.
     */
    fun reloadFromPreferences() {
        loadPresetsAndCurrentPreset()
    }

    private fun loadPresetsAndCurrentPreset() {
        viewModelScope.launch(Dispatchers.IO) {
            val assets = getApplication<Application>().assets
            val list = PresetManager.listAll(assets)
            _presets.value = list

            val path = MilkdropPreferences.getLastPreset()
                .takeIf { it.isNotBlank() }
                ?: PresetManager.firstPresetPath(assets)?.also {
                    MilkdropPreferences.setLastPreset(it)
                }
                ?: return@launch

            val index = list.indexOfFirst { it.path == path }.coerceAtLeast(0)
            _currentIndex.value = index
            _presetContent.value = PresetManager.loadContent(assets, path)
        }
    }

    /**
     * Loads the preset at [index] directly on the calling coroutine — avoids launching
     * a nested coroutine when called from inside the shuffle loop.
     */
    private suspend fun loadPresetAtIndexInternal(index: Int) {
        val list = _presets.value
        if (index < 0 || index >= list.size) return
        val preset = list[index]
        _currentIndex.value = index
        MilkdropPreferences.setLastPreset(preset.path)
        _presetContent.value = PresetManager.loadContent(
                getApplication<Application>().assets, preset.path
        )
    }

    /**
     * Launches a coroutine that picks a new random preset every [SHUFFLE_INTERVAL_MS]
     * milliseconds until [stopShuffle] is called or the ViewModel is cleared.
     */
    private fun startShuffle() {
        shuffleJob?.cancel()
        shuffleJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(SHUFFLE_INTERVAL_MS)
                val list = _presets.value
                if (list.isEmpty()) continue
                val randomIndex = list.indices.random()
                loadPresetAtIndexInternal(randomIndex)
            }
        }
    }

    /** Cancels any in-flight shuffle coroutine. */
    private fun stopShuffle() {
        shuffleJob?.cancel()
        shuffleJob = null
    }

    override fun onCleared() {
        stopShuffle()
        super.onCleared()
    }

    companion object {
        /** Interval between automatic preset changes while shuffle is active. */
        private const val SHUFFLE_INTERVAL_MS = 15_000L
    }
}
