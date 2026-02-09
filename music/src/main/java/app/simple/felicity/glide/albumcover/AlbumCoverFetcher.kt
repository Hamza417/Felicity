package app.simple.felicity.glide.albumcover

import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.utils.AlbumUtils
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.io.FileNotFoundException

/**
 * Glide DataFetcher for loading album cover artwork.
 * Delegates to AlbumUtils.loadAlbumCover() for centralized album cover loading logic.
 */
class AlbumCoverFetcher internal constructor(
        private val album: Album
) : DataFetcher<Bitmap> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        try {
            // Delegate to AlbumUtils for centralized album cover loading
            val bitmap = AlbumUtils.loadAlbumCover(album)
                ?: throw FileNotFoundException("Could not find album artwork for: ${album.name}")

            callback.onDataReady(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading album cover for: ${album.name}", e)
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() {
        // No cleanup needed - AlbumUtils handles resource management
    }

    override fun cancel() {
        // No cancellation needed
    }

    override fun getDataClass(): Class<Bitmap> {
        return Bitmap::class.java
    }

    override fun getDataSource(): DataSource {
        return DataSource.LOCAL
    }

    companion object {
        private const val TAG = "AlbumCoverFetcher"
    }
}
