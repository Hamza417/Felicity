package app.simple.felicity.repository.covers

import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.repository.models.Folder
import java.io.File

/**
 * Cover art loader for Folder collections.
 */
object FolderCover {
    private const val TAG = "FolderCover"

    /**
     * Loads folder cover bitmap from multiple sources in order of efficiency:
     * 1. External image files in the folder directory (folder.jpg, cover.jpg, etc.)
     * 2. Embedded artwork in audio files (using MediaMetadataRetriever)
     *
     * @param folder Folder model with path and songPaths
     * @return Bitmap of folder cover or null if not found
     */
    fun load(folder: Folder): Bitmap? {
        // Source 1: Check for external album art files in the folder directory (fastest)
        val directory = File(folder.path)
        if (directory.exists()) {
            val externalArtwork = CoverLoader.loadExternalArtwork(directory, emptyList())
            if (externalArtwork != null) {
                Log.d(TAG, "Loaded folder art from external file for: ${folder.name}")
                return externalArtwork
            }
        }

        // Source 2: Extract from audio file metadata (embedded artwork)
        if (folder.songPaths.isNotEmpty()) {
            val embeddedArtwork = CoverLoader.loadEmbeddedArtworkFromPaths(folder.songPaths)
            if (embeddedArtwork != null) {
                Log.d(TAG, "Loaded folder art from embedded metadata for: ${folder.name}")
                return embeddedArtwork
            }
        }

        return null
    }
}

