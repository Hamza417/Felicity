package app.simple.felicity.viewmodels.main.songs

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.normal.Audio
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.viewmodels.main.home.HomeViewModel.Companion.TAG
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class SongsViewModel(application: Application) : WrappedViewModel(application) {

    private val audioRepository by lazy {
        AudioRepository(AudioDatabase.getInstance(applicationContext())?.audioDao()!!)
    }

    private val _songs: MutableSharedFlow<MutableList<Audio>> = MutableSharedFlow(replay = 1)

    val songs = _songs.asSharedFlow()

    init {
        loadData()
    }

    private fun loadData() {
        audioRepository.getAllAudio()
            .onEach {
                Log.d(TAG, "loadData: ${it.size} songs loaded")
                try {
                    _songs.emit(it)
                } catch (e: ClassCastException) {
                    Log.e(TAG, "loadData: Error casting songs to ArrayList<Audio?>", e)
                    _songs.emit(it)
                }
            }
            .launchIn(viewModelScope)
    }
}
