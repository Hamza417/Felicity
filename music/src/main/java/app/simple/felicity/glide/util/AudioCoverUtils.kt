package app.simple.felicity.glide.util

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import app.simple.felicity.R
import app.simple.felicity.glide.transformation.Blur
import app.simple.felicity.glide.transformation.BlurShadow
import app.simple.felicity.glide.transformation.Darken
import app.simple.felicity.glide.transformation.Greyscale
import app.simple.felicity.glide.transformation.Padding
import app.simple.felicity.glide.transformation.RoundedCorners
import app.simple.felicity.preferences.AlbumArtPreferences
import app.simple.felicity.preferences.AppearancePreferences
import com.bumptech.glide.Glide
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

object AudioCoverUtils {
    fun ImageView.loadArtCover(
            item: Any,
            shadow: Boolean = false,
            roundedCorners: Boolean = false,
            blur: Boolean = false,
            greyscale: Boolean = false,
            darken: Boolean = false,
            crop: Boolean = true
    ) {
        val transformations = mutableListOf<Transformation<Bitmap>>()

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

        val glideRequest = Glide.with(this)
            .asBitmap()
            .dontTransform() // This way we can apply our own transformations and skip the module specific ones
            .transform(*transformations.toTypedArray())
            .load(item)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .error(R.drawable.ic_felicity)

        glideRequest.into(this)
    }

    fun ImageView.loadArtCoverWithPayload(item: Any) {
        loadArtCover(
                item = item,
                shadow = AlbumArtPreferences.isShadowEnabled(),
                blur = false,
                greyscale = AlbumArtPreferences.isGreyscaleEnabled(),
                darken = false,
                crop = AlbumArtPreferences.isCropEnabled(),
                roundedCorners = AlbumArtPreferences.isRoundedCornersEnabled())
    }

    fun ImageView.loadPlainArtCover(item: Any) {
        Glide.with(this)
            .asBitmap()
            .dontTransform()
            .dontAnimate()
            .transform(CenterCrop())
            .load(item)
            .into(this)
    }

    fun Context.getArtCover(
            item: Any,
            size: Int,
            shadow: Boolean = false,
            roundedCorners: Boolean = false,
            blur: Boolean = false,
            greyscale: Boolean = false,
            darken: Boolean = false,
            crop: Boolean = true
    ): Bitmap {
        val transformations = mutableListOf<Transformation<Bitmap>>()

        if (crop) transformations.add(CenterCrop())
        if (roundedCorners) transformations.add(RoundedCorners(AppearancePreferences.getCornerRadius().toInt()))
        if (shadow) {
            transformations.add(Padding(BlurShadow.DEFAULT_SHADOW_SIZE.toInt()))

            transformations.add(
                    BlurShadow(this)
                        .setElevation(25F)
                        .setBlurRadius(BlurShadow.DEFAULT_SHADOW_SIZE)
            )
        }
        if (blur) transformations.add(Blur(72))
        if (greyscale) transformations.add(Greyscale())
        if (darken) transformations.add(Darken(0.3F))

        val glideRequest = Glide.with(this)
            .asBitmap()
            .dontTransform() // This way we can apply our own transformations and skip the module specific ones
            .transform(*transformations.toTypedArray())
            .load(item)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .error(R.drawable.ic_felicity)

        return glideRequest.submit(size, size).get()
    }

    /**
     * Loads album art for [item] into a plain [Bitmap] delivered via [onBitmap].
     * Designed for canvas-drawn views such as [MiniPlayerView] that do not hold
     * an [ImageView] reference.  Cancellation is caller-managed — call
     * [Glide.with(context).clear(target)] with the returned target if needed;
     * here we simply pass `null` on clear.
     */
    fun Context.loadArtIntoBitmap(item: Any, onBitmap: (Bitmap?) -> Unit) {
        Glide.with(this)
            .asBitmap()
            .dontTransform()
            .dontAnimate()
            .transform(CenterCrop())
            .load(item)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    onBitmap(resource)
                }

                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                    onBitmap(null)
                }

                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    onBitmap(null)
                }
            })
    }
}