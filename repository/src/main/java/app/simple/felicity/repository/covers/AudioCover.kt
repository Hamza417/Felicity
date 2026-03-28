package app.simple.felicity.repository.covers

import android.content.Context
import android.graphics.Bitmap
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.covers.MediaStoreCover.loadCoverFromMediaStore
import app.simple.felicity.repository.covers.MediaStoreCover.uriToBitmap
import app.simple.felicity.repository.models.Audio
import java.io.File

/**
 * Cover art loader for individual Audio (Song) files.
 *
 * @author Hamza417
 */
object AudioCover {
    private const val TAG = "AudioCover"

    /**
     * Loads audio cover bitmap from multiple sources in order of preference:
     * 1. MediaStore album art cache (when [LibraryPreferences.isUseMediaStoreArtwork] is true)
     * 2. External image files in the audio directory (folder.jpg, cover.jpg, etc.)
     * 3. Embedded artwork extracted from the audio file via [android.media.MediaMetadataRetriever]
     *
     * MediaStore is tried first when the preference is enabled because it reads from the system's
     * pre-indexed artwork cache, which avoids both file-system traversal and full tag parsing.
     * If MediaStore returns nothing the loader falls through to the file-based sources so no
     * artwork is silently lost.
     *
     * @param context Android context used for MediaStore queries
     * @param audio Audio model whose path is used to resolve artwork
     * @return Bitmap of audio cover, or null if no artwork is found
     */
    fun load(context: Context, audio: Audio): Bitmap? {
        val audioPath = audio.path ?: return null

        if (LibraryPreferences.isUseMediaStoreArtwork()) {
            // Primary: resolve from MediaStore's pre-indexed album art cache (fastest path).
            val uri = context.loadCoverFromMediaStore(audioPath)
            val mediaStoreBitmap = uri?.let { context.uriToBitmap(it) }
            if (mediaStoreBitmap != null) return mediaStoreBitmap
            // Fall through to file-based sources if MediaStore yielded nothing.
        }

        // Source 1: External image files in the audio directory (fast — only File.exists checks).
        val directory = File(audioPath).parentFile
        if (directory != null && directory.exists()) {
            val customNames = BaseCoverLoader.generateCustomArtworkNames(audio.album)
            val externalArtwork = BaseCoverLoader.loadExternalArtwork(directory, customNames)
            if (externalArtwork != null) return externalArtwork
        }

        // Source 2: Artwork embedded in the audio file tags (slower — full tag parse).
        val embeddedArtwork = BaseCoverLoader.loadEmbeddedArtwork(audioPath)
        if (embeddedArtwork != null) return embeddedArtwork

        return BaseCoverLoader.loadEmptyAudioCover()
    }
}
