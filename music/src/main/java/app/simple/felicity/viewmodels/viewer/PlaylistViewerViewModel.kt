package app.simple.felicity.viewmodels.viewer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.PageData
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
 * ViewModel that builds and exposes [PageData] for a single [Playlist].
 *
 * Combines two reactive streams: one for the playlist's own metadata row (which carries
 * the per-playlist sort preference) and one for the ordered song list. Whenever either
 * stream emits — a song is added/removed, or the user picks a new sort via the sort
 * dialog — the songs are re-sorted and pushed to [data] automatically.
 *
 * Sort order is read from [Playlist.sortOrder] and [Playlist.sortStyle] stored in the DB,
 * so each playlist remembers its own sort independently. A [sortOrder] of {@code -1} means
 * "As Added", which preserves the position-based insertion order from the database.
 *
 * @author Hamza417
 */
@HiltViewModel(assistedFactory = PlaylistViewerViewModel.Factory::class)
class PlaylistViewerViewModel @AssistedInject constructor(
        @Assisted val playlist: Playlist,
        private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _data = MutableStateFlow<PageData?>(null)

    /** Reactive [PageData] for the playlist page, re-emitted on song or sort changes. */
    val data: StateFlow<PageData?> = _data.asStateFlow()

    private val _currentPlaylist = MutableStateFlow(playlist)

    /** Reflects the latest playlist metadata from the DB, including any sort preference changes. */
    val currentPlaylist: StateFlow<Playlist> = _currentPlaylist.asStateFlow()

    init {
        loadPlaylistData()
    }

    private fun loadPlaylistData() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()

            combine(
                    playlistRepository.getPlaylistByIdFlow(playlist.id),
                    playlistRepository.getPlaylistPageData(playlist)
            ) { updatedPlaylist, pageData ->
                Pair(updatedPlaylist ?: playlist, pageData)
            }
                .catch { e -> Log.e(TAG, "Error loading playlist page data", e) }
                .flowOn(Dispatchers.IO)
                .collect { (currentPlaylist, pageData) ->
                    val loadTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "loadPlaylistData: '${currentPlaylist.name}', sort=${currentPlaylist.sortOrder}")
                    Log.d(TAG, "  - Songs: ${pageData.songs.size}, load time: $loadTime ms")

                    _currentPlaylist.value = currentPlaylist
                    _data.value = pageData.copy(songs = pageData.songs.sortedByPlaylist(currentPlaylist))
                }
        }
    }

    /**
     * Sorts the raw song list according to [playlist]'s stored sort preference.
     * When [Playlist.sortOrder] is {@code -1} the songs stay in their "As Added"
     * position order exactly as they came from the database.
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

    /**
     * No-op kept for API compatibility. Sort changes are now driven automatically by
     * the DB flow, so an explicit resort call is no longer necessary.
     */
    fun resort() = Unit

    /** Factory interface required by the Hilt assisted-injection mechanism. */
    @AssistedFactory
    interface Factory {
        /**
         * Creates a [PlaylistViewerViewModel] scoped to the given [playlist].
         *
         * @param playlist The playlist whose page data will be loaded.
         */
        fun create(playlist: Playlist): PlaylistViewerViewModel
    }

    companion object {
        private const val TAG = "PlaylistViewerViewModel"
    }
}
