package app.simple.felicity.viewmodels.ui

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.loaders.MediaStoreLoader.loadAlbums
import app.simple.felicity.models.normal.Album
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlbumsViewModel(application: Application) : WrappedViewModel(application) {

    private val albums: MutableLiveData<ArrayList<Album>> by lazy {
        MutableLiveData<ArrayList<Album>>().also {
            fetchAlbums()
        }
    }

    fun getAlbums(): LiveData<ArrayList<Album>> {
        return albums
    }

    private fun fetchAlbums() {
        viewModelScope.launch(Dispatchers.IO) {
            albums.postValue(applicationContext().loadAlbums())
        }
    }
}
