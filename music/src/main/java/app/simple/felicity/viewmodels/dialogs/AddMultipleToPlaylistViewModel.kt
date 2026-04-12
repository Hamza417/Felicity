package app.simple.felicity.viewmodels.dialogs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.repository.models.Audio
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
 * ViewModel for the AddMultipleToPlaylistDialog.
 *
 * This is the big-brother version of [AddToPlaylistViewModel] — instead of managing
 * playlist membership for a single track, it handles a whole batch of selected songs at once.
 *
 * All playlists are shown without any pre-checked state, since determining which playlists
 * already contain every song in the selection would be complex and potentially confusing.
 * When the user taps "Save", all selected songs are appended to every checked playlist
 * using the efficient batch [PlaylistRepository.addSongs] operation.
 *
 * @author Hamza417
 */
@HiltViewModel(assistedFactory = AddMultipleToPlaylistViewModel.Factory::class)
class AddMultipleToPlaylistViewModel @AssistedInject constructor(
        @Assisted val audios: ArrayList<Audio>,
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
     * Fires exactly once after [saveToPlaylists] has finished writing all changes.
     * The dialog should dismiss itself in response to this event.
     */
    val saveComplete: SharedFlow<Unit> = _saveComplete.asSharedFlow()

    init {
        observePlaylists()
    }

    /**
     * Subscribes to all playlists and maps each emission into a [State].
     * The stream stays alive for the ViewModel's lifetime so any newly created playlist
     * appears in the dialog list automatically without a manual reload.
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
     * Appends all of the songs from [audios] to each playlist in [checkedPlaylistIds].
     * This runs on the IO dispatcher and fires [saveComplete] when done, signaling the
     * dialog to close and the selection to be cleared.
     *
     * @param checkedPlaylistIds The set of playlist IDs the user has checked in the dialog.
     */
    fun saveToPlaylists(checkedPlaylistIds: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            val audioHashes = audios.map { it.hash }
            checkedPlaylistIds.forEach { playlistId ->
                playlistRepository.addSongs(playlistId, audioHashes)
            }
            _saveComplete.emit(Unit)
        }
    }

    /** Factory required by Hilt's assisted-injection mechanism. */
    @AssistedFactory
    interface Factory {
        /**
         * Creates an [AddMultipleToPlaylistViewModel] bound to the given batch of [audios].
         *
         * @param audios The audio tracks whose playlist membership is being set.
         * @return A new [AddMultipleToPlaylistViewModel] instance.
         */
        fun create(audios: ArrayList<Audio>): AddMultipleToPlaylistViewModel
    }
}

