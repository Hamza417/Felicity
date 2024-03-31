package app.simple.felicity.glide.pathcover

import android.graphics.Bitmap
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey

class PathCoverLoader : ModelLoader<PathCoverModel, Bitmap> {
    override fun buildLoadData(pathCoverModel: PathCoverModel, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap> {
        return ModelLoader.LoadData(ObjectKey(pathCoverModel), PathCoverFetcher(pathCoverModel))
    }

    fun getResourceFetcher(model: PathCoverModel): DataFetcher<Bitmap> {
        return PathCoverFetcher(model)
    }

    override fun handles(model: PathCoverModel): Boolean {
        return true
    }

    internal class Factory : ModelLoaderFactory<PathCoverModel, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<PathCoverModel, Bitmap> {
            return PathCoverLoader()
        }

        override fun teardown() {}
    }
}
