package app.simple.felicity.viewmodels.main.albums

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.AlbumPreferences
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.repositories.AlbumRepository
import app.simple.felicity.repository.sort.AlbumSort.sorted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlbumsViewModel(application: Application) : WrappedViewModel(application) {

    private val albumRepository: AlbumRepository by lazy {
        AlbumRepository(application.applicationContext)
    }

    private val albums: MutableLiveData<List<Album>> by lazy {
        MutableLiveData<List<Album>>().also {
            fetchAlbums()
        }
    }

    fun getAlbums(): LiveData<List<Album>> {
        return albums
    }

    private fun fetchAlbums() {
        viewModelScope.launch(Dispatchers.IO) {
            albums.postValue(albumRepository.fetchAlbums().sorted())
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            AlbumPreferences.ALBUM_SORT, AlbumPreferences.SORTING_STYLE -> {
                fetchAlbums()
            }
        }
    }
}
