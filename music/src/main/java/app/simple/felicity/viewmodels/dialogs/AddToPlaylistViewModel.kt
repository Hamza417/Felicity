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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel for the [app.simple.felicity.dialogs.playlists.AddToPlaylistDialog].
 *
 * <p>Observes all playlists together with their songs via a single reactive
 * [PlaylistRepository.getAllPlaylistsWithSongs] stream. Because that query carries a
 * [androidx.room.Transaction] annotation, Room re-emits whenever either the
 * {@code playlists} or the {@code playlist_song_cross_ref} table changes — so playlist
 * additions, song insertions, and song removals all surface automatically without extra
 * polling. Pre-checked IDs and per-playlist song counts are derived in-memory from the
 * emitted list, avoiding additional per-playlist round-trips.</p>
 *
 * @author Hamza417
 */
@HiltViewModel(assistedFactory = AddToPlaylistViewModel.Factory::class)
class AddToPlaylistViewModel @AssistedInject constructor(
        @Assisted val audio: Audio,
        private val playlistRepository: PlaylistRepository
) : ViewModel() {

    /**
     * Immutable snapshot of the data required to render the playlist-checkbox list.
     *
     * @param playlists     All available playlists ordered alphabetically.
     * @param preCheckedIds Set of playlist IDs that already contain the target audio.
     * @param songCounts    Map of playlist ID to its current song count.
     */
    data class PlaylistDialogState(
            val playlists: List<Playlist>,
            val preCheckedIds: Set<Long>,
            val songCounts: Map<Long, Int>
    )

    private val _state = MutableStateFlow<PlaylistDialogState?>(null)

    /**
     * Reactive UI state for the dialog. Emits {@code null} until the first database load
     * completes, then re-emits on every playlist or cross-ref table change.
     */
    val state: StateFlow<PlaylistDialogState?> = _state.asStateFlow()

    private val _saveComplete = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Emits [Unit] exactly once after [saveMembership] has finished persisting all changes.
     * The dialog should dismiss itself in response to this event.
     */
    val saveComplete: SharedFlow<Unit> = _saveComplete.asSharedFlow()

    init {
        observePlaylists()
    }

    /**
     * Subscribes to [PlaylistRepository.getAllPlaylistsWithSongs] and maps each emission
     * into a [PlaylistDialogState]. The pre-checked IDs and song counts are derived in-memory
     * from the returned [app.simple.felicity.repository.models.PlaylistWithSongs] projections.
     */
    private fun observePlaylists() {
        viewModelScope.launch {
            val audioHash = audio.hash
            playlistRepository.getAllPlaylistsWithSongs()
                .collect { playlistsWithSongs ->
                    val playlists = playlistsWithSongs.map { it.playlist }
                    val preCheckedIds = playlistsWithSongs
                        .filter { pws -> pws.songs.any { song -> song.hash == audioHash } }
                        .map { it.playlist.id }
                        .toSet()
                    val songCounts = playlistsWithSongs.associate { it.playlist.id to it.songs.size }
                    _state.emit(PlaylistDialogState(playlists, preCheckedIds, songCounts))
                }
        }
    }

    /**
     * Persists the updated playlist membership by diffing [checkedIds] against the current
     * database state. Songs are added to newly checked playlists and removed from unchecked
     * ones. Emits [saveComplete] on the IO dispatcher after all changes have been committed.
     *
     * @param checkedIds The set of playlist IDs the user has checked in the dialog.
     */
    fun saveMembership(checkedIds: Set<Long>) {
        val audioHash = audio.hash
        viewModelScope.launch(Dispatchers.IO) {
            val allPlaylists = playlistRepository.getAllPlaylists().first()
            allPlaylists.forEach { playlist ->
                val wasIn = playlistRepository.isSongInPlaylist(playlist.id, audioHash)
                val isNowIn = checkedIds.contains(playlist.id)
                when {
                    !wasIn && isNowIn -> playlistRepository.addSong(playlist.id, audioHash)
                    wasIn && !isNowIn -> playlistRepository.removeSong(playlist.id, audioHash)
                }
            }
            _saveComplete.emit(Unit)
        }
    }

    /** Factory interface required by the Hilt assisted-injection mechanism. */
    @AssistedFactory
    interface Factory {
        /**
         * Creates an [AddToPlaylistViewModel] bound to the given [audio] track.
         *
         * @param audio The audio track whose playlist membership is being edited.
         * @return A new [AddToPlaylistViewModel] instance.
         */
        fun create(audio: Audio): AddToPlaylistViewModel
    }

    companion object {
        @Suppress("unused")
        private const val TAG = "AddToPlaylistViewModel"
    }
}

