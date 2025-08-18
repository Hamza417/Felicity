package app.simple.felicity.glide.songcover

import android.widget.ImageView
import app.simple.felicity.R
import app.simple.felicity.glide.transformation.Blur
import app.simple.felicity.glide.transformation.BlurShadow
import app.simple.felicity.glide.transformation.Darken
import app.simple.felicity.glide.transformation.Greyscale
import app.simple.felicity.glide.transformation.Padding
import app.simple.felicity.glide.transformation.RoundedCorners
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.repository.models.Song
import com.bumptech.glide.Glide
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop

object SongCoverUtils {
    fun ImageView.loadSongCover(
            song: Song,
            roundedCorners: Boolean = true,
            blurShadow: Boolean = true,
            blur: Boolean = false,
            skipCache: Boolean = false,
            greyscale: Boolean = false,
            darken: Boolean = false
    ) {
        val transformations = mutableListOf<Transformation<android.graphics.Bitmap>>()

        if (roundedCorners) {
            transformations.add(RoundedCorners(AppearancePreferences.getCornerRadius().toInt(), 0))
        }

        if (blurShadow) {
            transformations.add(Padding(BlurShadow.DEFAULT_SHADOW_SIZE.toInt()))

            transformations.add(
                    BlurShadow(this.context)
                        .setElevation(25F)
                        .setBlurRadius(BlurShadow.DEFAULT_SHADOW_SIZE)
            )
        }

        if (blur) {
            transformations.add(Blur())
        }

        if (greyscale) {
            transformations.add(Greyscale())
        }

        if (darken) {
            transformations.add(Darken(0.6F))
        }

        var glideRequest = Glide.with(this)
            .asBitmap()
            .dontTransform() // This way we can apply our own transformations and skip the module specific ones
            .transform(*transformations.toTypedArray())
            .load(song)
            .error(R.drawable.ic_felicity)

        if (skipCache) {
            glideRequest = glideRequest
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
        }

        glideRequest.into(this)
    }

    fun ImageView.loadBlurredBWSongCover(song: Song) {
        Glide.with(this)
            .asBitmap()
            .load(song)
            .transform(
                    CenterCrop(),
                    Blur(),
                    Darken(0.4F)
            )
            .into(this)
    }
}