package app.simple.felicity.glide.foldercover

import android.graphics.Bitmap
import app.simple.felicity.repository.models.Folder
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey

class FolderCoverLoader : ModelLoader<Folder, Bitmap> {
    override fun buildLoadData(model: Folder, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap?>? {
        return ModelLoader.LoadData(ObjectKey(model), FolderCoverFetcher(model))
    }

    override fun handles(model: Folder): Boolean = true

    internal class Factory : ModelLoaderFactory<Folder, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Folder, Bitmap> {
            return FolderCoverLoader()
        }

        override fun teardown() {}
    }
}

