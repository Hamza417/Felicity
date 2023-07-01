package app.simple.felicity.viewmodels.ui

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.loaders.MediaLoader
import app.simple.felicity.models.Audio
import app.simple.felicity.utils.ArrayUtils.toArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : WrappedViewModel(application) {

    private val data: MutableLiveData<ArrayList<Pair<Int, ArrayList<Audio>>>> by lazy {
        MutableLiveData<ArrayList<Pair<Int, ArrayList<Audio>>>>().also {
            loadData()
        }
    }

    fun getHomeData(): MutableLiveData<ArrayList<Pair<Int, ArrayList<Audio>>>> {
        return data
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = MediaLoader.getSongs(applicationContext())!!.toArrayList()
            val albums = MediaLoader.getAlbums(applicationContext())!!.toArrayList()
            val artists = MediaLoader.getArtists(applicationContext())!!.toArrayList()
            val recentlyAdded = MediaLoader.getRecentlyAdded(applicationContext())!!.toArrayList()

            val homeData = arrayListOf<Pair<Int, ArrayList<Audio>>>()

            homeData.add(Pair(R.string.songs, songs))
            homeData.add(Pair(R.string.albums, albums))
            homeData.add(Pair(R.string.artists, artists))
            homeData.add(Pair(R.string.recently_added, recentlyAdded))

            data.postValue(homeData)
        }
    }
}