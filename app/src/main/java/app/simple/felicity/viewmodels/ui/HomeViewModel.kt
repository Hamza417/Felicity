package app.simple.felicity.viewmodels.ui

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.loaders.MediaLoader
import app.simple.felicity.models.HomeItem
import app.simple.felicity.utils.ArrayUtils.toArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : WrappedViewModel(application) {

    private val data: MutableLiveData<ArrayList<HomeItem>> by lazy {
        MutableLiveData<ArrayList<HomeItem>>().also {
            loadData()
        }
    }

    fun getHomeData(): MutableLiveData<ArrayList<HomeItem>> {
        return data
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = MediaLoader.getSongs(applicationContext())!!.toArrayList()
            val albums = MediaLoader.getAlbums(applicationContext())!!.toArrayList()
            val artists = MediaLoader.getArtists(applicationContext())!!.toArrayList()
            val recentlyAdded = MediaLoader.getRecentlyAdded(applicationContext())!!.toArrayList()

            val homeData = arrayListOf<HomeItem>()

            homeData.add(HomeItem(R.string.songs, R.drawable.ic_music, songs))
            homeData.add(HomeItem(R.string.albums, R.drawable.ic_album, albums))
            homeData.add(HomeItem(R.string.artists, R.drawable.ic_artist, artists))
            homeData.add(HomeItem(R.string.recently_added, R.drawable.ic_history, recentlyAdded))

            data.postValue(homeData)
        }
    }
}
