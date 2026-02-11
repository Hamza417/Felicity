package app.simple.felicity.repository.models

data class PageData(
        val songs: List<Audio> = emptyList(),
        val albums: List<Album> = emptyList(),
        val artists: List<Artist> = emptyList(),
        val genres: List<Genre> = emptyList()
) {
    override fun toString(): String {
        return "CollectionPageData(audios=${songs.size}, albums=${albums.size}, artists=${artists.size}, genres=${genres.size})"
    }
}