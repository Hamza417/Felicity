package app.simple.felicity.models

import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song

data class CollectionPageData(
        val songs: List<Song> = emptyList(),
        val albums: List<Album> = emptyList(),
        val artists: List<Artist> = emptyList(),
        val genres: List<Genre> = emptyList()
) {
    override fun toString(): String {
        return "CollectionPageData(songs=${songs.size}, albums=${albums.size}, artists=${artists.size})"
    }
}