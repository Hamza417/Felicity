package app.simple.felicity.glide.pathcover

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions

object Utils {

    fun ImageView.loadFromPath(path: String) {
        Glide.with(context)
            .asBitmap()
            .load(PathCoverModel(context, path))
            .dontTransform()
            .transition(BitmapTransitionOptions.withCrossFade())
            .into(this)
    }
}
