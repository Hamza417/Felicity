package app.simple.felicity.viewmodels.dialogs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.repository.models.Playlist
import app.simple.felicity.repository.repositories.PlaylistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the [app.simple.felicity.dialogs.playlists.AddMultipleToPlaylistDialog].
 *
 * Works with audio hashes instead of full [app.simple.felicity.repository.models.Audio]
 * objects to keep the memory footprint tiny regardless of how many songs the user selected.
 * Since [PlaylistRepository.addSongs] only needs hashes, there's no reason to hold onto
 * the full model objects here.
 *
 * @author Hamza417
 */
@HiltViewModel(assistedFactory = AddMultipleToPlaylistViewModel.Factory::class)
class AddMultipleToPlaylistViewModel @AssistedInject constructor(
        @Assisted val audioHashes: LongArray,
        private val playlistRepository: PlaylistRepository
) : ViewModel() {

    /**
     * Snapshot of the data needed to render the playlist-checkbox list.
     *
     * @param playlists  All available playlists sorted alphabetically.
     * @param songCounts Map of playlist ID to its current song count, for the subtitle label.
     */
    data class State(
            val playlists: List<Playlist>,
            val songCounts: Map<Long, Int>
    )

    private val _state = MutableStateFlow<State?>(null)

    /**
     * Reactive UI state for the dialog. Emits null until the first database load
     * completes, then re-emits whenever the playlists table changes.
     */
    val state: StateFlow<State?> = _state.asStateFlow()

    private val _saveComplete = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Fires exactly once after [saveToPlaylists] finishes writing all changes.
     * The dialog dismisses itself in response to this event.
     */
    val saveComplete: SharedFlow<Unit> = _saveComplete.asSharedFlow()

    init {
        observePlaylists()
    }

    /**
     * Subscribes to all playlists and maps each emission into a [State].
     * Any newly created playlist appears in the dialog list automatically without a reload.
     */
    private fun observePlaylists() {
        viewModelScope.launch {
            playlistRepository.getAllPlaylistsWithSongs().collect { list ->
                val playlists = list.map { it.playlist }
                val songCounts = list.associate { it.playlist.id to it.songs.size }
                _state.emit(State(playlists, songCounts))
            }
        }
    }

    /**
     * Appends all songs (by their hashes) to each checked playlist.
     * Runs on the IO dispatcher and fires [saveComplete] when done.
     *
     * @param checkedPlaylistIds The set of playlist IDs the user confirmed in the dialog.
     */
    fun saveToPlaylists(checkedPlaylistIds: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            val hashes = audioHashes.toList()
            checkedPlaylistIds.forEach { playlistId ->
                playlistRepository.addSongs(playlistId, hashes)
            }
            _saveComplete.emit(Unit)
        }
    }

    @AssistedFactory
    interface Factory {
        /**
         * Creates an [AddMultipleToPlaylistViewModel] bound to the given [audioHashes].
         *
         * @param audioHashes The XXHash64 fingerprints of the songs being batch-added.
         */
        fun create(audioHashes: LongArray): AddMultipleToPlaylistViewModel
    }
}
