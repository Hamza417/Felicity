package app.simple.felicity.viewmodels.main.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.normal.Audio
import app.simple.felicity.repository.repositories.AudioRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : WrappedViewModel(application) {

    private val audioRepository: AudioRepository by lazy {
        AudioRepository(AudioDatabase.getInstance(applicationContext())?.audioDao()!!)
    }

    private val _songs = MutableSharedFlow<MutableList<Audio>>(replay = 1)
    private val _artists = MutableSharedFlow<MutableList<Audio>>(replay = 1)
    private val _albums = MutableSharedFlow<MutableList<Audio>>(replay = 1)
    private val _recentlyAdded = MutableSharedFlow<MutableList<Audio>>(replay = 1)

    val songs: SharedFlow<MutableList<Audio>> = _songs.asSharedFlow()
    val artists: SharedFlow<MutableList<Audio>> = _artists.asSharedFlow()
    val albums: SharedFlow<MutableList<Audio>> = _albums.asSharedFlow()
    val recentlyAdded: SharedFlow<MutableList<Audio>> = _recentlyAdded.asSharedFlow()

    fun getHomeData(): SharedFlow<MutableList<Audio>> {
        return _songs.asSharedFlow()
    }

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            audioRepository.getAllAudio().collect {
                Log.d(TAG, "loadData: ${it.size} songs loaded")
                _songs.emit(it.take(TAKE_COUNT).toMutableList())
            }

            audioRepository.getAllArtists().collect {
                Log.d(TAG, "loadData: ${it.size} artists loaded")
                _artists.emit(it.take(TAKE_COUNT).toMutableList())
            }

            audioRepository.getAllAlbums().collect {
                Log.d(TAG, "loadData: ${it.size} albums loaded")
                _albums.emit(it.take(TAKE_COUNT).toMutableList())
            }

            audioRepository.getRecentAudio().collect {
                Log.d(TAG, "loadData: ${it.size} recently added songs loaded")
                _recentlyAdded.emit(it.take(TAKE_COUNT).toMutableList())
            }

            //            val homeData = arrayListOf<Home>()
            //
            //            homeData.add(HomeAudio(R.string.songs, R.drawable.ic_music, _songs))
            //            homeData.add(HomeAudio(R.string.albums, R.drawable.ic_album, _albums))
            //            homeData.add(HomeAudio(R.string.artists, R.drawable.ic_artist, _artists))
            //            homeData.add(HomeAudio(R.string.recently_added, R.drawable.ic_history, recentlyAdded))
            //
            //            // homeData.add(HomeAudio(R.string.folders, R.drawable.ic_folder, arrayListOf()))
            //            // homeData.add(HomeAudio(R.string.favorites, R.drawable.ic_favorite, arrayListOf()))
            //            // homeData.add(HomeLinks(R.string.links, R.drawable.ic_link, 0))
            //            // homeData.add(HomeBookmarks(R.string.bookmarks, R.drawable.ic_bookmarks, 0))
            //
            //            data.postValue(homeData)
        }
    }

    companion object {
        const val TAG = "HomeViewModel"
        private const val TAKE_COUNT = 18
    }
}
