package app.simple.felicity.repository.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Artist(
        val id: Long,
        val name: String?,
        val albumCount: Int,
        val trackCount: Int
) : Parcelable {
    override fun toString(): String {
        return "Artist(id=$id, " +
                "artistName='$name', " +
                "albumCount=$albumCount, " +
                "trackCount=$trackCount)"
    }
}