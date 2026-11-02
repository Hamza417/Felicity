package app.simple.felicity.glide.mixcover

import android.content.Context
import android.graphics.Bitmap
import app.simple.felicity.viewmodels.panels.MixesViewModel.Mix
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey

/**
 * Glide [ModelLoader] for [Mix] → [Bitmap] that generates an organic Voronoi collage.
 */
class MixCoverLoader(private val context: Context) : ModelLoader<Mix, Bitmap> {

    override fun buildLoadData(mix: Mix, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap> {
        // Fallback to reasonable bounds if width/height are Target.SIZE_ORIGINAL
        val width = if (width == Target.SIZE_ORIGINAL) 600 else width
        val height = if (height == Target.SIZE_ORIGINAL) 600 else height

        return ModelLoader.LoadData(ObjectKey(mix), MixCoverFetcher(context, mix, width, height))
    }

    override fun handles(model: Mix): Boolean {
        return model.songs.isNotEmpty()
    }

    internal class Factory(private val context: Context) : ModelLoaderFactory<Mix, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Mix, Bitmap> {
            return MixCoverLoader(context)
        }

        override fun teardown() {}
    }
}
