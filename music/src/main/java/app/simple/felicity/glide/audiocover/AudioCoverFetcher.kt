package app.simple.felicity.glide.audiocover

import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.repository.covers.AudioCover
import app.simple.felicity.repository.models.Audio
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.io.FileNotFoundException

/**
 * Glide DataFetcher for loading audio cover artwork.
 * Delegates to AudioCover.load() for centralized audio cover loading logic.
 */
class AudioCoverFetcher internal constructor(
        private val audio: Audio
) : DataFetcher<Bitmap> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        try {
            // Delegate to AudioCover for centralized audio cover loading
            val bitmap = AudioCover.load(audio)
                ?: throw FileNotFoundException("Could not find audio artwork for: ${audio.title}")

            callback.onDataReady(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading audio cover for: ${audio.title}", e)
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() {
        // No cleanup needed - AudioUtils handles resource management
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
        private const val TAG = "AudioCoverFetcher"
    }
}