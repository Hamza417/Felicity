package app.simple.felicity.glide.artistcover

import android.content.Context
import android.graphics.Bitmap
import app.simple.felicity.repository.models.Artist
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey

class ArtistCoverLoader(private val context: Context) : ModelLoader<Artist, Bitmap> {
    override fun buildLoadData(model: Artist, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap> {
        return ModelLoader.LoadData(ObjectKey(model), ArtistCoverFetcher(model))
    }

    fun getResourceFetcher(model: Artist): DataFetcher<Bitmap> {
        return ArtistCoverFetcher(model)
    }

    override fun handles(model: Artist): Boolean {
        return true
    }

    internal class Factory(private val context: Context) : ModelLoaderFactory<Artist, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Artist, Bitmap> {
            return ArtistCoverLoader(context)
        }

        override fun teardown() {}
    }
}
