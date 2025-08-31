package app.simple.felicity.glide.songcover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Size
import app.simple.felicity.R
import app.simple.felicity.core.helpers.ImageHelper.toBitmap
import app.simple.felicity.glide.artistcover.Constants
import app.simple.felicity.repository.models.Song
import app.simple.felicity.repository.utils.SongUtils
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.io.FileNotFoundException

class SongCoverFetcher internal constructor(private val context: Context, private val song: Song) : DataFetcher<Bitmap> {
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        val bitmap: Bitmap = try {
            val uri = SongUtils.getArtworkUri(context, song.albumId, song.id)
                ?: throw FileNotFoundException("Could not find artwork URI for song: $song")

            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(Constants.ART_SIZE, Constants.ART_SIZE), null)
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
                }
            }) ?: R.drawable.ic_felicity_full_art.toBitmap(context)
        } catch (_: FileNotFoundException) {
            R.drawable.ic_felicity_full_art.toBitmap(context)
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
