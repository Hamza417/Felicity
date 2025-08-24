package app.simple.felicity.repository.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Album(
        val id: Long,
        val name: String?,
        val artist: String?,
        val artistId: Long,
        val songCount: Int = 0,
        val firstYear: Long = 0,
        val lastYear: Long = 0
) : Parcelable {
    override fun toString(): String {
        return "Album(id=$id, " +
                "name=$name, " +
                "artist=$artist, " +
                "artistId=$artistId, " +
                "songCount=$songCount)" +
                "firstYear=$firstYear)" +
                "lastYear=$lastYear)"
    }
}