package app.simple.felicity.viewmodels.main.songs

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.Song
import app.simple.felicity.repository.repositories.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SongsViewModel @Inject constructor(
        application: Application,
        private val songRepository: SongRepository) : WrappedViewModel(application) {

    private val songs: MutableLiveData<List<Song>> by lazy {
        MutableLiveData<List<Song>>().also {
            loadData()
        }
    }

    fun getSongs(): LiveData<List<Song>> {
        return songs
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val songsList = songRepository.fetchSongs()
            songs.postValue(songsList)
            Log.d(TAG, "loadData: ${songsList.size} songs loaded")
        }
    }

    companion object {
        private const val TAG = "SongsViewModel"
    }
}
