package app.simple.felicity.glide.genres

import android.widget.ImageView
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
    fun ImageView.loadGenreCover(genre: Genre) {
        Glide.with(this)
            .asBitmap()
            .load(GenreCoverModel(this.context, genre.id, genre.name ?: ""))
            .transform(
                    CenterCrop(),
                    RoundedCorners(AppearancePreferences.getCornerRadius().toInt().times(2)),
                    Padding(BlurShadow.DEFAULT_SHADOW_SIZE.toInt()),
                    BlurShadow(this.context)
                        .setElevation(25F)
                        .setBlurRadius(BlurShadow.DEFAULT_SHADOW_SIZE)
            )
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .transition(BitmapTransitionOptions.withCrossFade())
            .into(this)
    }
}