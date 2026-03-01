package app.simple.felicity.glide.yearcover

import android.graphics.Bitmap
import app.simple.felicity.repository.models.YearGroup
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey

class YearCoverLoader : ModelLoader<YearGroup, Bitmap> {
    override fun buildLoadData(model: YearGroup, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap?>? {
        return ModelLoader.LoadData(ObjectKey(model), YearCoverFetcher(model))
    }

    override fun handles(model: YearGroup): Boolean = true

    internal class Factory : ModelLoaderFactory<YearGroup, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<YearGroup, Bitmap> {
            return YearCoverLoader()
        }

        override fun teardown() {}
    }
}

