package app.simple.felicity.repository.models

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Artist(
        val id: Long,
        val name: String?,
        val albumCount: Int,
        val trackCount: Int,
        val artworkUri: Uri? = null
) : Parcelable {
    override fun toString(): String {
        return "Artist(id=$id, " +
                "artistName='$name', " +
                "albumCount=$albumCount, " +
                "trackCount=$trackCount)"
    }
}