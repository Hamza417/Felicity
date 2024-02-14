package app.simple.felicity.glide.uricover

import android.content.Context
import android.net.Uri

class UriCoverModel(val context: Context, val artUri: Uri) {
    override fun hashCode(): Int {
        var result = context.hashCode()
        result = 31 * result + artUri.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        other as UriCoverModel

        if (context != other.context) return false
        return artUri == other.artUri
    }
}