package app.simple.felicity.glide.songcover

import android.content.Context
import android.graphics.Bitmap
import app.simple.felicity.repository.models.Song
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey

class SongCoverLoader(private val context: Context) : ModelLoader<Song, Bitmap> {

    override fun buildLoadData(model: Song, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap> {
        return ModelLoader.LoadData(ObjectKey(model), SongCoverFetcher(context, model))
    }

    fun getResourceFetcher(model: Song): DataFetcher<Bitmap> {
        return SongCoverFetcher(context, model)
    }

    override fun handles(model: Song): Boolean {
        return true
    }

    internal class Factory(private val context: Context) : ModelLoaderFactory<Song, Bitmap> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Song, Bitmap> {
            return SongCoverLoader(context)
        }

        override fun teardown() {}
    }
}