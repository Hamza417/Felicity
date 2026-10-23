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
import app.simple.felicity.repository.models.YearGroup
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.sort.SearchSort.searchSorted
import app.simple.felicity.repository.utils.AudioUtils.getProperTitle
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
 * ViewModel for the Search panel. Searches all audio fields (title, file name, path,
 * artist, album, genre, composer, year) and groups results into [SearchResults] by
 * category (songs, albums, artists, genres, composers, years).
 * A 300 ms debounce prevents excessive queries while the user is typing.
 * Category visibility is driven by [SearchCategoryFilter] which is persisted
 * through [SearchPreferences]. Song results are additionally ranked by title-match
 * relevance so the most relevant track always appears first (see [titleRelevanceRank]).
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
                    val coreAudioResults = combine(
                            audioRepository.searchByTitleFlow(query),
                            audioRepository.searchArtistsFlow(query),
                            audioRepository.searchByAlbumFlow(query),
                            audioRepository.searchByGenreFlow(query),
                            audioRepository.searchByComposerFlow(query)
                    ) { byTitle, artists, byAlbum, byGenre, byComposer ->
                        CoreAudioResults(byTitle, artists, byAlbum, byGenre, byComposer)
                    }

                    combine(
                            coreAudioResults,
                            audioRepository.searchComposersFlow(query),
                            audioRepository.searchYearGroupsFlow(query)
                    ) { core, composers, years ->
                        buildSearchResults(core, composers, years, query, filter)
                    }
                }
            }.catch { e ->
                Log.e(TAG, "Error searching", e)
                emit(SearchResults.empty())
            }.flowOn(Dispatchers.IO)
                .collect { results ->
                    _searchResults.value = results
                    Log.d(TAG, "observeSearchQuery: songs=${results.songs.size}, albums=${results.albums.size}, " +
                            "artists=${results.artists.size}, genres=${results.genres.size}, " +
                            "composers=${results.composers.size}, years=${results.years.size}")
                }
        }
    }

    /**
     * Intermediate holder for the five audio-table queries that make up the "core" of a
     * search pass (songs, artists, albums, genres, composer-matched songs), combined in a
     * single step before being merged with the composer/year grouping flows.
     */
    private data class CoreAudioResults(
            val byTitle: List<Audio>,
            val artists: List<Artist>,
            val byAlbum: List<Audio>,
            val byGenre: List<Audio>,
            val byComposer: List<Audio>
    )

    /**
     * Aggregates raw per-field query results into a [SearchResults] instance,
     * applying the current [SearchCategoryFilter] to suppress disabled categories.
     *
     * Songs are additionally ranked by how closely their title matches [query] — an exact
     * (or "starts with") title match is always surfaced above other songs that only matched
     * through an unrelated field (e.g. the album name), even if those other songs would
     * otherwise sort earlier alphabetically. This fixes the case where a song shares its
     * name with its album: previously every track on that album would be interleaved
     * alphabetically, burying the actual match.
     */
    private fun buildSearchResults(
            core: CoreAudioResults,
            composers: List<Artist>,
            years: List<YearGroup>,
            query: String,
            filter: SearchCategoryFilter): SearchResults {
        val (byTitle, artists, byAlbum, byGenre, byComposer) = core

        val allAudio = (byTitle + byAlbum + byGenre + byComposer)
            .distinctBy { it.id }
            .searchSorted()
            .sortedBy { it.titleRelevanceRank(query) }

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

        val filteredArtists = if (filter.artistsEnabled) artists else emptyList()

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

        val filteredComposers = if (filter.composersEnabled) composers else emptyList()
        val filteredYears = if (filter.yearsEnabled) years else emptyList()

        return SearchResults(
                songs = songs,
                albums = albums,
                artists = filteredArtists,
                genres = genres,
                composers = filteredComposers,
                years = filteredYears
        )
    }


    /**
     * Ranks how closely this track's title matches [query], for sorting purposes.
     * Lower is a better match: 0 = exact match, 1 = starts with, 2 = contains, 3 = no title match
     * (i.e. the track only matched through another field like album, genre, or composer).
     */
    private fun Audio.titleRelevanceRank(query: String): Int {
        val title = getProperTitle()
        return when {
            title.equals(query, ignoreCase = true) -> 0
            title.startsWith(query, ignoreCase = true) -> 1
            title.contains(query, ignoreCase = true) -> 2
            else -> 3
        }
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
            genresEnabled = SearchPreferences.isGenresEnabled(),
            composersEnabled = SearchPreferences.isComposersEnabled(),
            yearsEnabled = SearchPreferences.isYearsEnabled()
    )

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, s: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, s)
        when (s) {
            SearchPreferences.SONG_SORT, SearchPreferences.SORTING_STYLE -> resort()
            SearchPreferences.FILTER_SONGS,
            SearchPreferences.FILTER_ALBUMS,
            SearchPreferences.FILTER_ARTISTS,
            SearchPreferences.FILTER_GENRES,
            SearchPreferences.FILTER_COMPOSERS,
            SearchPreferences.FILTER_YEARS -> {
                _categoryFilter.value = loadCategoryFilter()
            }
        }
    }

    companion object {
        private const val TAG = "SearchViewModel"
    }
}
