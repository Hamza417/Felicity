package app.simple.felicity.engine.managers

import android.util.Log
import app.simple.felicity.repository.models.normal.Audio
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

object AudioStateManager {

    private const val TAG = "AudioStateManager"
    private val _audioState = MutableStateFlow(AudioState())
    val audioState: StateFlow<AudioState> = _audioState

    private val _currentPositionState = MutableSharedFlow<Long>(replay = 1)
    val currentPositionState: SharedFlow<Long> = _currentPositionState

    fun playSong(audios: MutableList<Audio>) {
        _audioState.value = AudioState(
                playlist = audios,
                playbackState = PlaybackState.PLAYING,
                index = 0
        )
        // TODO: Start playback logic
    }

    fun pause() {
        _audioState.value = _audioState.value.copy(
                playbackState = PlaybackState.PAUSED
        )
        // TODO: Pause playback logic
    }

    fun resume() {
        _audioState.value = _audioState.value.copy(
                playbackState = PlaybackState.PLAYING
        )
        // TODO: Resume playback logic
    }

    fun stop() {
        _audioState.value = _audioState.value.copy(
                playbackState = PlaybackState.STOPPED,
        )
        // TODO: Stop playback logic
    }

    fun seekTo(position: Long) {
        _currentPositionState.tryEmit(position)
        // TODO: Seek playback logic
    }

    fun updatePlaybackState(state: PlaybackState) {
        _audioState.value = _audioState.value.copy(
                playbackState = state
        )
    }

    fun setCurrentPosition(position: Long) {
        _currentPositionState.tryEmit(position)
    }

    fun updateListPosition(position: Int) {
        _audioState.value = _audioState.value.copy(
                index = position
        )
    }

    fun setPlaylist(playlist: MutableList<Audio>, startIndex: Int = 0) {
        Log.d(TAG, "Setting playlist with ${playlist.size} items starting at index $startIndex")
        _audioState.value = _audioState.value.copy(
                playlist = playlist,
                playbackState = PlaybackState.IDLE,
                index = startIndex
        )
    }
}