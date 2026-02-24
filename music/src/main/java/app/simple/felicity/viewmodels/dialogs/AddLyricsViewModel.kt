package app.simple.felicity.viewmodels.dialogs

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.LrcRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Regex that matches a standard LRC timestamp: [mm:ss.xx] or [mm:ss.xxx]
 */
private val LRC_TIMESTAMP_REGEX = Regex("""\[\d{1,2}:\d{2}\.\d{2,3}]""")

@HiltViewModel(assistedFactory = AddLyricsViewModel.Factory::class)
class AddLyricsViewModel @AssistedInject constructor(
        application: Application,
        @Assisted val audio: Audio,
        private val lrcRepository: LrcRepository
) : AndroidViewModel(application) {

    /**
     * Emits the saved file path on success, or null on failure.
     * Uses SharedFlow so the dialog can react only once (not re-emit on rotation).
     */
    private val _saveResult = MutableSharedFlow<SaveResult>(extraBufferCapacity = 1)
    val saveResult: SharedFlow<SaveResult> = _saveResult.asSharedFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /**
     * Determine whether the pasted text contains LRC timestamps.
     */
    fun hasTimestamps(text: String): Boolean {
        return LRC_TIMESTAMP_REGEX.containsMatchIn(text)
    }

    /**
     * Save the pasted lyrics as either an .lrc sidecar (when timestamps are present)
     * or a .txt sidecar (plain lyrics), then emit a [SaveResult].
     */
    fun saveLyrics(text: String) {
        if (text.isBlank()) {
            viewModelScope.launch {
                _saveResult.emit(SaveResult.Error("Lyrics text is empty."))
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isSaving.emit(true)
            try {
                val audioPath = audio.path ?: run {
                    _saveResult.emit(SaveResult.Error("Audio path is null."))
                    return@launch
                }

                val result = if (hasTimestamps(text)) {
                    // Save as .lrc sidecar
                    Log.d(TAG, "Timestamps detected, saving as .lrc")
                    lrcRepository.saveLrcToFile(text, audioPath)
                } else {
                    // Save as .txt sidecar
                    Log.d(TAG, "No timestamps detected, saving as .txt")
                    lrcRepository.saveTxtToFile(text, audioPath)
                }

                result.fold(
                        onSuccess = { file ->
                            _saveResult.emit(SaveResult.Success(file.absolutePath, hasTimestamps(text)))
                        },
                        onFailure = { e ->
                            _saveResult.emit(SaveResult.Error(e.message ?: "Unknown error"))
                        }
                )
            } finally {
                _isSaving.emit(false)
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(audio: Audio): AddLyricsViewModel
    }

    sealed class SaveResult {
        data class Success(val filePath: String, val isLrc: Boolean) : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    companion object {
        private const val TAG = "AddLyricsViewModel"
    }
}

