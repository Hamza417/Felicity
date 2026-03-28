package app.simple.felicity.repository.covers

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.covers.MediaStoreCover.loadCoverFromMediaStore
import app.simple.felicity.repository.covers.MediaStoreCover.uriToBitmap
import app.simple.felicity.repository.models.Folder
import java.io.File

/**
 * Cover art loader for Folder collections.
 *
 * @author Hamza417
 */
object FolderCover {
    private const val TAG = "FolderCover"

    /**
     * Loads folder cover bitmap from multiple sources in order of preference:
     * 1. MediaStore album art cache (when [LibraryPreferences.isUseMediaStoreArtwork] is true)
     * 2. External image files in the folder directory (folder.jpg, cover.jpg, etc.)
     * 3. Embedded artwork extracted from the first matching audio file
     *
     * The first song path in [Folder.songPaths] is used for the MediaStore lookup, giving the
     * system-cached art for the album that physically resides in this folder.
     *
     * @param context Android context used for MediaStore queries
     * @param folder Folder model with [Folder.path] and [Folder.songPaths]
     * @return Bitmap of folder cover, or null if no artwork is found
     */
    fun load(context: Context, folder: Folder): Bitmap? {
        if (folder.songPaths.isNotEmpty() && LibraryPreferences.isUseMediaStoreArtwork()) {
            // Primary: resolve from MediaStore using the first song path in this folder.
            val uri = context.loadCoverFromMediaStore(folder.songPaths.first())
            val mediaStoreBitmap = uri?.let { context.uriToBitmap(it) }
            if (mediaStoreBitmap != null) {
                Log.d(TAG, "Loaded folder art from MediaStore for: ${folder.name}")
                return mediaStoreBitmap
            }
            // Fall through to file-based sources if MediaStore yielded nothing.
        }

        // Source 1: External image files directly in the folder directory.
        val directory = File(folder.path)
        if (directory.exists()) {
            val externalArtwork = BaseCoverLoader.loadExternalArtwork(directory, emptyList())
            if (externalArtwork != null) {
                Log.d(TAG, "Loaded folder art from external file for: ${folder.name}")
                return externalArtwork
            }
        }

        // Source 2: Artwork embedded in the audio file tags.
        if (folder.songPaths.isNotEmpty()) {
            val embeddedArtwork = BaseCoverLoader.loadEmbeddedArtworkFromPaths(folder.songPaths)
            if (embeddedArtwork != null) {
                Log.d(TAG, "Loaded folder art from embedded metadata for: ${folder.name}")
                return embeddedArtwork
            }
        }

        return null
    }
}
