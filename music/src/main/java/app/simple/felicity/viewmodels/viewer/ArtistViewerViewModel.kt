package app.simple.felicity.viewmodels.viewer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.PageData
import app.simple.felicity.repository.repositories.AudioRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ArtistViewerViewModel.Factory::class)
class ArtistViewerViewModel @AssistedInject constructor(
        @Assisted private val artist: Artist,
        private val audioRepository: AudioRepository,
        application: Application) : WrappedViewModel(application) {

    private val data: MutableLiveData<PageData> by lazy {
        MutableLiveData<PageData>().also {
            loadArtistSongs()
        }
    }

    private val imageUris: MutableLiveData<List<Uri>> by lazy {
        MutableLiveData<List<Uri>>()
    }

    fun getData(): LiveData<PageData> {
        return data
    }

    fun getImageUris(): LiveData<List<Uri>> {
        return imageUris
    }

    private fun loadArtistSongs() {
        viewModelScope.launch(Dispatchers.IO) {

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