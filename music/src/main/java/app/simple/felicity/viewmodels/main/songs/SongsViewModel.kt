package app.simple.felicity.viewmodels.main.songs

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.SongsPreferences
import app.simple.felicity.repository.models.Song
import app.simple.felicity.repository.repositories.SongRepository
import app.simple.felicity.repository.sort.SongSort.sort
import app.simple.felicity.repository.sort.SongSort.sorted
import app.simple.felicity.repository.utils.SongUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SongsViewModel @Inject constructor(
        application: Application,
        private val songRepository: SongRepository) : WrappedViewModel(application) {

    private var carouselPosition = 0

    private val songs: MutableLiveData<List<Song>> by lazy {
        MutableLiveData<List<Song>>().also {
            loadData()
        }
    }

    private val songAndArt: MutableLiveData<Map<Uri, Song>> by lazy {
        MutableLiveData<Map<Uri, Song>>().also {
            loadSongAndArt()
        }
    }

    fun getSongs(): LiveData<List<Song>> {
        return songs
    }

    fun getSongAndArt(): LiveData<Map<Uri, Song>> {
        return songAndArt
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val songsList = songRepository.fetchSongs().sorted()
            songs.postValue(songsList)
            Log.d(TAG, "loadData: ${songsList.size} songs loaded")
        }
    }

    private fun loadSongAndArt() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = songRepository.fetchSongs().sort()
            val songArtMap = LinkedHashMap<Uri, Song>()

            for (i in songs.indices) {
                val song = songs[i]
                val uri = SongUtils.getArtworkUri(application, song.albumId, song.id)
                if (uri != null) {
                    songArtMap[uri] = song
                } else {
                    Log.w(TAG, "loadSongAndArt: No artwork URI for song ${song.title}")
                }
            }

            songAndArt.postValue(songArtMap)
        }
    }

    fun setCarouselPosition(position: Int) {
        carouselPosition = position
    }

    fun getCarouselPosition(): Int {
        return carouselPosition
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, s: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, s)
        when (s) {
            SongsPreferences.SONG_SORT -> {
                loadData()
                loadSongAndArt()
            }
            SongsPreferences.SORTING_STYLE -> {
                loadData()
                loadSongAndArt()
            }
        }
    }

    companion object {
        private const val TAG = "SongsViewModel"
    }
}
