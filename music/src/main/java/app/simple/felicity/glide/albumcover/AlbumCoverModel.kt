package app.simple.felicity.glide.albumcover

import android.content.Context

class AlbumCoverModel(val context: Context, val albumId: Long) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AlbumCoverModel

        if (context != other.context) return false
        if (albumId != other.albumId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = context.hashCode()
        result = 31 * result + albumId.hashCode()
        return result
    }
}
