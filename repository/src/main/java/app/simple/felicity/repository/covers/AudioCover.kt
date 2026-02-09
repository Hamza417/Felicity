package app.simple.felicity.repository.covers

import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.repository.models.Audio
import java.io.File

/**
 * Cover art loader for individual Audio (Song) files.
 */
object AudioCover {
    private const val TAG = "AudioCover"
    private const val ALBUM_ART_PATH = "/album_art.png"

    /**
     * Loads audio cover bitmap from multiple sources in order of efficiency:
     * 1. External image files in audio directory (folder.jpg, cover.jpg, etc.)
     * 2. Embedded artwork in audio file (using MediaMetadataRetriever)
     *
     * @param audio Audio model with path
     * @return Bitmap of audio cover or null if not found
     */
    fun load(audio: Audio): Bitmap? {
        val audioPath = audio.path ?: return null

        // Source 1: Check for external album art files (fastest)
        val directory = File(audioPath).parentFile

        if (directory != null && directory.exists()) {
            val customNames = CoverLoader.generateCustomArtworkNames(audio.album)
            val externalArtwork = CoverLoader.loadExternalArtwork(directory, customNames)

            if (externalArtwork != null) {
                Log.d(TAG, "Loaded audio art from external file for: ${audio.title}")
                return externalArtwork
            }
        }

        // Source 2: Extract from audio file metadata (embedded artwork)
        val embeddedArtwork = CoverLoader.loadEmbeddedArtwork(audioPath)
        if (embeddedArtwork != null) {
            Log.d(TAG, "Loaded audio art from embedded metadata for: ${audio.title}")
            return embeddedArtwork
        }

        // If no artwork found, return null or a default placeholder
        return CoverLoader.loadEmptyAudioCover()
    }

    /**
     * Loads a default placeholder cover if no artwork is found.
     *
     * @return Default bitmap or null
     */
    private fun loadDefaultCover(): Bitmap? {
        // Note: This would need to be implemented based on app resources
        // Currently returns null to maintain consistency with existing behavior
        return null
    }
}

