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
import app.simple.felicity.repository.models.LrcLibResponse
import app.simple.felicity.repository.repositories.LrcRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ViewModel for the [app.simple.felicity.ui.subpanels.MetadataSearch] panel.
 *
 * Queries the LrcLib API using the title and artist from the [audio] being edited,
 * so the user immediately sees relevant results when the screen opens. The search
 * keyword is persisted across configuration changes via [SavedStateHandle] so that
 * a rotation or language change does not clear what the user typed.
 *
 * @author Hamza417
 */
@HiltViewModel(assistedFactory = MetadataSearchViewModel.Factory::class)
class MetadataSearchViewModel @AssistedInject constructor(
        application: Application,
        @Assisted val audio: Audio,
        private val savedStateHandle: SavedStateHandle,
        private val lrcRepository: LrcRepository
) : WrappedViewModel(application) {

    private val _searchResults = MutableLiveData<List<LrcLibResponse>>(emptyList())

    /** The list of LrcLib entries matching the latest search. */
    val searchResults: LiveData<List<LrcLibResponse>> get() = _searchResults

    private val _isLoading = MutableLiveData(false)

    /** True while a network request is in flight. */
    val isLoading: LiveData<Boolean> get() = _isLoading

    /**
     * The current search keyword — either what the user typed or the default
     * built from the audio's title and artist tags.
     */
    val keyword: String
        get() = savedStateHandle[KEY_KEYWORD] ?: buildDefaultKeyword()

    private val isKeywordUserModified: Boolean
        get() = savedStateHandle.get<Boolean>(KEY_USER_MODIFIED) ?: false

    init {
        if (!isKeywordUserModified) {
            savedStateHandle[KEY_KEYWORD] = buildDefaultKeyword()
        }
        triggerAutoSearch()
    }

    private fun triggerAutoSearch() {
        val title = audio.title?.takeIf { it.isNotBlank() } ?: audio.name
        val artist = audio.artist ?: ""
        performSearch(title, artist)
    }

    /**
     * Persists the user's typed keyword so it survives a configuration change.
     */
    fun setUserKeyword(query: String) {
        savedStateHandle[KEY_KEYWORD] = query
        savedStateHandle[KEY_USER_MODIFIED] = true
    }

    /**
     * Fires a search using whatever keyword is currently saved.
     */
    fun searchWithCurrentKeyword() {
        val query = keyword.trim()
        if (query.isBlank()) {
            warning.postValue(getString(R.string.search))
            return
        }
        performSearch(query, "")
    }

    private fun performSearch(trackName: String, artistName: String) {
        if (trackName.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            Log.d(TAG, "Searching metadata: trackName='$trackName', artistName='$artistName'")

            val result = lrcRepository.searchAllLyrics(trackName, artistName)

            result.onSuccess { results ->
                _searchResults.postValue(results)
                if (results.isEmpty()) {
                    warning.postValue(getString(R.string.no_lyrics_found))
                }
                Log.d(TAG, "Metadata search returned ${results.size} result(s)")
            }.onFailure { exception ->
                Log.e(TAG, "Metadata search failed", exception)
                warning.postValue(exception.message ?: getString(R.string.no_lyrics_found))
                _searchResults.postValue(emptyList())
            }

            _isLoading.postValue(false)
        }
    }

    private fun buildDefaultKeyword(): String {
        val title = audio.title?.takeIf { it.isNotBlank() } ?: audio.name
        val artist = audio.artist?.takeIf { it.isNotBlank() }
        return if (artist != null) "$title $artist" else title
    }

    @AssistedFactory
    interface Factory {
        fun create(audio: Audio): MetadataSearchViewModel
    }

    companion object {
        private const val TAG = "MetadataSearchViewModel"
        private const val KEY_KEYWORD = "metadata_search_keyword"
        private const val KEY_USER_MODIFIED = "metadata_search_user_modified"
    }
}




