package app.simple.felicity.models

import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Genre

/**
 * Encapsulates the categorized results of a full-library search query,
 * grouping matches by their respective media type.
 *
 * @author Hamza417
 */
data class SearchResults(
        val songs: List<Audio> = emptyList(),
        val albums: List<Album> = emptyList(),
        val artists: List<Artist> = emptyList(),
        val genres: List<Genre> = emptyList()
) {
    fun isEmpty(results: SearchResults): Boolean {
        return results.songs.isEmpty() &&
                results.albums.isEmpty() &&
                results.artists.isEmpty() &&
                results.genres.isEmpty()
    }

    fun isSmallDataSet(): Boolean {
        val totalResults = songs.size + albums.size + artists.size + genres.size
        return totalResults < 10
    }

    companion object {
        /**
         * Returns an instance of [SearchResults] with all lists empty,
         * representing no search results.
         */
        fun empty(): SearchResults = SearchResults()
    }
}

/**
 * Represents the user-selected category filter for the Search panel.
 * Each flag controls whether results of that type are included in [SearchResults].
 *
 * @author Hamza417
 */
data class SearchCategoryFilter(
        val songsEnabled: Boolean = true,
        val albumsEnabled: Boolean = true,
        val artistsEnabled: Boolean = true,
        val genresEnabled: Boolean = true
)

