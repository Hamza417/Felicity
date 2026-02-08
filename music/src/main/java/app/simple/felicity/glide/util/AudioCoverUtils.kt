package app.simple.felicity.glide.util

import android.widget.ImageView
import app.simple.felicity.R
import app.simple.felicity.glide.pathcover.PathCoverModel
import app.simple.felicity.glide.transformation.Blur
import app.simple.felicity.glide.transformation.BlurShadow
import app.simple.felicity.glide.transformation.Darken
import app.simple.felicity.glide.transformation.Greyscale
import app.simple.felicity.glide.transformation.Padding
import app.simple.felicity.glide.transformation.RoundedCorners
import app.simple.felicity.preferences.AlbumArtPreferences
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.repository.models.Audio
import com.bumptech.glide.Glide
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop

object AudioCoverUtils {
    fun ImageView.loadArtCover(
            item: Any,
            shadow: Boolean = false,
            roundedCorners: Boolean = false,
            blur: Boolean = false,
            skipCache: Boolean = false,
            greyscale: Boolean = false,
            darken: Boolean = false,
            crop: Boolean = true
    ) {
        val transformations = mutableListOf<Transformation<android.graphics.Bitmap>>()

        if (crop) transformations.add(CenterCrop())
        if (roundedCorners) transformations.add(RoundedCorners(AppearancePreferences.getCornerRadius().toInt()))
        if (shadow) {
            transformations.add(Padding(BlurShadow.DEFAULT_SHADOW_SIZE.toInt()))

            transformations.add(
                    BlurShadow(this.context)
                        .setElevation(25F)
                        .setBlurRadius(BlurShadow.DEFAULT_SHADOW_SIZE)
            )
        }
        if (blur) transformations.add(Blur(72))
        if (greyscale) transformations.add(Greyscale())
        if (darken) transformations.add(Darken(0.3F))

        val glideRequest = if (item is Audio) {
            Glide.with(this)
                .asBitmap()
                .dontTransform() // This way we can apply our own transformations and skip the module specific ones
                .transform(*transformations.toTypedArray())
                .load(PathCoverModel(this.context, item.path))
                .error(R.drawable.ic_felicity)
        } else {
            Glide.with(this)
                .asBitmap()
                .dontTransform() // This way we can apply our own transformations and skip the module specific ones
                .transform(*transformations.toTypedArray())
                .load(item)
                .error(R.drawable.ic_felicity)
        }

        val finalRequest = if (skipCache) {
            glideRequest.skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE)
        } else {
            glideRequest
        }

        finalRequest.into(this)
    }

    fun ImageView.loadArtCoverWithPayload(item: Any) {
        loadArtCover(
                item = item,
                shadow = AlbumArtPreferences.isShadowEnabled(),
                blur = false,
                skipCache = true,
                greyscale = AlbumArtPreferences.isGreyscaleEnabled(),
                darken = false,
                crop = AlbumArtPreferences.isCropEnabled(),
                roundedCorners = AlbumArtPreferences.isRoundedCornersEnabled())
    }

    fun ImageView.loadPeristyleArtCover(item: Any) {
        Glide.with(this)
            .asBitmap()
            .dontTransform()
            .dontAnimate()
            .transform(CenterCrop())
            .load(item)
            .into(this)
    }
}