package app.simple.felicity.viewmodels.main.home

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.home.Home
import app.simple.felicity.repository.models.home.HomeAudio
import app.simple.felicity.repository.models.normal.Audio
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
            val audioDatabase = AudioDatabase.getInstance(applicationContext())
            val songs = audioDatabase?.audioDao()?.getAllAudio()?.take(TAKE_COUNT) as ArrayList<Audio>
            val albums = audioDatabase.audioDao()?.getAllAlbums()?.take(TAKE_COUNT) as ArrayList<Audio>
            val artists = audioDatabase.audioDao()?.getAllArtists()?.take(TAKE_COUNT) as ArrayList<Audio>
            val recentlyAdded = audioDatabase.audioDao()?.getRecentAudio()?.take(TAKE_COUNT) as ArrayList<Audio>

            val homeData = arrayListOf<Home>()

            homeData.add(HomeAudio(R.string.songs, R.drawable.ic_music, songs))
            homeData.add(HomeAudio(R.string.albums, R.drawable.ic_album, albums))
            homeData.add(HomeAudio(R.string.artists, R.drawable.ic_artist, artists))
            homeData.add(HomeAudio(R.string.recently_added, R.drawable.ic_history, recentlyAdded))

            // homeData.add(HomeAudio(R.string.folders, R.drawable.ic_folder, arrayListOf()))
            // homeData.add(HomeAudio(R.string.favorites, R.drawable.ic_favorite, arrayListOf()))
            // homeData.add(HomeLinks(R.string.links, R.drawable.ic_link, 0))
            // homeData.add(HomeBookmarks(R.string.bookmarks, R.drawable.ic_bookmarks, 0))

            data.postValue(homeData)
        }
    }

    companion object {
        const val TAG = "HomeViewModel"
        private const val TAKE_COUNT = 18
    }
}
