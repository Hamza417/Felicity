// repository/models/normal/Song.kt
package app.simple.felicity.repository.models

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Song(
        val id: Long,
        val title: String?,
        val artist: String?,
        val album: String?,
        val albumId: Long,
        val artistId: Long,
        val uri: Uri?,
        val path: String,
        val duration: Long,
        val size: Long,
        val dateAdded: Long,
        val dateModified: Long,
) : Parcelable {
    override fun toString(): String {
        return "Song(id=$id, " +
                "title=$title, " +
                "artist=$artist, " +
                "album=$album, " +
                "albumId=$albumId, " +
                "artistId=$artistId, " +
                "uri=$uri, " +
                "path='$path', " +
                "duration=$duration, " +
                "size=$size, " +
                "dateAdded=$dateAdded, " +
                "dateModified=$dateModified"
    }
}