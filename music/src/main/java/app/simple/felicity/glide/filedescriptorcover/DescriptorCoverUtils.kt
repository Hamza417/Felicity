package app.simple.felicity.glide.filedescriptorcover

import android.net.Uri
import android.widget.ImageView
import app.simple.felicity.R
import app.simple.felicity.glide.transformation.BlurShadow
import app.simple.felicity.glide.transformation.Padding
import app.simple.felicity.preferences.AppearancePreferences
import com.bumptech.glide.Glide
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners

object DescriptorCoverUtils {

    /**
     * Load original artwork directly from file using file descriptor.
     */
    fun ImageView.loadFromDescriptor(
            uri: Uri,
            roundedCorners: Boolean = true,
            blur: Boolean = true,
            skipCache: Boolean = false
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

        var glideRequest = Glide.with(this)
            .asBitmap()
            .dontTransform() // This way we can apply our own transformations and skip the module specific ones
            .transform(*transformations.toTypedArray())
            .load(DescriptorCoverModel(this.context, uri))
            .error(R.drawable.ic_felicity)

        if (skipCache) {
            glideRequest = glideRequest
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
        }

        glideRequest.into(this)
    }
}