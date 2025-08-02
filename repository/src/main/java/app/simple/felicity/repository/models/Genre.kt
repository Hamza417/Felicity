package app.simple.felicity.repository.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Genre(
        val id: Long,
        val name: String?
) : Parcelable {
    override fun toString(): String {
        return "Genre(id=\$id, name=\$name)"
    }
}