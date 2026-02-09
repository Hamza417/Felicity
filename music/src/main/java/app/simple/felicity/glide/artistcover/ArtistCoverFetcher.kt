package app.simple.felicity.glide.artistcover

import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.repository.covers.ArtistCover
import app.simple.felicity.repository.models.Artist
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.io.FileNotFoundException

/**
 * Glide DataFetcher for loading artist cover artwork.
 * Delegates to ArtistCover.load() for centralized artist cover loading logic.
 */
class ArtistCoverFetcher internal constructor(
        private val artist: Artist
) : DataFetcher<Bitmap> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        try {
            // Delegate to ArtistCover for centralized artist cover loading
            val bitmap = ArtistCover.load(artist)
                ?: throw FileNotFoundException("Could not find artist artwork for: ${artist.name}")

            callback.onDataReady(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading artist cover for: ${artist.name}", e)
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() {
        // No cleanup needed - ArtistUtils handles resource management
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
        private const val TAG = "ArtistCoverFetcher"
    }
}
