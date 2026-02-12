package app.simple.felicity.viewmodels.viewer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.repository.models.Genre
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

@HiltViewModel(assistedFactory = GenreViewerViewModel.Factory::class)
class GenreViewerViewModel @AssistedInject constructor(
        @Assisted private val genre: Genre,
        private val audioRepository: AudioRepository,
) : ViewModel() {

    private val _data = MutableStateFlow<PageData?>(null)
    val data: StateFlow<PageData?> = _data.asStateFlow()

    init {
        loadGenreData()
    }

    private fun loadGenreData() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()

            audioRepository.getGenrePageData(genre)
                .catch { exception ->
                    Log.e(TAG, "Error loading genre data", exception)
                    emit(PageData())
                }
                .flowOn(Dispatchers.IO)
                .collect { pageData ->
                    val loadTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "loadGenreData: Loaded data for genre: ${genre.name}")
                    Log.d(TAG, "  - Audios: ${pageData.songs.size}")
                    Log.d(TAG, "  - Albums: ${pageData.albums.size}")
                    Log.d(TAG, "  - Artists: ${pageData.artists.size}")
                    Log.d(TAG, "  - Load time: $loadTime ms")

                    _data.value = pageData
                }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(genre: Genre): GenreViewerViewModel
    }

    companion object {
        private const val TAG = "GenreViewerViewModel"
    }
}