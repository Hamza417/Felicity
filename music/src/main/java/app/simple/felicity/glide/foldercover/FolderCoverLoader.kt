package app.simple.felicity.glide.foldercover

import android.content.Context
import android.graphics.Bitmap
import app.simple.felicity.repository.models.Folder
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey

/**
 * Glide [ModelLoader] for [Folder] → [Bitmap] that wires a [Context] into
 * [FolderCoverFetcher] so it can query the MediaStore album art cache.
 *
 * @author Hamza417
 */
class FolderCoverLoader(private val context: Context) : ModelLoader<Folder, Bitmap> {
    override fun buildLoadData(model: Folder, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap?>? {
        return ModelLoader.LoadData(ObjectKey(model), FolderCoverFetcher(context, model))
    }

    override fun handles(model: Folder): Boolean = true

    internal class Factory(private val context: Context) : ModelLoaderFactory<Folder, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Folder, Bitmap> {
            return FolderCoverLoader(context)
        }

        override fun teardown() {}
    }
}
