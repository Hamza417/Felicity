package app.simple.felicity.glide.albumcover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Size
import app.simple.felicity.glide.util.Constants
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.utils.AlbumUtils
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.io.FileNotFoundException

class AlbumCoverFetcher internal constructor(private val context: Context, private val album: Album) : DataFetcher<Bitmap> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        val uri = AlbumUtils.getAlbumCover(context, album.id)
            ?: throw FileNotFoundException("Could not find artwork URI for album: $album")

        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(Constants.ART_SIZE, Constants.ART_SIZE), null)
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
            } ?: throw FileNotFoundException("Could not open file descriptor for URI: $uri")
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
