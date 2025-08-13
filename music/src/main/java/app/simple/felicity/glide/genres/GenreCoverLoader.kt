package app.simple.felicity.glide.genres

import android.content.Context
import android.graphics.Bitmap
import app.simple.felicity.repository.models.Genre
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey

class GenreCoverLoader(private val context: Context) : ModelLoader<Genre, Bitmap> {
    override fun buildLoadData(model: Genre, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap?>? {
        return ModelLoader.LoadData(ObjectKey(model), GenreCoverFetcher(context, model))
    }

    override fun handles(model: Genre): Boolean {
        return true
    }

    internal class Factory(private val context: Context) : ModelLoaderFactory<Genre, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Genre, Bitmap> {
            return GenreCoverLoader(context)
        }

        override fun teardown() {}
    }
}