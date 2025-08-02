package app.simple.felicity.repository.models

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Album(
        val id: Long,
        val name: String?,
        val artist: String?,
        val artistId: Long,
        val artworkUri: Uri? = null,
        val songCount: Int = 0
) : Parcelable {
    override fun toString(): String {
        return "Album(id=$id, " +
                "name=$name, " +
                "artist=$artist, " +
                "artistId=$artistId, " +
                "artworkUri=$artworkUri, " +
                "songCount=$songCount)"
    }
}