package app.simple.felicity.glide.albumcover

import android.widget.ImageView
import app.simple.felicity.glide.transformation.BlurShadow
import app.simple.felicity.glide.transformation.Padding
import app.simple.felicity.glide.transformation.RoundedCorners
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.repository.models.Album
import com.bumptech.glide.Glide
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter

object AlbumCoverUtils {
    fun ImageView.loadAlbumCover(
            album: Album,
            roundedCorners: Boolean = true,
            blur: Boolean = true,
            skipCache: Boolean = false,
            crop: Boolean = true
    ) {
        val transformations = mutableListOf<Transformation<android.graphics.Bitmap>>()

        if (crop) {
            transformations.add(CenterCrop())
        } else {
            transformations.add(FitCenter())
        }

        if (roundedCorners) {
            transformations.add(RoundedCorners(AppearancePreferences.getCornerRadius().toInt(), 0))
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
            .dontTransform() // This way we can apply our own transformations and skip the module specific ones
            .transform(*transformations.toTypedArray())
            .load(album)

        if (skipCache) {
            glideRequest = glideRequest
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
        }

        glideRequest.into(this)
    }
}
