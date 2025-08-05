package app.simple.felicity.glide.artistcover

import android.net.Uri
import android.widget.ImageView
import app.simple.felicity.glide.uricover.UriCoverModel
import app.simple.felicity.repository.models.Artist
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop

object ArtistCoverUtils {
    fun ImageView.loadPeristyleArtistCover(artist: Artist) {
        Glide.with(this)
            .asBitmap()
            .dontTransform()
            .dontAnimate()
            .transform(CenterCrop())
            .load(UriCoverModel(this.context, artUri = artist.artworkUri ?: Uri.EMPTY))
            .into(this)
    }
}
