package app.simple.felicity.glide.foldercover

import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.repository.covers.FolderCover
import app.simple.felicity.repository.models.Folder
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.io.FileNotFoundException

class FolderCoverFetcher internal constructor(
        private val folder: Folder
) : DataFetcher<Bitmap> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        try {
            val bitmap = FolderCover.load(folder)
                ?: throw FileNotFoundException("Could not find artwork for folder: ${folder.name}")
            callback.onDataReady(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading folder cover for: ${folder.name}", e)
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() {}

    override fun cancel() {}

    override fun getDataClass(): Class<Bitmap> = Bitmap::class.java

    override fun getDataSource(): DataSource = DataSource.LOCAL

    companion object {
        private const val TAG = "FolderCoverFetcher"
    }
}

