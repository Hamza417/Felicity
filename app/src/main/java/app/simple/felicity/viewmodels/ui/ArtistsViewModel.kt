package app.simple.felicity.viewmodels.ui

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.loaders.MediaStoreLoader.loadArtists
import app.simple.felicity.models.normal.Artist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ArtistsViewModel(application: Application) : WrappedViewModel(application) {

    private val albums: MutableLiveData<ArrayList<Artist>> by lazy {
        MutableLiveData<ArrayList<Artist>>().also {
            fetchArtists()
        }
    }

    fun getArtists(): LiveData<ArrayList<Artist>> {
        return albums
    }

    private fun fetchArtists() {
        viewModelScope.launch(Dispatchers.IO) {
            albums.postValue(applicationContext().loadArtists())
        }
    }
}
