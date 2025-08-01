package app.simple.felicity.glide.genres

import android.graphics.Bitmap
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey

class GenreCoverLoader : ModelLoader<GenreCoverModel, Bitmap> {
    override fun buildLoadData(model: GenreCoverModel, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap?>? {
        return ModelLoader.LoadData(ObjectKey(model), GenreCoverFetcher(model))
    }

    override fun handles(model: GenreCoverModel): Boolean {
        return true
    }

    internal class Factory : ModelLoaderFactory<GenreCoverModel, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<GenreCoverModel, Bitmap> {
            return GenreCoverLoader()
        }

        override fun teardown() {}
    }
}