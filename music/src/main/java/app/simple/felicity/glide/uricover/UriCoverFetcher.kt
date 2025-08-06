package app.simple.inure.glide.uricover

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import app.simple.felicity.glide.uricover.UriCoverModel
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.io.FileNotFoundException

class UriCoverFetcher internal constructor(private val uriCoverModel: UriCoverModel) : DataFetcher<Bitmap> {
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            uriCoverModel.context.contentResolver.loadThumbnail(uriCoverModel.artUri, android.util.Size(512, 512), null)
        } else {
            uriCoverModel.context.contentResolver.openFileDescriptor(uriCoverModel.artUri, "r")?.use { pfd ->
                BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
            } ?: throw FileNotFoundException("Could not open file descriptor for URI: ${uriCoverModel.artUri}")
        }

        callback.onDataReady(bitmap)
    }

    override fun cleanup() {
        // Cleared
    }

    override fun cancel() {
        // Probably already cleared
    }

    override fun getDataClass(): Class<Bitmap> {
        return Bitmap::class.java
    }

    override fun getDataSource(): DataSource {
        return DataSource.LOCAL
    }
}
