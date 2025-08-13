package app.simple.felicity.glide.genres

import android.widget.ImageView
import app.simple.felicity.R
import app.simple.felicity.glide.transformation.BlurShadow
import app.simple.felicity.glide.transformation.Padding
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.repository.models.Genre
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners

object GenreCoverUtils {
    fun ImageView.loadGenreCover(
            genre: Genre,
            roundedCorners: Boolean = true,
            blur: Boolean = true,
            skipCache: Boolean = false
    ) {
        val transformations = mutableListOf<com.bumptech.glide.load.Transformation<android.graphics.Bitmap>>()

        transformations.add(CenterCrop())

        if (roundedCorners) {
            transformations.add(RoundedCorners(AppearancePreferences.getCornerRadius().toInt()))
        }

        if (blur) {
            transformations.add(Padding(BlurShadow.DEFAULT_SHADOW_SIZE.toInt()))
            transformations.add(
                    BlurShadow(this.context)
                        .setElevation(25F)
                        .setBlurRadius(BlurShadow.DEFAULT_SHADOW_SIZE)
            )
        }

        var glideRequest = Glide.with(this)
            .asBitmap()
            .dontTransform()
            .transform(*transformations.toTypedArray())
            .load(genre)
            .error(R.drawable.ic_felicity)
            .transition(BitmapTransitionOptions.withCrossFade())

        if (skipCache) {
            glideRequest = glideRequest
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
        }

        glideRequest.into(this)
    }
}