package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.AudioRepository
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
                                    .sortedBy { it.title?.lowercase() }
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

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    companion object {
        private const val TAG = "SearchViewModel"
    }
}