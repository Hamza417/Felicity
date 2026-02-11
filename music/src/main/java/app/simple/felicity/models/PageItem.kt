package app.simple.felicity.models

import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Genre

sealed class PageItem {
    /**
     * Header with album info, art flow, and controls
     */
    data class Header(
            val album: Album,
            val totalSongs: Int,
            val totalDuration: Long,
            val albumArtists: List<Artist>,
            val songs: List<Audio>
    ) : PageItem()

    /**
     * Sub-header carousel for albums from this artist
     */
    data class AlbumsSection(
            val albums: List<Album>,
            val artistName: String?
    ) : PageItem()

    /**
     * Sub-header carousel for artists in this album
     */
    data class ArtistsSection(
            val artists: List<Artist>
    ) : PageItem()

    /**
     * Sub-header carousel for genres in this album
     */
    data class GenresSection(
            val genres: List<Genre>
    ) : PageItem()

    /**
     * Individual song item
     */
    data class SongItem(
            val audio: Audio,
            val position: Int, // Position in the songs list
            val allSongs: List<Audio> // Reference to all songs for playback
    ) : PageItem()
}

