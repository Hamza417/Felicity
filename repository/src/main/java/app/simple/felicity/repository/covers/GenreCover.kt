package app.simple.felicity.repository.covers

import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.repository.models.Genre
import java.io.File

/**
 * Cover art loader for Genre collections.
 */
object GenreCover {
    private const val TAG = "GenreCover"

    /**
     * Loads genre cover bitmap from multiple sources in order of efficiency:
     * 1. External image files in audio directory (folder.jpg, cover.jpg, etc.)
     * 2. Embedded artwork in audio files (using MediaMetadataRetriever)
     *
     * @param genre Genre model with songPaths
     * @return Bitmap of genre cover or null if not found
     */
    fun load(genre: Genre): Bitmap? {
        // Source 1: Check for external album art files (fastest)
        if (genre.songPaths.isNotEmpty()) {
            val firstSongPath = genre.songPaths.first()
            val directory = File(firstSongPath).parentFile

            if (directory != null && directory.exists()) {
                val customNames = CoverLoader.generateCustomArtworkNames(genre.name)
                val externalArtwork = CoverLoader.loadExternalArtwork(directory, customNames)

                if (externalArtwork != null) {
                    Log.d(TAG, "Loaded genre art from external file for: ${genre.name}")
                    return externalArtwork
                }
            }
        }

        // Source 2: Extract from audio file metadata (embedded artwork)
        if (genre.songPaths.isNotEmpty()) {
            val embeddedArtwork = CoverLoader.loadEmbeddedArtworkFromPaths(genre.songPaths)
            if (embeddedArtwork != null) {
                Log.d(TAG, "Loaded genre art from embedded metadata for: ${genre.name}")
                return embeddedArtwork
            }
        }

        return null
    }
}

