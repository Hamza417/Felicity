package app.simple.felicity.glide.artistcover

import android.graphics.Bitmap
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey

class ArtistCoverLoader : ModelLoader<ArtistCoverModel, Bitmap> {
    override fun buildLoadData(artistCoverModel: ArtistCoverModel, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap> {
        return ModelLoader.LoadData(ObjectKey(artistCoverModel), ArtistCoverFetcher(artistCoverModel))
    }

    fun getResourceFetcher(model: ArtistCoverModel): DataFetcher<Bitmap> {
        return ArtistCoverFetcher(model)
    }

    override fun handles(model: ArtistCoverModel): Boolean {
        return true
    }

    internal class Factory : ModelLoaderFactory<ArtistCoverModel, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<ArtistCoverModel, Bitmap> {
            return ArtistCoverLoader()
        }

        override fun teardown() {}
    }
}
