package app.simple.felicity.glide.albumcover

import android.widget.ImageView
import com.bumptech.glide.Glide

object AlbumCoverUtils {

    fun ImageView.loadAlbumCoverSquare(albumId: Long) {
        Glide.with(context)
            .asBitmap()
            .dontTransform()
            .load(AlbumCoverModel(context, albumId))
            .into(this)
    }
}
