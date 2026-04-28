package app.simple.felicity.viewmodels.viewer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.PageData
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.sort.PageSort.sortedForAlbumPage
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

@HiltViewModel(assistedFactory = AlbumViewerViewModel.Factory::class)
class AlbumViewerViewModel @AssistedInject constructor(
        @Assisted album: Album,
        private val audioRepository: AudioRepository
) : ViewModel() {

    /**
     * The album we're showing. Song paths are cleared right here because they were only
     * needed for the initial lookup — no point keeping a potentially giant list in memory.
     */
    private val album = album.copy(songPaths = emptyList())

    private val _data = MutableStateFlow<PageData?>(null)
    val data: StateFlow<PageData?> = _data.asStateFlow()

    /** Raw unsorted songs fetched from the repository. Re-sorting does not require a DB trip. */
    private var rawSongs: List<Audio> = emptyList()

    init {
        loadAlbumData()
    }

    private fun loadAlbumData() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()

            audioRepository.getAlbumPageData(album)
                .catch { exception ->
                    Log.e(TAG, "Error loading album data", exception)
                    emit(PageData())
                }
                .flowOn(Dispatchers.IO)
                .collect { pageData ->
                    val loadTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "loadAlbumData: Loaded data for album: ${album.name}")
                    Log.d(TAG, "  - Audios: ${pageData.songs.size}")
                    Log.d(TAG, "  - Artists: ${pageData.artists.size}")
                    Log.d(TAG, "  - Genres: ${pageData.genres.size}")
                    Log.d(TAG, "  - Load time: $loadTime ms")

                    rawSongs = pageData.songs
                    _data.value = pageData.copy(songs = rawSongs.sortedForAlbumPage())
                }
        }
    }

    /**
     * Re-sorts the cached song list using the current [app.simple.felicity.preferences.PagePreferences]
     * and re-emits [PageData] without hitting the database.
     */
    fun resort() {
        val current = _data.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            _data.value = current.copy(songs = rawSongs.sortedForAlbumPage())
            Log.d(TAG, "resort: re-sorted ${rawSongs.size} songs for album page")
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(album: Album): AlbumViewerViewModel
    }

    companion object {
        private const val TAG = "AlbumViewerViewModel"
    }
}