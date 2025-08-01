// repository/models/normal/Song.kt
package app.simple.felicity.repository.models

data class Song(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val albumId: Long,
        val artistId: Long,
        val uri: String,
        val path: String,
        val duration: Long,
        val size: Long,
        val dateAdded: Long,
        val dateModified: Long,
        val artworkUri: String? = null
)