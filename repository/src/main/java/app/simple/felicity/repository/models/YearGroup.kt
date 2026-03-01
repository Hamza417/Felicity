package app.simple.felicity.repository.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class YearGroup(
        val id: Long,
        val year: String,
        val songPaths: List<String>,
        val songCount: Int
) : Parcelable {
    override fun toString(): String {
        return "YearGroup(id=$id, year=$year, songPaths=$songPaths, songCount=$songCount)"
    }
}

