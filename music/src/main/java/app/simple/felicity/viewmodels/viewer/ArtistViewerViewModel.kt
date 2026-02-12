package app.simple.felicity.viewmodels.viewer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.PageData
import app.simple.felicity.repository.repositories.AudioRepository
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

@HiltViewModel(assistedFactory = ArtistViewerViewModel.Factory::class)
class ArtistViewerViewModel @AssistedInject constructor(
        @Assisted private val artist: Artist,
        private val audioRepository: AudioRepository
) : ViewModel() {

    private val _data = MutableStateFlow<PageData?>(null)
    val data: StateFlow<PageData?> = _data.asStateFlow()

    init {
        loadArtistData()
    }

    private fun loadArtistData() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()

            audioRepository.getArtistPageData(artist)
                .catch { exception ->
                    Log.e(TAG, "Error loading artist data", exception)
                    emit(PageData())
                }
                .flowOn(Dispatchers.IO)
                .collect { pageData ->
                    val loadTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "loadArtistData: Loaded data for artist: ${artist.name}")
                    Log.d(TAG, "  - Audios: ${pageData.songs.size}")
                    Log.d(TAG, "  - Albums: ${pageData.albums.size}")
                    Log.d(TAG, "  - Genres: ${pageData.genres.size}")
                    Log.d(TAG, "  - Load time: $loadTime ms")

                    _data.value = pageData
                }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(artist: Artist): ArtistViewerViewModel
    }

    companion object {
        private const val TAG = "ArtistViewerViewModel"
    }
}