package app.simple.felicity.viewmodels.main.songs

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.Song
import app.simple.felicity.repository.repositories.SongRepository
import app.simple.felicity.viewmodels.main.home.HomeViewModel.Companion.TAG
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SongsViewModel(application: Application) : WrappedViewModel(application) {

    private val songRepository by lazy {
        SongRepository(applicationContext())
    }

    private val _songs: MutableSharedFlow<List<Song>> = MutableSharedFlow(replay = 1)

    val songs = _songs.asSharedFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                val songsList = songRepository.fetchSongs()
                Log.d(TAG, "loadData: ${songsList.size} songs loaded")
                _songs.emit(songsList)
            } catch (e: Exception) {
                Log.e(TAG, "loadData: Error loading songs", e)
                _songs.emit(emptyList())
            }
        }
    }
}
