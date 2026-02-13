package app.simple.felicity.viewmodels.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.decorations.lrc.model.LrcData
import app.simple.felicity.decorations.lrc.parser.LrcParser
import app.simple.felicity.decorations.lrc.parser.LyricsParseException
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.repositories.LrcRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LyricsViewModel @Inject constructor(
        application: Application,
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
            val currentSong = MediaManager.getCurrentSong()
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
                    Log.d(TAG, "No existing LRC file found for ${currentSong.title}, attempting to fetch automatically.")
                    // No existing LRC file, try to fetch automatically
                    fetchAndSaveLrc(
                            trackName = currentSong.title ?: currentSong.name,
                            artistName = currentSong.artist ?: "",
                            audioPath = currentSong.path
                    )
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

    companion object {
        private const val TAG = "LyricsViewModel"
    }
}