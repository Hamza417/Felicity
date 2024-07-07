package app.simple.felicity.glide.pathcover

import android.widget.ImageView
import app.simple.felicity.glide.transformation.BlurShadow
import app.simple.felicity.glide.transformation.Padding
import app.simple.felicity.preferences.AppearancePreferences
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions
import com.bumptech.glide.load.resource.bitmap.RoundedCorners

object Utils {

    fun ImageView.loadFromPath(path: String) {
        Glide.with(context)
            .asBitmap()
            .load(PathCoverModel(context, path))
            .dontTransform()
            .centerCrop()
            .transition(BitmapTransitionOptions.withCrossFade())
            .into(this)
    }

    fun ImageView.loadFromPathForCarousel(path: String) {
        Glide.with(context)
            .asBitmap()
            .load(PathCoverModel(context, path))
            .transform(RoundedCorners(AppearancePreferences.getCornerRadius().toInt().coerceAtLeast(1)),
                       Padding(BlurShadow.MAX_BLUR_RADIUS.toInt()),
                       BlurShadow(context)
                           .setElevation(25F)
                           .setBlurRadius(BlurShadow.MAX_BLUR_RADIUS))
            .transition(BitmapTransitionOptions.withCrossFade())
            .into(this)
    }
}
