package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.AudioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Powers the "Always Skipped" panel by keeping a live list of every song the
 * user has flagged to always be skipped during playback. Think of it as the
 * "hall of shame" for songs that just didn't make the cut.
 *
 * The list reacts automatically to any database change — add or remove a song
 * from the always-skip list and this ViewModel will know about it instantly.
 *
 * @author Hamza417
 */
@HiltViewModel
class AlwaysSkippedViewModel @Inject constructor(
        application: Application,
        private val audioRepository: AudioRepository
) : WrappedViewModel(application) {

    private val _skippedSongs = MutableStateFlow<List<Audio>>(emptyList())

    /** The live list of always-skipped songs. Observe this in the fragment. */
    val skippedSongs: StateFlow<List<Audio>> = _skippedSongs.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadSkippedSongs()
    }

    private fun loadSkippedSongs() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            audioRepository.getAlwaysSkippedAudio()
                .catch { e ->
                    Log.e(TAG, "Oops — failed to load always-skipped songs", e)
                    emit(emptyList())
                }
                .flowOn(Dispatchers.IO)
                .collect { list ->
                    _skippedSongs.value = list
                    Log.d(TAG, "Always-skipped songs updated: ${list.size} songs in the hall of shame")
                }
        }
    }

    companion object {
        private const val TAG = "AlwaysSkippedViewModel"
    }
}

