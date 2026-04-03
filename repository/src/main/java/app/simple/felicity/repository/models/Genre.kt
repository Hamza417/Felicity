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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Genre) return false

        if (id != other.id) return false
        if (name != other.name) return false
        if (songPaths != other.songPaths) return false
        if (songCount != other.songCount) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + songPaths.hashCode()
        result = 31 * result + songCount
        return result
    }

}