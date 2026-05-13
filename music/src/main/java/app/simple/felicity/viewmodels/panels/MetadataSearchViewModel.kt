package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.MusicBrainzRecordingResult
import app.simple.felicity.repository.repositories.MusicBrainzRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ViewModel for the [app.simple.felicity.ui.subpanels.MetadataSearch] panel.
 *
 * Queries the MusicBrainz recording search using the title and artist from the [audio]
 * being edited so the user immediately sees relevant results when the screen opens.
 * The search keyword is persisted across configuration changes via [SavedStateHandle]
 * so a screen rotation doesn't wipe out what the user typed.
 *
 * @author Hamza417
 */
@HiltViewModel(assistedFactory = MetadataSearchViewModel.Factory::class)
class MetadataSearchViewModel @AssistedInject constructor(
        application: Application,
        @Assisted val audio: Audio,
        private val savedStateHandle: SavedStateHandle,
        private val musicBrainzRepository: MusicBrainzRepository
) : WrappedViewModel(application) {

    private val _searchResults = MutableLiveData<List<MusicBrainzRecordingResult>>(emptyList())

    /** The list of MusicBrainz recordings matching the latest search. */
    val searchResults: LiveData<List<MusicBrainzRecordingResult>> get() = _searchResults

    private val _isLoading = MutableLiveData(false)

    /** True while a network request is in flight. */
    val isLoading: LiveData<Boolean> get() = _isLoading

    /** The title field value, restored across configuration changes. */
    val titleKeyword: String
        get() = savedStateHandle[KEY_TITLE] ?: (audio.title?.takeIf { it.isNotBlank() } ?: audio.name)

    /** The artist field value, restored across configuration changes. */
    val artistKeyword: String
        get() = savedStateHandle[KEY_ARTIST] ?: (audio.artist?.takeIf { it.isNotBlank() } ?: "")

    init {
        triggerAutoSearch()
    }

    private fun triggerAutoSearch() {
        performSearch(titleKeyword, artistKeyword)
    }

    /** Saves the title the user typed so it survives a screen rotation. */
    fun setUserTitle(title: String) {
        savedStateHandle[KEY_TITLE] = title
    }

    /** Saves the artist the user typed so it survives a screen rotation. */
    fun setUserArtist(artist: String) {
        savedStateHandle[KEY_ARTIST] = artist
    }

    /**
     * Fires a new search using whatever is currently in the title and artist fields.
     */
    fun searchWithCurrentKeyword() {
        val title = titleKeyword.trim()
        if (title.isBlank()) {
            warning.postValue(getString(R.string.search))
            return
        }
        performSearch(title, artistKeyword.trim())
    }

    private fun performSearch(trackName: String, artistName: String) {
        if (trackName.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            Log.d(TAG, "Searching MusicBrainz recordings: '$trackName' by '$artistName'")

            val results = musicBrainzRepository.searchRecordings(trackName, artistName)
            _searchResults.postValue(results)

            if (results.isEmpty()) {
                warning.postValue(getString(R.string.no_lyrics_found))
            }

            Log.d(TAG, "Recording search returned ${results.size} result(s)")
            _isLoading.postValue(false)
        }
    }


    @AssistedFactory
    interface Factory {
        fun create(audio: Audio): MetadataSearchViewModel
    }

    companion object {
        private const val TAG = "MetadataSearchViewModel"
        private const val KEY_TITLE = "metadata_search_title"
        private const val KEY_ARTIST = "metadata_search_artist"
    }
}
