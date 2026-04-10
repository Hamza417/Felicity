package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.media.MediaScannerConnection
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.metadata.MetadataWriter
import app.simple.felicity.repository.models.Audio
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
import java.io.File

/**
 * ViewModel for the [app.simple.felicity.ui.panels.MetadataEditor] panel.
 *
 * Handles writing all edited tag fields to the audio file on disk via
 * [MetadataWriter], updating the Room database row via [AudioDatabase], and
 * notifying the MediaStore via [MediaScannerConnection] so that external apps
 * reflect the change immediately.
 *
 * Uses the assisted-inject pattern so that the [Audio] parcelable passed from
 * the fragment's arguments can be provided at creation time.
 *
 * @author Hamza417
 */
@HiltViewModel(assistedFactory = MetadataEditorViewModel.Factory::class)
class MetadataEditorViewModel @AssistedInject constructor(
        application: Application,
        @Assisted val audio: Audio
) : AndroidViewModel(application) {

    private val _isSaving = MutableStateFlow(false)

    /** Emits `true` while a save operation is in progress. */
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveResult = MutableSharedFlow<SaveResult>(extraBufferCapacity = 1)

    /** Emits a [SaveResult] exactly once after each save attempt. */
    val saveResult: SharedFlow<SaveResult> = _saveResult.asSharedFlow()

    /**
     * Writes the supplied [fields] to the audio file on disk, updates the Room
     * database, and requests a MediaStore rescan for the file. Results are
     * delivered through [saveResult].
     *
     * @param fields      The edited tag data to write.
     * @param updatedAudio A copy of the original [Audio] row with all mutable
     *                    string fields already set to the new values so the
     *                    database update reflects the editor's state.
     */
    fun saveMetadata(fields: MetadataWriter.Fields, updatedAudio: Audio) {
        if (_isSaving.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isSaving.emit(true)
            try {
                val path = updatedAudio.path
                    ?: throw IllegalStateException("Audio path must not be null.")
                val file = File(path)

                MetadataWriter.write(file, fields)

                updatedAudio.setDateModified(file.lastModified())

                AudioDatabase.getInstance(getApplication())
                    .audioDao()
                    ?.update(updatedAudio)

                MediaScannerConnection.scanFile(
                        getApplication(),
                        arrayOf(path),
                        null
                ) { _, _ ->
                    Log.d(TAG, "MediaStore scan complete: $path")
                }

                _saveResult.emit(SaveResult.Success)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save metadata: ${e.message}", e)
                _saveResult.emit(SaveResult.Error(e.message ?: "Unknown error"))
            } finally {
                _isSaving.emit(false)
            }
        }
    }

    /** Represents the outcome of a metadata save operation. */
    sealed class SaveResult {
        /** Metadata was written and the database was updated successfully. */
        data object Success : SaveResult()

        /** An error occurred during writing or database update. */
        data class Error(val message: String) : SaveResult()
    }

    /** Assisted factory so [Audio] can be injected at fragment creation time. */
    @AssistedFactory
    interface Factory {
        /** Creates a [MetadataEditorViewModel] scoped to the given [audio]. */
        fun create(audio: Audio): MetadataEditorViewModel
    }

    companion object {
        private const val TAG = "MetadataEditorViewModel"
    }
}

