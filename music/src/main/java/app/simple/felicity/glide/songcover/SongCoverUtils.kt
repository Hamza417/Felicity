package app.simple.felicity.glide.songcover

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import app.simple.felicity.glide.transformation.Blur
import app.simple.felicity.glide.transformation.Darken
import app.simple.felicity.repository.models.Song
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import java.io.FileNotFoundException

object SongCoverUtils {
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

    fun Song.fetchBitmap(context: Context): Bitmap? {
        try {
            return Glide.with(context)
                .asBitmap()
                .load(this)
                .submit()
                .get()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            return null
        } catch (e: GlideException) {
            e.printStackTrace()
            return null
        }
    }
}