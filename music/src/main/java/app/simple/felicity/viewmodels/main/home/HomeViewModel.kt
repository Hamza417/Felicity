package app.simple.felicity.viewmodels.main.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.home.HomeAudio
import app.simple.felicity.repository.models.normal.Audio
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.utils.ArrayUtils.toArrayList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class HomeViewModel(application: Application) : WrappedViewModel(application) {

    private val audioRepository: AudioRepository by lazy {
        AudioRepository(AudioDatabase.getInstance(applicationContext())?.audioDao()!!)
    }

    private val _songs = MutableSharedFlow<HomeAudio>(replay = 1)
    private val _artists = MutableSharedFlow<HomeAudio>(replay = 1)
    private val _albums = MutableSharedFlow<HomeAudio>(replay = 1)
    private val _recentlyAdded = MutableSharedFlow<HomeAudio>(replay = 1)

    val songs: SharedFlow<HomeAudio> = _songs.asSharedFlow()
    val artists: SharedFlow<HomeAudio> = _artists.asSharedFlow()
    val albums: SharedFlow<HomeAudio> = _albums.asSharedFlow()
    val recentlyAdded: SharedFlow<HomeAudio> = _recentlyAdded.asSharedFlow()

    init {
        loadData()
    }

    private fun loadData() {
        audioRepository.getAllAudio()
            .onEach {
                Log.d(TAG, "loadData: ${it.size} songs loaded")
                try {
                    _songs.emit(HomeAudio(R.string.songs, R.drawable.ic_music, it.take(TAKE_COUNT).random() as ArrayList<Audio?>?))
                } catch (e: ClassCastException) {
                    Log.e(TAG, "loadData: Error casting songs to ArrayList<Audio?>", e)
                    _songs.emit(HomeAudio(R.string.songs, R.drawable.ic_music, it.toArrayList()))
                }
            }
            .launchIn(viewModelScope)

        audioRepository.getAllArtists()
            .onEach {
                Log.d(TAG, "loadData: ${it.size} artists loaded")
                try {
                    _artists.emit(HomeAudio(R.string.artists, R.drawable.ic_artist, it.take(TAKE_COUNT).random() as ArrayList<Audio?>?))
                } catch (e: ClassCastException) {
                    Log.e(TAG, "loadData: Error casting artists to ArrayList<Audio?>", e)
                    _artists.emit(HomeAudio(R.string.artists, R.drawable.ic_artist, it.toArrayList()))
                }
            }
            .launchIn(viewModelScope)

        audioRepository.getAllAlbums()
            .onEach {
                Log.d(TAG, "loadData: ${it.size} albums loaded")
                try {
                    _albums.emit(HomeAudio(R.string.albums, R.drawable.ic_album, it.take(TAKE_COUNT).random() as ArrayList<Audio?>?))
                } catch (e: ClassCastException) {
                    Log.e(TAG, "loadData: Error casting albums to ArrayList<Audio?>", e)
                    _albums.emit(HomeAudio(R.string.albums, R.drawable.ic_album, it.toArrayList()))
                }
            }
            .launchIn(viewModelScope)

        audioRepository.getRecentAudio()
            .onEach {
                Log.d(TAG, "loadData: ${it.size} recently added songs loaded")
                try {
                    _recentlyAdded.emit(HomeAudio(R.string.recently_added, R.drawable.ic_history, it.take(TAKE_COUNT).random() as ArrayList<Audio?>?))
                } catch (e: ClassCastException) {

                }
            }
            .launchIn(viewModelScope)
    }

    companion object {
        const val TAG = "HomeViewModel"
        private const val TAKE_COUNT = 18
    }
}
