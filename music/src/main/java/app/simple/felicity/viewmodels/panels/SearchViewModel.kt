package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.SearchPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.sort.SearchSort.searchSorted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
        application: Application,
        private val audioRepository: AudioRepository) : WrappedViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _songs = MutableStateFlow<List<Audio>>(emptyList())
    val songs: StateFlow<List<Audio>> = _songs.asStateFlow()

    init {
        observeSearchQuery()
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300L)
                .distinctUntilChanged()
                .map { query ->
                    if (query.isBlank()) {
                        emptyList()
                    } else {
                        audioRepository.searchByTitle(query)
                            .also { byTitle ->
                                val byArtist = audioRepository.searchByArtist(query)
                                val byAlbum = audioRepository.searchByAlbum(query)
                                return@map (byTitle + byArtist + byAlbum)
                                    .distinctBy { it.id }
                                    .searchSorted()
                            }
                    }
                }
                .catch { e ->
                    Log.e(TAG, "Error searching songs", e)
                    emit(emptyList())
                }
                .flowOn(Dispatchers.IO)
                .collect { results ->
                    _songs.value = results
                    Log.d(TAG, "observeSearchQuery: ${results.size} results for '${_searchQuery.value}'")
                }
        }
    }

    private fun resort() {
        viewModelScope.launch(Dispatchers.IO) {
            _songs.value = _songs.value.searchSorted()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, s: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, s)
        when (s) {
            SearchPreferences.SONG_SORT, SearchPreferences.SORTING_STYLE -> {
                resort()
            }
        }
    }

    companion object {
        private const val TAG = "SearchViewModel"
    }
}

