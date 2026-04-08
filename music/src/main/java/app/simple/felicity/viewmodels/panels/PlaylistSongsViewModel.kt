package app.simple.felicity.viewmodels.panels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Playlist
import app.simple.felicity.repository.repositories.PlaylistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * ViewModel for the PlaylistSongs panel.
 *
 * <p>Combines a reactive stream of the playlist's own metadata (to pick up sort-order
 * changes saved to the DB row) with a reactive stream of its ordered song list.
 * Whenever either stream emits — a song is added or removed, or the user saves a new
 * sort preference via the sort dialog — the two values are merged and the songs are
 * re-sorted in memory before being exposed through [songs].</p>
 *
 * <p>Sort order is stored per-playlist in [Playlist.sortOrder] / [Playlist.sortStyle].
 * A value of {@code -1} for [Playlist.sortOrder] signals "manual / position order",
 * which preserves the drag-and-drop sequence from the cross-ref table.</p>
 *
 * @author Hamza417
 */
@HiltViewModel(assistedFactory = PlaylistSongsViewModel.Factory::class)
class PlaylistSongsViewModel @AssistedInject constructor(
        @Assisted val initialPlaylist: Playlist,
        private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Audio>>(emptyList())

    /** Reactive sorted song list for this playlist. */
    val songs: StateFlow<List<Audio>> = _songs.asStateFlow()

    private val _currentPlaylist = MutableStateFlow(initialPlaylist)

    /** Reactive playlist metadata — reflects sort-order changes saved to the DB. */
    val currentPlaylist: StateFlow<Playlist> = _currentPlaylist.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                    playlistRepository.getPlaylistByIdFlow(initialPlaylist.id),
                    playlistRepository.getSongsInPlaylistOrdered(initialPlaylist.id)
            ) { updatedPlaylist, rawSongs ->
                Pair(updatedPlaylist ?: initialPlaylist, rawSongs)
            }
                .catch { e -> Log.e(TAG, "Error loading playlist songs", e) }
                .flowOn(Dispatchers.IO)
                .collect { (playlist, rawSongs) ->
                    _currentPlaylist.value = playlist
                    _songs.value = rawSongs.sortedByPlaylist(playlist)
                    Log.d(TAG, "Loaded ${rawSongs.size} songs for '${playlist.name}'")
                }
        }
    }

    /**
     * Sorts the raw song list according to the per-playlist [Playlist.sortOrder] and
     * [Playlist.sortStyle] values. Returns the list unchanged when [Playlist.sortOrder]
     * is {@code -1} (manual position order from the cross-ref table).
     */
    private fun List<Audio>.sortedByPlaylist(playlist: Playlist): List<Audio> {
        if (playlist.sortOrder == -1) return this
        val asc = playlist.sortStyle == CommonPreferencesConstants.ASCENDING
        return when (playlist.sortOrder) {
            CommonPreferencesConstants.BY_TITLE ->
                if (asc) sortedBy { it.title?.lowercase() } else sortedByDescending { it.title?.lowercase() }
            CommonPreferencesConstants.BY_ARTIST ->
                if (asc) sortedBy { it.artist?.lowercase() } else sortedByDescending { it.artist?.lowercase() }
            CommonPreferencesConstants.BY_ALBUM ->
                if (asc) sortedBy { it.album?.lowercase() } else sortedByDescending { it.album?.lowercase() }
            CommonPreferencesConstants.BY_DURATION ->
                if (asc) sortedBy { it.duration } else sortedByDescending { it.duration }
            CommonPreferencesConstants.BY_DATE_ADDED ->
                if (asc) sortedBy { it.dateAdded } else sortedByDescending { it.dateAdded }
            else -> this
        }
    }

    /** Factory interface required by the Hilt assisted-injection mechanism. */
    @AssistedFactory
    interface Factory {
        fun create(initialPlaylist: Playlist): PlaylistSongsViewModel
    }

    companion object {
        private const val TAG = "PlaylistSongsViewModel"
    }
}

