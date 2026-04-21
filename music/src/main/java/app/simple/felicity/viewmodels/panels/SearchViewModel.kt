package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.models.SearchCategoryFilter
import app.simple.felicity.models.SearchResults
import app.simple.felicity.preferences.SearchPreferences
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.sort.SearchSort.searchSorted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Search panel. Searches all audio fields (title, artist, album,
 * genre, composer) and groups results into [SearchResults] by category.
 * A 300 ms debounce prevents excessive queries while the user is typing.
 * Category visibility is driven by [SearchCategoryFilter] which is persisted
 * through [SearchPreferences].
 *
 * @author Hamza417
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
        application: Application,
        private val audioRepository: AudioRepository) : WrappedViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _categoryFilter = MutableStateFlow(loadCategoryFilter())
    val categoryFilter: StateFlow<SearchCategoryFilter> = _categoryFilter.asStateFlow()

    private val _searchResults = MutableStateFlow(SearchResults.empty())
    val searchResults: StateFlow<SearchResults> = _searchResults.asStateFlow()

    init {
        observeSearchQuery()
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeSearchQuery() {
        val debouncedQuery = _searchQuery
            .debounce(300L)
            .distinctUntilChanged()

        viewModelScope.launch {
            combine(debouncedQuery, _categoryFilter) { query, filter ->
                Pair(query, filter)
            }.flatMapLatest { (query, filter) ->
                if (query.isBlank()) {
                    flowOf(SearchResults.empty())
                } else {
                    combine(
                            audioRepository.searchByTitleFlow(query),
                            audioRepository.searchByArtistFlow(query),
                            audioRepository.searchByAlbumFlow(query),
                            audioRepository.searchByGenreFlow(query),
                            audioRepository.searchByComposerFlow(query)
                    ) { byTitle, byArtist, byAlbum, byGenre, byComposer ->
                        buildSearchResults(byTitle, byArtist, byAlbum, byGenre, byComposer, filter)
                    }
                }
            }.catch { e ->
                Log.e(TAG, "Error searching", e)
                emit(SearchResults.empty())
            }.flowOn(Dispatchers.IO)
                .collect { results ->
                    _searchResults.value = results
                    Log.d(TAG, "observeSearchQuery: songs=${results.songs.size}, albums=${results.albums.size}, artists=${results.artists.size}, genres=${results.genres.size}")
                }
        }
    }

    /**
     * Aggregates raw per-field query results into a [SearchResults] instance,
     * applying the current [SearchCategoryFilter] to suppress disabled categories.
     */
    private fun buildSearchResults(
            byTitle: List<Audio>,
            byArtist: List<Audio>,
            byAlbum: List<Audio>,
            byGenre: List<Audio>,
            byComposer: List<Audio>,
            filter: SearchCategoryFilter): SearchResults {

        val allAudio = (byTitle + byArtist + byAlbum + byGenre + byComposer)
            .distinctBy { it.id }
            .searchSorted()

        val songs = if (filter.songsEnabled) allAudio else emptyList()

        val albums = if (filter.albumsEnabled) {
            byAlbum.groupBy { it.album }
                .mapNotNull { (albumName, songs) ->
                    if (albumName.isNullOrEmpty()) return@mapNotNull null
                    val firstSong = songs.firstOrNull() ?: return@mapNotNull null
                    Album(
                            id = "${albumName}_${firstSong.artist}".hashCode().toLong(),
                            name = albumName,
                            artist = firstSong.artist,
                            artistId = firstSong.artist?.hashCode()?.toLong() ?: 0L,
                            songCount = songs.size,
                            songPaths = songs.map { it.uri }
                    )
                }
                .sortedBy { it.name?.lowercase() }
        } else {
            emptyList()
        }

        val artists = if (filter.artistsEnabled) {
            byArtist.groupBy { it.artist }
                .mapNotNull { (artistName, songs) ->
                    if (artistName.isNullOrEmpty()) return@mapNotNull null
                    val uniqueAlbums = songs.mapNotNull { it.album }.distinct().size
                    Artist(
                            id = artistName.hashCode().toLong(),
                            name = artistName,
                            albumCount = uniqueAlbums,
                            trackCount = songs.size,
                            songPaths = songs.map { it.uri }
                    )
                }
                .sortedBy { it.name?.lowercase() }
        } else {
            emptyList()
        }

        val genres = if (filter.genresEnabled) {
            byGenre.groupBy { it.genre }
                .mapNotNull { (genreName, songs) ->
                    if (genreName.isNullOrEmpty()) return@mapNotNull null
                    Genre(
                            id = genreName.hashCode().toLong(),
                            name = genreName,
                            songPaths = songs.map { it.uri },
                            songCount = songs.size
                    )
                }
                .sortedBy { it.name?.lowercase() }
        } else {
            emptyList()
        }

        return SearchResults(
                songs = songs,
                albums = albums,
                artists = artists,
                genres = genres
        )
    }

    private fun resort() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _searchResults.value
            _searchResults.value = current.copy(songs = current.songs.searchSorted())
        }
    }

    /**
     * Updates the active search query. The 300 ms debounce is applied internally
     * before any database queries are issued.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun loadCategoryFilter() = SearchCategoryFilter(
            songsEnabled = SearchPreferences.isSongsEnabled(),
            albumsEnabled = SearchPreferences.isAlbumsEnabled(),
            artistsEnabled = SearchPreferences.isArtistsEnabled(),
            genresEnabled = SearchPreferences.isGenresEnabled()
    )

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, s: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, s)
        when (s) {
            SearchPreferences.SONG_SORT, SearchPreferences.SORTING_STYLE -> resort()
            SearchPreferences.FILTER_SONGS,
            SearchPreferences.FILTER_ALBUMS,
            SearchPreferences.FILTER_ARTISTS,
            SearchPreferences.FILTER_GENRES -> {
                _categoryFilter.value = loadCategoryFilter()
            }
        }
    }

    companion object {
        private const val TAG = "SearchViewModel"
    }
}
