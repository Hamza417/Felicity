package app.simple.felicity.viewmodels.ui

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.loaders.MediaLoader.loadAlbums
import app.simple.felicity.loaders.MediaLoader.loadArtists
import app.simple.felicity.loaders.MediaLoader.loadAudios
import app.simple.felicity.models.home.Home
import app.simple.felicity.models.home.HomeAlbum
import app.simple.felicity.models.home.HomeArtist
import app.simple.felicity.models.home.HomeAudio
import app.simple.felicity.utils.ArrayUtils.toArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : WrappedViewModel(application) {

    private val data: MutableLiveData<ArrayList<Home>> by lazy {
        MutableLiveData<ArrayList<Home>>().also {
            loadData()
        }
    }

    fun getHomeData(): MutableLiveData<ArrayList<Home>> {
        return data
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = applicationContext().loadAudios()
            val albums = applicationContext().loadAlbums()
            val artists = applicationContext().loadArtists()
            val recentlyAdded = applicationContext().loadAudios("date_added DESC").toArrayList()

            val homeData = arrayListOf<Home>()

            homeData.add(HomeAudio(R.string.songs, R.drawable.ic_music, songs))
            homeData.add(HomeAlbum(R.string.albums, R.drawable.ic_album, albums))
            homeData.add(HomeArtist(R.string.artists, R.drawable.ic_artist, artists))
            homeData.add(HomeAudio(R.string.recently_added, R.drawable.ic_history, recentlyAdded))

            data.postValue(homeData)
        }
    }
}
