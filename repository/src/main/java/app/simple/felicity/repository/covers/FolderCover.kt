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
     * Loads the cover art for a folder, trying sources in order until one works.
     *
     * The lookup order is:
     * 1. MediaStore's album art cache — system-indexed and very fast.
     * 2. External image files sitting in the folder (folder.jpg, cover.jpg, etc.)
     *    — only attempted when [Folder.path] is a real file path, not a URI.
     * 3. Artwork embedded inside one of the folder's audio files.
     *
     * The external file check is skipped when the folder path is a content URI
     * because we can't list files in an arbitrary content URI directory.
     *
     * @param context Android context used for MediaStore and SAF queries.
     * @param folder Folder model with [Folder.path] and [Folder.songPaths].
     * @return Bitmap of folder cover, or null if no artwork is found.
     */
    fun load(context: Context, folder: Folder): Bitmap? {
        if (folder.songPaths.isNotEmpty() && LibraryPreferences.isUseMediaStoreArtwork()) {
            val uri = context.loadCoverFromMediaStore(folder.songPaths.first())
            val mediaStoreBitmap = uri?.let { context.uriToBitmap(it) }
            if (mediaStoreBitmap != null) {
                Log.d(TAG, "Loaded folder art from MediaStore for: ${folder.name}")
                return mediaStoreBitmap
            }
        }

        // Try folder images — the lookup strategy depends on whether the folder
        // path is a plain file system path or a SAF document ID.
        // SAF document IDs look like "primary:Music/Album" — they contain a colon
        // but don't start with a slash, so we can tell them apart from real paths.
        val isSAFFolder = !folder.path.startsWith("/")

        if (isSAFFolder) {
            // The folder path IS the parent document ID, so we can hand it straight
            // to BaseCoverLoader which handles the tree URI lookup, child scan, and caching.
            val externalArtwork = BaseCoverLoader.loadExternalArtworkSAFByDocId(context, folder.path)
            if (externalArtwork != null) {
                Log.d(TAG, "Loaded folder art from SAF external file for: ${folder.name}")
                return externalArtwork
            }
        } else {
            val directory = File(folder.path)
            if (directory.exists()) {
                val externalArtwork = BaseCoverLoader.loadExternalArtwork(directory, emptyList())
                if (externalArtwork != null) {
                    Log.d(TAG, "Loaded folder art from external file for: ${folder.name}")
                    return externalArtwork
                }
            }
        }

        if (folder.songPaths.isNotEmpty()) {
            val embeddedArtwork = BaseCoverLoader.loadEmbeddedArtworkFromPaths(context, folder.songPaths)
            if (embeddedArtwork != null) {
                Log.d(TAG, "Loaded folder art from embedded metadata for: ${folder.name}")
                return embeddedArtwork
            }
        }

        return null
    }
}
