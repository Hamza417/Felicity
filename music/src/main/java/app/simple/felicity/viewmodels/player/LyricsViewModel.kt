package app.simple.felicity.viewmodels.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.decorations.lrc.model.LrcData
import app.simple.felicity.decorations.lrc.parser.LrcParser
import app.simple.felicity.decorations.lrc.parser.LyricsParseException
import app.simple.felicity.decorations.lrc.parser.TxtParser
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.LrcRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel(assistedFactory = LyricsViewModel.Factory::class)
class LyricsViewModel @AssistedInject constructor(
        application: Application,
        @Assisted private val audio: Audio?,
        private val lrcRepository: LrcRepository
) : WrappedViewModel(application) {

    private val lrcData: MutableLiveData<LrcData> by lazy {
        MutableLiveData<LrcData>().also {
            loadLrcData()
        }
    }

    fun getLrcData(): LiveData<LrcData> {
        return lrcData
    }

    fun loadLrcData() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentSong = audio ?: MediaManager.getCurrentSong()
            if (currentSong == null) {
                lrcData.postValue(LrcData())
                return@launch
            }

            // Try to load from existing file first
            val loadResult = lrcRepository.loadLrcFromFile(currentSong.path)

            loadResult.onSuccess { lrcContent ->
                if (lrcContent != null) {
                    // Parse and display existing LRC
                    Log.d(TAG, "Existing LRC file found for ${currentSong.title}, loading lyrics.")
                    try {
                        val lrcDataLoaded = LrcParser().parse(lrcContent)
                        lrcData.postValue(lrcDataLoaded)
                    } catch (e: LyricsParseException) {
                        e.printStackTrace()
                        lrcData.postValue(LrcData())
                    }
                } else {
                    Log.d(TAG, "No existing LRC file found for ${currentSong.title}, checking for TXT sidecar.")
                    // Try plain-text sidecar first
                    val txtResult = lrcRepository.loadTxtFromFile(currentSong.path)
                    val txtContent = txtResult.getOrNull()
                    if (!txtContent.isNullOrBlank()) {
                        Log.d(TAG, "TXT sidecar found for ${currentSong.title}, loading plain-text lyrics.")
                        try {
                            val txtLrcData = TxtParser().parse(txtContent)
                            lrcData.postValue(txtLrcData)
                        } catch (e: LyricsParseException) {
                            e.printStackTrace()
                            lrcData.postValue(LrcData())
                        }
                    } else {
                        Log.d(TAG, "No TXT sidecar found for ${currentSong.title}, attempting to fetch automatically.")
                        // No existing LRC or TXT file, try to fetch automatically
                        fetchAndSaveLrc(
                                trackName = currentSong.title ?: currentSong.name,
                                artistName = currentSong.artist ?: "",
                                audioPath = currentSong.path
                        )
                    }
                }
            }.onFailure { exception ->
                exception.printStackTrace()
                lrcData.postValue(LrcData())
            }
        }
    }

    /**
     * Fetch lyrics automatically and save to file.
     * This gets the first best match from the search results.
     */
    private suspend fun fetchAndSaveLrc(trackName: String, artistName: String, audioPath: String) {
        val searchResult = lrcRepository.searchLyrics(trackName, artistName)

        searchResult.onSuccess { results ->
            val bestMatch = results.firstOrNull()
            val syncedLyrics = bestMatch?.syncedLyrics
            if (bestMatch != null && !syncedLyrics.isNullOrBlank()) {
                try {
                    // Parse the LRC
                    val lrcDataLoaded = withContext(Dispatchers.Default) {
                        LrcParser().parse(syncedLyrics)
                    }

                    // Save to file
                    lrcRepository.saveLrcToFile(syncedLyrics, audioPath)

                    // Update LiveData
                    lrcData.postValue(lrcDataLoaded)
                } catch (e: LyricsParseException) {
                    e.printStackTrace()
                    lrcData.postValue(LrcData())
                }
            } else {
                lrcData.postValue(LrcData())
            }
        }.onFailure { exception ->
            exception.printStackTrace()
            lrcData.postValue(LrcData())
        }
    }

    /**
     * Reload LRC data. Useful after user selects new lyrics from search.
     */
    fun reloadLrcData() {
        loadLrcData()
    }

    @AssistedFactory
    interface Factory {
        fun create(audio: Audio?): LyricsViewModel
    }

    companion object {
        private const val TAG = "LyricsViewModel"
    }
}