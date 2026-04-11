package app.simple.felicity.repository.managers

import app.simple.felicity.repository.models.Audio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A singleton that keeps track of which audio tracks the user has marked for a bulk action.
 *
 * Think of it as the "shopping cart" of the music selection experience — songs go in,
 * come out, and anything watching the cart gets notified the moment it changes.
 * The underlying [StateFlow] makes it trivially easy to react to changes in any
 * coroutine or lifecycle-aware component.
 *
 * This is intentionally kept as a barebone scaffold for now. More sophisticated
 * selection logic (multi-select, range select, etc.) can be added on top later.
 *
 * @author Hamza417
 */
object SelectionManager {

    private val _selectedAudios = MutableStateFlow<List<Audio>>(emptyList())

    /**
     * The live list of currently selected tracks. Observe this from your Activity or
     * ViewModel to react to selection changes across the entire app.
     */
    val selectedAudios: StateFlow<List<Audio>> = _selectedAudios.asStateFlow()

    /** True when at least one song is sitting in the selection basket. */
    val hasSelection: Boolean
        get() = _selectedAudios.value.isNotEmpty()

    /** How many songs are currently selected — handy for displaying counts in the UI. */
    val selectionCount: Int
        get() = _selectedAudios.value.size

    /**
     * Marks [audio] as selected. If it is already in the selection, this is a no-op —
     * no duplicates allowed in our shopping cart.
     *
     * @param audio The track to add to the current selection.
     */
    fun select(audio: Audio) {
        if (_selectedAudios.value.any { it.id == audio.id }) return
        _selectedAudios.value += audio
    }

    /**
     * Removes [audio] from the selection. Safe to call even if the track was never selected.
     *
     * @param audio The track to remove.
     */
    fun deselect(audio: Audio) {
        _selectedAudios.value = _selectedAudios.value.filter { it.id != audio.id }
    }

    /**
     * Toggles the selection state of [audio] — adds it if not selected, removes it if selected.
     * Great for checkbox-style interaction where a single tap flips the state.
     *
     * @param audio The track whose selection state should be flipped.
     */
    fun toggle(audio: Audio) {
        val current = _selectedAudios.value.toMutableList()
        if (current.any { it.id == audio.id }) {
            current.removeAll { it.id == audio.id }
        } else {
            current.add(audio)
        }
        _selectedAudios.value = current
    }

    /**
     * Empties the entire selection in one swoop — like putting every item back on the shelf.
     * Call this after a bulk action (delete, share, etc.) is completed or canceled.
     */
    fun clear() {
        _selectedAudios.value = emptyList()
    }

    /**
     * Returns whether [audio] is currently part of the selection.
     *
     * @param audio The track to check.
     * @return `true` if the track is selected, `false` otherwise.
     */
    fun isSelected(audio: Audio): Boolean {
        return _selectedAudios.value.any { it.id == audio.id }
    }
}