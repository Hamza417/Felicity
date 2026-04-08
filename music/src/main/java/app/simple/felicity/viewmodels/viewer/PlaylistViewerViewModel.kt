package app.simple.felicity.viewmodels.viewer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * ViewModel that builds and exposes [PageData] for a single [Playlist].
 *
 * <p>Subscribes to [PlaylistRepository.getPlaylistPageData] which reactively re-emits
 * whenever the playlist's song membership changes. Songs, albums, artists, and genres
 * are all derived in a single stream, keeping the page in sync with the underlying data.</p>
 *
 * @author Hamza417
 */
@HiltViewModel(assistedFactory = PlaylistViewerViewModel.Factory::class)
class PlaylistViewerViewModel @AssistedInject constructor(
        @Assisted val playlist: Playlist,
        private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _data = MutableStateFlow<PageData?>(null)

    /** Reactive [PageData] for the playlist page. */
    val data: StateFlow<PageData?> = _data.asStateFlow()

    init {
        loadPlaylistData()
    }

    private fun loadPlaylistData() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()

            playlistRepository.getPlaylistPageData(playlist)
                .catch { exception ->
                    Log.e(TAG, "Error loading playlist page data", exception)
                    emit(PageData())
                }
                .flowOn(Dispatchers.IO)
                .collect { pageData ->
                    val loadTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "loadPlaylistData: Loaded data for playlist: '${playlist.name}'")
                    Log.d(TAG, "  - Songs: ${pageData.songs.size}")
                    Log.d(TAG, "  - Albums: ${pageData.albums.size}")
                    Log.d(TAG, "  - Artists: ${pageData.artists.size}")
                    Log.d(TAG, "  - Genres: ${pageData.genres.size}")
                    Log.d(TAG, "  - Load time: $loadTime ms")

                    _data.value = pageData
                }
        }
    }

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

