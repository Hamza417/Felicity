package app.simple.felicity.glide.genres

import android.content.Context

class GenreCoverModel(val context: Context, val genreId: Long, val genreName: String) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val that = other as GenreCoverModel

        if (context != that.context) return false
        if (genreId != that.genreId) return false
        return genreName == that.genreName
    }

    override fun hashCode(): Int {
        var result = context.hashCode()
        result = 31 * result + genreId.hashCode()
        result = 31 * result + genreName.hashCode()
        return result
    }
}