package app.simple.felicity.viewmodels.main.artists

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.repositories.ArtistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ArtistsViewModel(application: Application) : WrappedViewModel(application) {

    private val artistRepository: ArtistRepository by lazy {
        ArtistRepository(application.applicationContext)
    }

    private val albums: MutableLiveData<List<Artist>> by lazy {
        MutableLiveData<List<Artist>>().also {
            fetchArtists()
        }
    }

    fun getArtists(): LiveData<List<Artist>> {
        return albums
    }

    private fun fetchArtists() {
        viewModelScope.launch(Dispatchers.IO) {
            albums.postValue(artistRepository.fetchArtists())
        }
    }
}
