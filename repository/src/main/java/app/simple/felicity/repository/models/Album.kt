package app.simple.felicity.repository.models

data class Album(
        val id: Long,
        val name: String,
        val artist: String,
        val artistId: Long,
        val artworkUri: String? = null,
        val songCount: Int = 0
)