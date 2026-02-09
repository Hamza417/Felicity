package app.simple.felicity.glide.albumcover

import android.content.Context
import android.graphics.Bitmap
import app.simple.felicity.repository.models.Album
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey

class AlbumCoverLoader() : ModelLoader<Album, Bitmap> {
    override fun buildLoadData(album: Album, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap> {
        return ModelLoader.LoadData(ObjectKey(album), AlbumCoverFetcher(album))
    }

    fun getResourceFetcher(model: Album): DataFetcher<Bitmap> {
        return AlbumCoverFetcher(model)
    }

    override fun handles(model: Album): Boolean {
        return true
    }

    internal class Factory(private val context: Context) : ModelLoaderFactory<Album, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Album, Bitmap> {
            return AlbumCoverLoader()
        }

        override fun teardown() {}
    }
}
