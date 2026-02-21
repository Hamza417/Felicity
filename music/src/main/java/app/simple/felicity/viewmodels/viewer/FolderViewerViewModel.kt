package app.simple.felicity.viewmodels.viewer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.repository.models.Folder
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

@HiltViewModel(assistedFactory = FolderViewerViewModel.Factory::class)
class FolderViewerViewModel @AssistedInject constructor(
        @Assisted private val folder: Folder,
        private val audioRepository: AudioRepository,
) : ViewModel() {

    private val _data = MutableStateFlow<PageData?>(null)
    val data: StateFlow<PageData?> = _data.asStateFlow()

    init {
        loadFolderData()
    }

    private fun loadFolderData() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()

            audioRepository.getFolderPageData(folder)
                .catch { exception ->
                    Log.e(TAG, "Error loading folder data", exception)
                    emit(PageData())
                }
                .flowOn(Dispatchers.IO)
                .collect { pageData ->
                    val loadTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "loadFolderData: Loaded data for folder: ${folder.name}")
                    Log.d(TAG, "  - Songs:   ${pageData.songs.size}")
                    Log.d(TAG, "  - Albums:  ${pageData.albums.size}")
                    Log.d(TAG, "  - Artists: ${pageData.artists.size}")
                    Log.d(TAG, "  - Genres:  ${pageData.genres.size}")
                    Log.d(TAG, "  - Load time: $loadTime ms")

                    _data.value = pageData
                }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(folder: Folder): FolderViewerViewModel
    }

    companion object {
        private const val TAG = "FolderViewerViewModel"
    }
}

