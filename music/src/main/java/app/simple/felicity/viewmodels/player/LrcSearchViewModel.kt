package app.simple.felicity.viewmodels.player

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.decorations.lrc.model.LrcData
import app.simple.felicity.decorations.lrc.parser.LrcParser
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.LrcLibResponse
import app.simple.felicity.repository.repositories.LrcRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LrcSearchViewModel @Inject constructor(
        application: Application,
        private val lrcRepository: LrcRepository
) : WrappedViewModel(application) {

    private val _searchResults = MutableLiveData<List<LrcLibResponse>>()
    val searchResults: LiveData<List<LrcLibResponse>> get() = _searchResults

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _error = MutableLiveData<String?>()
    val lrcError: LiveData<String?> get() = _error

    private val _selectedLrc = MutableLiveData<LrcData>()
    val selectedLrc: LiveData<LrcData> get() = _selectedLrc

    private val _lrcSaved = MutableLiveData<Boolean>()
    val lrcSaved: LiveData<Boolean> get() = _lrcSaved

    private var currentAudio: Audio? = null

    /**
     * Initialize with the current audio being played.
     */
    fun setAudio(audio: Audio) {
        currentAudio = audio
    }

    /**
     * Search for lyrics based on the provided track name and artist name.
     */
    fun searchLyrics(trackName: String, artistName: String) {
        if (trackName.isBlank()) {
            _error.value = "Track name cannot be empty"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = lrcRepository.searchLyrics(trackName.trim(), artistName.trim())

            result.onSuccess { results ->
                if (results.isEmpty()) {
                    _error.value = "No lyrics found for this track"
                    _searchResults.value = emptyList()
                } else {
                    _searchResults.value = results
                }
            }.onFailure { exception ->
                _error.value = exception.message ?: "Failed to search lyrics"
                _searchResults.value = emptyList()
            }

            _isLoading.value = false
        }
    }

    /**
     * Select and download a specific LRC from the search results.
     * This will parse the LRC content and save it to the storage.
     */
    fun selectAndDownloadLrc(lrcResponse: LrcLibResponse) {
        val audio = currentAudio
        if (audio == null) {
            _error.value = "No audio file set"
            return
        }

        val syncedLyrics = lrcResponse.syncedLyrics
        if (syncedLyrics.isNullOrBlank()) {
            _error.value = "This lyrics entry has no synced lyrics"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Parse the LRC content
                val lrcData = withContext(Dispatchers.Default) {
                    LrcParser().parse(syncedLyrics)
                }

                // Save to file as sidecar
                val saveResult = lrcRepository.saveLrcToFile(
                        syncedLyrics,
                        audio.path
                )

                saveResult.onSuccess {
                    _selectedLrc.value = lrcData
                    _lrcSaved.value = true
                }.onFailure { exception ->
                    _error.value = "Failed to save lyrics: ${exception.message}"
                    _lrcSaved.value = false
                }
            } catch (e: Exception) {
                _error.value = "Failed to parse lyrics: ${e.message}"
                _lrcSaved.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Load existing LRC from file if available.
     */
    fun loadExistingLrc() {
        val audio = currentAudio
        if (audio == null) {
            _error.value = "No audio file set"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true

            val result = lrcRepository.loadLrcFromFile(audio.path)

            result.onSuccess { lrcContent ->
                if (lrcContent != null) {
                    try {
                        val lrcData = withContext(Dispatchers.Default) {
                            LrcParser().parse(lrcContent)
                        }
                        _selectedLrc.value = lrcData
                    } catch (e: Exception) {
                        _error.value = "Failed to parse existing lyrics: ${e.message}"
                    }
                } else {
                    // No existing LRC file found, trigger search with audio metadata
                    searchLyrics(audio.title ?: audio.name, audio.artist ?: "")
                }
            }.onFailure { exception ->
                _error.value = "Failed to load lyrics: ${exception.message}"
            }

            _isLoading.value = false
        }
    }

    /**
     * Check if an LRC file already exists for the current audio.
     */
    fun lrcExists(): Boolean {
        val audio = currentAudio ?: return false
        return lrcRepository.lrcFileExists(audio.path)
    }

    /**
     * Clear the search results.
     */
    fun clearSearchResults() {
        _searchResults.value = emptyList()
        _error.value = null
    }

    /**
     * Reset the error message.
     */
    fun clearError() {
        _error.value = null
    }
}

