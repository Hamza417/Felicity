package app.simple.felicity.repository.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Genre(
        val id: Long,
        val name: String?,
        val songPaths: List<String>,
        val songCount: Int
) : Parcelable {
    override fun toString(): String {
        return "Genre(id=$id, name=$name, songPaths=$songPaths, songCount=$songCount)"
    }
}