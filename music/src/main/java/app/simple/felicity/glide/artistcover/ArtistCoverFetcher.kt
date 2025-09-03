package app.simple.felicity.glide.artistcover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Size
import app.simple.felicity.glide.util.Constants
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.utils.ArtistUtils
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.io.FileNotFoundException

class ArtistCoverFetcher internal constructor(private val context: Context, private val artist: Artist) : DataFetcher<Bitmap> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        val uri = ArtistUtils.getArtistArtworkUri(context, artist.id)
            ?: throw FileNotFoundException("Could not find artwork URI for artist: $artist")

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
