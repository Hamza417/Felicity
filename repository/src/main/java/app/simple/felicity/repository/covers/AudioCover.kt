package app.simple.felicity.repository.covers

import android.content.Context
import android.graphics.Bitmap
import androidx.core.net.toUri
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.covers.MediaStoreCover.loadCoverFromMediaStore
import app.simple.felicity.repository.covers.MediaStoreCover.uriToBitmap
import app.simple.felicity.repository.models.Audio
import java.io.File

/**
 * Cover art loader for individual Audio (Song) files.
 *
 * Handles both regular file paths and SAF content URIs transparently —
 * the caller doesn't need to know which one it's dealing with.
 *
 * @author Hamza417
 */
object AudioCover {
    private const val TAG = "AudioCover"

    /**
     * Loads audio cover bitmap from multiple sources in order of preference:
     * 1. MediaStore album art cache (when [LibraryPreferences.isUseMediaStoreArtwork] is true)
     * 2. External image files in the audio directory (only for file paths, not SAF URIs)
     * 3. Embedded artwork extracted from the audio file via [android.media.MediaMetadataRetriever]
     *
     * For SAF content URIs, external directory art is skipped since we can't navigate
     * parent folders via a content URI — but embedded art works perfectly fine.
     *
     * @param context Android context used for MediaStore queries and SAF URI resolution.
     * @param audio Audio model whose path is used to resolve artwork.
     * @return Bitmap of audio cover, or null if no artwork is found.
     */
    fun load(context: Context, audio: Audio): Bitmap? {
        val audioPath = audio.path ?: return null
        val isSAFPath = audioPath.startsWith("content://")

        if (LibraryPreferences.isUseMediaStoreArtwork()) {
            // Primary: resolve from MediaStore's pre-indexed album art cache (fastest path).
            val uri = context.loadCoverFromMediaStore(audioPath)
            val mediaStoreBitmap = uri?.let { context.uriToBitmap(it) }
            if (mediaStoreBitmap != null) return mediaStoreBitmap
        }

        if (!isSAFPath) {
            // Source 1: External image files in the audio directory (fast — only File.exists checks).
            // Only works for regular file paths — we skip this step for SAF URIs because
            // you can't navigate to a parent directory from a content URI.
            val directory = File(audioPath).parentFile
            if (directory != null && directory.exists()) {
                val customNames = BaseCoverLoader.generateCustomArtworkNames(audio.album)
                val externalArtwork = BaseCoverLoader.loadExternalArtwork(directory, customNames)
                if (externalArtwork != null) return externalArtwork
            }

            // Source 2: Embedded artwork via file path (slower — full tag parse).
            val embeddedArtwork = BaseCoverLoader.loadEmbeddedArtwork(audioPath)
            if (embeddedArtwork != null) return embeddedArtwork
        } else {
            // SAF path — use the context-aware embedded artwork extractor.
            val embeddedArtwork = BaseCoverLoader.loadEmbeddedArtwork(context, audioPath.toUri())
            if (embeddedArtwork != null) return embeddedArtwork
        }

        return BaseCoverLoader.loadEmptyAudioCover()
    }
}


