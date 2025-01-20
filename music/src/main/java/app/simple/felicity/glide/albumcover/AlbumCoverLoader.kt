package app.simple.felicity.glide.albumcover

import android.graphics.Bitmap
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey

class AlbumCoverLoader : ModelLoader<AlbumCoverModel, Bitmap> {
    override fun buildLoadData(albumCoverModel: AlbumCoverModel, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap> {
        return ModelLoader.LoadData(ObjectKey(albumCoverModel), AlbumCoverFetcher(albumCoverModel))
    }

    fun getResourceFetcher(model: AlbumCoverModel): DataFetcher<Bitmap> {
        return AlbumCoverFetcher(model)
    }

    override fun handles(model: AlbumCoverModel): Boolean {
        return true
    }

    internal class Factory : ModelLoaderFactory<AlbumCoverModel, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<AlbumCoverModel, Bitmap> {
            return AlbumCoverLoader()
        }

        override fun teardown() {}
    }
}
