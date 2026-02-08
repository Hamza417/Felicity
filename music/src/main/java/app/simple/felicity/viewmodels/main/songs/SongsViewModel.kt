package app.simple.felicity.viewmodels.main.songs

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.SongsPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.sort.SongSort.sorted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SongsViewModel @Inject constructor(
        application: Application,
        private val audioRepository: AudioRepository) : WrappedViewModel(application) {

    private var carouselPosition = 0

    private val songs: MutableLiveData<List<Audio>> by lazy {
        MutableLiveData<List<Audio>>().also {
            loadData()
        }
    }

    private val songAndArt: MutableLiveData<Map<Uri, Audio>> by lazy {
        MutableLiveData<Map<Uri, Audio>>().also {
            loadSongAndArt()
        }
    }

    fun getSongs(): LiveData<List<Audio>> {
        return songs
    }

    fun getSongAndArt(): LiveData<Map<Uri, Audio>> {
        return songAndArt
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val songsList = audioRepository.getAllAudioList().sorted()
            songs.postValue(songsList)
            Log.d(TAG, "loadData: ${songsList.size} songs loaded")
        }
    }

    private fun loadSongAndArt() {
        viewModelScope.launch(Dispatchers.IO) {

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
