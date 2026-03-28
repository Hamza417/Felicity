package app.simple.felicity.glide.yearcover

import android.content.Context
import android.graphics.Bitmap
import app.simple.felicity.repository.models.YearGroup
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey

/**
 * Glide [ModelLoader] for [YearGroup] → [Bitmap] that wires a [Context] into
 * [YearCoverFetcher] so it can query the MediaStore album art cache.
 *
 * @author Hamza417
 */
class YearCoverLoader(private val context: Context) : ModelLoader<YearGroup, Bitmap> {
    override fun buildLoadData(model: YearGroup, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap?>? {
        return ModelLoader.LoadData(ObjectKey(model), YearCoverFetcher(context, model))
    }

    override fun handles(model: YearGroup): Boolean = true

    internal class Factory(private val context: Context) : ModelLoaderFactory<YearGroup, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<YearGroup, Bitmap> {
            return YearCoverLoader(context)
        }

        override fun teardown() {}
    }
}
