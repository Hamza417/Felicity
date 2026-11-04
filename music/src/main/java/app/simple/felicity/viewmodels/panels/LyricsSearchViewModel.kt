package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.managers.LyricsManager
import app.simple.felicity.repository.models.LrcLibResponse
import app.simple.felicity.repository.repositories.LrcRepository
import app.simple.felicity.repository.utils.AudioUtils.getProperTitle
import app.simple.felicity.repository.utils.toLyricsErrorCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/**
 * ViewModel for the [app.simple.felicity.ui.subpanels.LyricsSearch] panel.
 *
 * Handles fetching LRC lyrics from the LrcLib API, persisting the user's search keyword
 * across configuration changes and process death via [SavedStateHandle], and saving the
 * selected [LrcLibResponse] as a sidecar `.lrc` file next to the currently playing audio file.
 *
 * On first creation the ViewModel auto-searches using the currently playing song's title and
 * artist from [MediaPlaybackManager]. If the user subsequently modifies the search keyword
 * and presses search, the user-provided text is used instead and is persisted so it survives
 * process death.
 *
 * @author Hamza417
 */
@HiltViewModel
class LyricsSearchViewModel @Inject constructor(
        application: Application,
        private val savedStateHandle: SavedStateHandle,
        private val lrcRepository: LrcRepository,
        private val lyricsManager: LyricsManager
) : WrappedViewModel(application) {

    /** The current list of [LrcLibResponse] items returned by the latest search. */
    val searchResults: LiveData<List<LrcLibResponse>>
        field = MutableLiveData<List<LrcLibResponse>>(emptyList())

    /** True while a network search or file-save operation is in progress. */
    val isLoading: LiveData<Boolean>
        field = MutableLiveData(false)

    /** Short error code (e.g. `HTTP 503`, `NO_INTERNET`) of the latest failed search, or `null`. */
    val errorCode: LiveData<String?>
        field = MutableLiveData<String?>(null)

    /**
     * Emits `true` once an LRC entry has been successfully saved to disk.
     * The UI should observe this and navigate back on a `true` emission.
     */
    val lrcSaved: LiveData<Boolean>
        field = MutableLiveData<Boolean>()

    /**
     * The current search keyword.
     *
     * Returns the keyword persisted in [SavedStateHandle] if one exists, or falls back
     * to the title of the currently playing song via [buildDefaultKeyword].
     */
    val keyword: String
        get() = savedStateHandle[KEY_KEYWORD] ?: buildDefaultKeyword()

    /**
     * Returns `true` if the user has manually edited the search keyword at least once
     * since the panel was first opened.
     */
    val isKeywordUserModified: Boolean
        get() = savedStateHandle.get<Boolean>(KEY_USER_MODIFIED) ?: false

    var targetDuration: Long = 0L

    init {
        // Populate the default keyword in SavedStateHandle so the fragment can read it
        // immediately without an extra call to buildDefaultKeyword().
        if (!isKeywordUserModified) {
            savedStateHandle[KEY_KEYWORD] = buildDefaultKeyword()
        }
        // Kick off an automatic search using the current song's metadata.
        triggerAutoSearch()
    }

    /**
     * Performs an automatic search using the title and artist of the currently playing song.
     * Falls back to [keyword] as the track name if no song is playing.
     */
    private fun triggerAutoSearch() {
        val audio = MediaPlaybackManager.getCurrentSong()
        if (audio != null) {
            targetDuration = audio.duration
            val title = audio.getProperTitle()
            val artist = audio.artist ?: ""
            performSearch(title, artist)
        } else {
            val fallback = keyword
            if (fallback.isNotBlank()) {
                performSearch(fallback, "")
            }
        }
    }

    /**
     * Persists the user-typed keyword in [SavedStateHandle] and marks it as user-modified
     * so that [triggerAutoSearch] does not overwrite it on a subsequent ViewModel recreation.
     *
     * @param query the text entered by the user in the search field.
     */
    fun setUserKeyword(query: String) {
        savedStateHandle[KEY_KEYWORD] = query
        savedStateHandle[KEY_USER_MODIFIED] = true
    }

    /**
     * Executes a search using whatever keyword is currently saved in [SavedStateHandle].
     * The whole keyword is passed as `trackName` with an empty `artistName` since the user
     * may have typed either a title, a title + artist, or any free-form query.
     */
    fun searchWithCurrentKeyword() {
        val query = keyword.trim()
        if (query.isBlank()) {
            warning.postValue(getString(R.string.search))
            return
        }
        performSearch(query, "")
    }

    /**
     * Sends a search request to [LrcRepository] and updates [searchResults].
     * Posts an appropriate warning message to [warning] when no results are found or on error.
     *
     * @param trackName  the song title to search for.
     * @param artistName the artist name to search for (maybe empty).
     */
    private fun performSearch(trackName: String, artistName: String) {
        if (trackName.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            isLoading.postValue(true)
            errorCode.postValue(null)
            Log.d(TAG, "Searching for lyrics: trackName='$trackName', artistName='$artistName'")

            val result = lrcRepository.searchLyrics(trackName, artistName)

            result.onSuccess { results ->
                val durationSec = targetDuration / 1000.0
                val sortedResults = results.sortedBy {
                    abs(it.duration - durationSec)
                }

                searchResults.postValue(sortedResults)

                if (sortedResults.isEmpty()) {
                    warning.postValue(getString(R.string.no_lyrics_found))
                }

                Log.d(TAG, "Search returned ${sortedResults.size} result(s)")
            }.onFailure { exception ->
                Log.e(TAG, "Search failed", exception)
                val code = exception.toLyricsErrorCode()
                errorCode.postValue(code)
                warning.postValue(code)
                searchResults.postValue(emptyList())
            }

            isLoading.postValue(false)
        }
    }

    /**
     * Downloads the `syncedLyrics` content from [lrcResponse] and saves it as a `.lrc`
     * sidecar file next to the currently playing audio file using [LrcRepository].
     *
     * Any pre-existing .lrc or .txt sidecar files are removed first so the freshly
     * downloaded content is guaranteed to be what loads next — no stale file will
     * sneak in ahead of the new one.
     *
     * On success, posts `true` to [lrcSaved] signaling the UI to navigate back, and
     * tells [LyricsManager] to reload so every screen immediately shows the new lyrics.
     * On failure, posts an error message to [warning].
     *
     * @param lrcResponse the chosen search result to save.
     */
    fun downloadAndSaveLrc(lrcResponse: LrcLibResponse) {
        val audio = MediaPlaybackManager.getCurrentSong()
        if (audio == null) {
            warning.postValue(getString(R.string.no_lyrics_found))
            return
        }

        val syncedLyrics = lrcResponse.syncedLyrics
        if (syncedLyrics.isNullOrBlank()) {
            warning.postValue(getString(R.string.no_lyrics_found))
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            isLoading.postValue(true)
            Log.d(TAG, "Saving LRC for: ${audio.title} -> ${audio.uri}")

            // Wipe out whatever was there before so the fresh download is the only
            // source of truth going forward — no accidental fallback to old files.
            lrcRepository.deleteLrcFile(audio.uri)

            val saveResult = lrcRepository.saveLrcToFile(syncedLyrics, audio.uri)

            saveResult.onSuccess {
                Log.d(TAG, "LRC saved successfully to ${it.path}")
                // Tell the manager to drop its cache and reload so every
                // screen — player, lyrics panel, etc. — gets the new lyrics right away.
                lyricsManager.reloadLrcData()
                lrcSaved.postValue(true)
            }.onFailure { exception ->
                Log.e(TAG, "Failed to save LRC", exception)
                warning.postValue(exception.toLyricsErrorCode())
            }

            isLoading.postValue(false)
        }
    }

    /**
     * Builds the default search keyword from the currently playing song's title.
     * Returns an empty string if no song is currently playing.
     */
    private fun buildDefaultKeyword(): String {
        val audio = MediaPlaybackManager.getCurrentSong() ?: return ""
        return audio.getProperTitle()
    }

    companion object {
        private const val TAG = "LyricsSearchViewModel"
        private const val KEY_KEYWORD = "lyrics_search_keyword"
        private const val KEY_USER_MODIFIED = "lyrics_search_user_modified"
    }
}

