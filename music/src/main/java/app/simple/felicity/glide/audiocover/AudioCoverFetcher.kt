package app.simple.felicity.glide.audiocover

import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.utils.AudioUtils
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.io.FileNotFoundException

/**
 * Glide DataFetcher for loading audio cover artwork.
 * Delegates to AudioUtils.loadAudioCover() for centralized audio cover loading logic.
 */
class AudioCoverFetcher internal constructor(
        private val audio: Audio
) : DataFetcher<Bitmap> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        try {
            // Delegate to AudioUtils for centralized audio cover loading
            val bitmap = AudioUtils.loadAudioCover(audio)
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