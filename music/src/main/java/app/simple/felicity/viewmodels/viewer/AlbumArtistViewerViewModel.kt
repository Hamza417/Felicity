package app.simple.felicity.viewmodels.viewer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.PageData
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.sort.PageSort.sortedForArtistPage
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
 * Drives the Album Artist page — fetches all songs, albums, and genres for a single album
 * artist and exposes them as a [PageData] state. Re-sorting is done in memory so we
 * skip unnecessary database round-trips (speedy!).
 *
 * @author Hamza417
 */
@HiltViewModel(assistedFactory = AlbumArtistViewerViewModel.Factory::class)
class AlbumArtistViewerViewModel @AssistedInject constructor(
        @Assisted albumArtist: Artist,
        private val audioRepository: AudioRepository
) : ViewModel() {

    private val _data = MutableStateFlow<PageData?>(null)
    val data: StateFlow<PageData?> = _data.asStateFlow()

    /**
     * The album artist we're showing. Song paths are cleared right here because they were only
     * needed for the initial lookup — no point keeping a potentially giant list in memory.
     */
    private val albumArtist = albumArtist.copy(songPaths = emptyList())

    /** The raw, unsorted song list — cached so re-sorting never touches the database. */
    private var rawSongs: List<Audio> = emptyList()

    init {
        loadAlbumArtistData()
    }

    private fun loadAlbumArtistData() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()

            audioRepository.getAlbumArtistPageData(albumArtist)
                .catch { exception ->
                    Log.e(TAG, "Error loading album artist data", exception)
                    emit(PageData())
                }
                .flowOn(Dispatchers.IO)
                .collect { pageData ->
                    val loadTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "loadAlbumArtistData: Loaded data for album artist: ${albumArtist.name}")
                    Log.d(TAG, "  - Songs:  ${pageData.songs.size}")
                    Log.d(TAG, "  - Albums: ${pageData.albums.size}")
                    Log.d(TAG, "  - Genres: ${pageData.genres.size}")
                    Log.d(TAG, "  - Load time: $loadTime ms")

                    rawSongs = pageData.songs
                    _data.value = pageData.copy(songs = rawSongs.sortedForArtistPage())
                }
        }
    }

    /**
     * Re-sorts the cached song list using the current page preferences and publishes
     * the updated [PageData] without hitting the database. Think of it as a free refresh.
     */
    fun resort() {
        val current = _data.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            _data.value = current.copy(songs = rawSongs.sortedForArtistPage())
            Log.d(TAG, "resort: re-sorted ${rawSongs.size} songs for album artist page")
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(albumArtist: Artist): AlbumArtistViewerViewModel
    }

    companion object {
        private const val TAG = "AlbumArtistViewerViewModel"
    }
}

