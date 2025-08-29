package app.simple.felicity.glide.artistcover

import android.widget.ImageView
import app.simple.felicity.R
import app.simple.felicity.glide.transformation.BlurShadow
import app.simple.felicity.glide.transformation.Darken
import app.simple.felicity.glide.transformation.Padding
import app.simple.felicity.glide.transformation.RoundedCorners
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.repository.models.Artist
import com.bumptech.glide.Glide
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop

object ArtistCoverUtils {
    fun ImageView.loadPeristyleArtistCover(artist: Artist) {
        Glide.with(this)
            .asBitmap()
            .dontTransform()
            .dontAnimate()
            .transform(CenterCrop())
            .load(artist)
            .into(this)
    }

    fun ImageView.loadArtistCover(
            artist: Artist,
            roundedCorners: Boolean = true,
            blur: Boolean = true,
            skipCache: Boolean = false,
            darken: Boolean = false
    ) {
        val transformations = mutableListOf<Transformation<android.graphics.Bitmap>>()

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

        if (darken) {
            transformations.add(Darken(0.6F))
        }

        var glideRequest = Glide.with(this)
            .asBitmap()
            .dontTransform() // This way we can apply our own transformations and skip the module specific ones
            .transform(*transformations.toTypedArray())
            .load(artist)
            .error(R.drawable.ic_felicity)

        if (skipCache) {
            glideRequest = glideRequest
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
        }

        glideRequest.into(this)
    }
}
