package app.simple.felicity.repository.covers

import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.repository.models.Album
import java.io.File

/**
 * Cover art loader for Album collections.
 */
object AlbumCover {
    private const val TAG = "AlbumCover"

    /**
     * Loads album cover bitmap from multiple sources in order of efficiency:
     * 1. External image files in audio directory (folder.jpg, cover.jpg, etc.)
     * 2. Embedded artwork in audio files (using MediaMetadataRetriever)
     *
     * @param album Album model with songPaths
     * @return Bitmap of album cover or null if not found
     */
    fun load(album: Album): Bitmap? {
        // Source 1: Check for external album art files (fastest)
        if (album.songPaths.isNotEmpty()) {
            val firstSongPath = album.songPaths.first()
            val directory = File(firstSongPath).parentFile

            if (directory != null && directory.exists()) {
                val customNames = CoverLoader.generateCustomArtworkNames(album.name)
                val externalArtwork = CoverLoader.loadExternalArtwork(directory, customNames)

                if (externalArtwork != null) {
                    Log.d(TAG, "Loaded album art from external file for: ${album.name}")
                    return externalArtwork
                }
            }
        }

        // Source 2: Extract from audio file metadata (embedded artwork)
        if (album.songPaths.isNotEmpty()) {
            val embeddedArtwork = CoverLoader.loadEmbeddedArtworkFromPaths(album.songPaths)
            if (embeddedArtwork != null) {
                Log.d(TAG, "Loaded album art from embedded metadata for: ${album.name}")
                return embeddedArtwork
            }
        }

        return CoverLoader.loadEmptyAudioCover()
    }
}

