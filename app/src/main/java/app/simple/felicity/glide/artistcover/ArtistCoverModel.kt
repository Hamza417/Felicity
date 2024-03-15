package app.simple.felicity.glide.artistcover

import android.content.Context

class ArtistCoverModel(val context: Context, val name: String) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ArtistCoverModel

        if (context != other.context) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = context.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}
