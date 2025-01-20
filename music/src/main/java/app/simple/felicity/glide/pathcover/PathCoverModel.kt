package app.simple.felicity.glide.pathcover

import android.content.Context

class PathCoverModel(val context: Context, val path: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val that = other as PathCoverModel

        if (context != that.context) return false
        return path == that.path
    }

    override fun hashCode(): Int {
        var result = context.hashCode()
        result = 31 * result + path.hashCode()
        return result
    }
}
