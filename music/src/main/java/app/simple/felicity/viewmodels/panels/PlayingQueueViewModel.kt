package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayingQueueViewModel @Inject constructor(
        application: Application) : WrappedViewModel(application) {

    private val _songs = MutableStateFlow<List<Audio>>(emptyList())
    val songs: StateFlow<List<Audio>> = _songs.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    init {
        observeQueue()
        observePosition()
    }

    private fun observeQueue() {
        viewModelScope.launch {
            MediaManager.songListFlow.collect { audioList ->
                Log.d(TAG, "Queue updated: ${audioList.size} songs")
                _songs.value = audioList
            }
        }
    }

    private fun observePosition() {
        viewModelScope.launch {
            MediaManager.songPositionFlow.collect { position ->
                Log.d(TAG, "Queue position updated: $position")
                _currentPosition.value = position
            }
        }
    }

    companion object {
        private const val TAG = "PlayingQueueViewModel"
    }
}

