package app.simple.felicity.repository.covers

import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.repository.models.Artist
import java.io.File

/**
 * Cover art loader for Artist collections.
 */
object ArtistCover {
    private const val TAG = "ArtistCover"

    /**
     * Loads artist cover bitmap from multiple sources in order of efficiency:
     * 1. External image files in audio directory (folder.jpg, cover.jpg, etc.)
     * 2. Embedded artwork in audio files (using MediaMetadataRetriever)
     *
     * @param artist Artist model with songPaths
     * @return Bitmap of artist cover or null if not found
     */
    fun load(artist: Artist): Bitmap? {
        // Source 1: Check for external album art files (fastest)
        if (artist.songPaths.isNotEmpty()) {
            val firstSongPath = artist.songPaths.first()
            val directory = File(firstSongPath).parentFile

            if (directory != null && directory.exists()) {
                val customNames = CoverLoader.generateCustomArtworkNames(artist.name)
                val externalArtwork = CoverLoader.loadExternalArtwork(directory, customNames)

                if (externalArtwork != null) {
                    Log.d(TAG, "Loaded artist art from external file for: ${artist.name}")
                    return externalArtwork
                }
            }
        }

        // Source 2: Extract from audio file metadata (embedded artwork)
        if (artist.songPaths.isNotEmpty()) {
            val embeddedArtwork = CoverLoader.loadEmbeddedArtworkFromPaths(artist.songPaths)
            if (embeddedArtwork != null) {
                Log.d(TAG, "Loaded artist art from embedded metadata for: ${artist.name}")
                return embeddedArtwork
            }
        }

        return CoverLoader.loadEmptyAudioCover()
    }
}

