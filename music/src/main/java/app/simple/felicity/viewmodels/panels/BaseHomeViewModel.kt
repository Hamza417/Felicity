package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.repositories.AudioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit

abstract class BaseHomeViewModel(
        application: Application,
        protected val audioRepository: AudioRepository
) : WrappedViewModel(application) {

    data class LibraryStats(val trackCount: Int, val totalHours: Long)

    protected val allAudioFlow = audioRepository.getAllAudio()
        .catch { e -> Log.e(TAG, "Error loading all audio", e); emit(mutableListOf()) }
        .flowOn(Dispatchers.IO)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val libraryStats: StateFlow<LibraryStats?> = allAudioFlow
        .map { audioList ->
            val totalMs = audioList.sumOf { it.duration }
            val totalHours = TimeUnit.MILLISECONDS.toHours(totalMs)
            LibraryStats(
                    trackCount = audioList.size,
                    totalHours = totalHours
            )
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeQueue: StateFlow<Int> = audioRepository.getPlaybackStateDao().getActiveQueueIdFlow()
        .catch { e -> Log.e(TAG, "Error fetching active queue id", e); emit(0) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    companion object {
        private const val TAG = "BaseHomeViewModel"
    }
}
