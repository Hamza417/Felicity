package app.simple.felicity.viewmodels.viewer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = GenreViewerViewModel.Factory::class)
class GenreViewerViewModel @AssistedInject constructor(
        @Assisted val genre: Genre,
        private val audioRepository: AudioRepository,
) : ViewModel() {

    private val data: MutableLiveData<PageData> by lazy {
        MutableLiveData<PageData>().also {
            loadGenreSongs()
        }
    }

    fun getData(): LiveData<PageData> {
        return data
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadGenreSongs() {
        viewModelScope.launch(Dispatchers.IO) {

        }
    }

    @AssistedFactory
    interface Factory {
        fun create(genre: Genre): GenreViewerViewModel
    }

    companion object {

    }
}