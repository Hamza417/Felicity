package app.simple.felicity.repository.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Folder(
        val id: Long,
        val path: String,
        val name: String,
        val songPaths: List<String>,
        val songCount: Int
) : Parcelable {
    override fun toString(): String {
        return "Folder(id=$id, path=$path, name=$name, songPaths=$songPaths, songCount=$songCount)"
    }
}

