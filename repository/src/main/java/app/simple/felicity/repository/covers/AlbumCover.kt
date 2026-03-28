package app.simple.felicity.repository.covers

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.covers.MediaStoreCover.loadCoverFromMediaStore
import app.simple.felicity.repository.covers.MediaStoreCover.uriToBitmap
import app.simple.felicity.repository.models.Album
import java.io.File

/**
 * Cover art loader for Album collections.
 *
 * @author Hamza417
 */
object AlbumCover {
    private const val TAG = "AlbumCover"

    /**
     * Loads album cover bitmap from multiple sources in order of preference:
     * 1. MediaStore album art cache (when [LibraryPreferences.isUseMediaStoreArtwork] is true)
     * 2. External image files in the audio directory (folder.jpg, cover.jpg, etc.)
     * 3. Embedded artwork extracted from the first matching audio file
     *
     * All songs in an album share the same [MediaStore.Audio.Media.ALBUM_ID], so querying by
     * the first song path reliably retrieves the correct per-album artwork from the system cache.
     *
     * @param context Android context used for MediaStore queries
     * @param album Album model with [Album.songPaths]
     * @return Bitmap of album cover, or null if no artwork is found
     */
    fun load(context: Context, album: Album): Bitmap? {
        if (album.songPaths.isNotEmpty()) {
            if (LibraryPreferences.isUseMediaStoreArtwork()) {
                // Primary: resolve from MediaStore using any song path from this album.
                val uri = context.loadCoverFromMediaStore(album.songPaths.first())
                val mediaStoreBitmap = uri?.let { context.uriToBitmap(it) }
                if (mediaStoreBitmap != null) {
                    Log.d(TAG, "Loaded album art from MediaStore for: ${album.name}")
                    return mediaStoreBitmap
                }
                // Fall through to file-based sources if MediaStore yielded nothing.
            }

            // Source 1: External image files in the audio directory.
            val directory = File(album.songPaths.first()).parentFile
            if (directory != null && directory.exists()) {
                val customNames = BaseCoverLoader.generateCustomArtworkNames(album.name)
                val externalArtwork = BaseCoverLoader.loadExternalArtwork(directory, customNames)
                if (externalArtwork != null) {
                    Log.d(TAG, "Loaded album art from external file for: ${album.name}")
                    return externalArtwork
                }
            }

            // Source 2: Artwork embedded in the audio file tags.
            val embeddedArtwork = BaseCoverLoader.loadEmbeddedArtworkFromPaths(album.songPaths)
            if (embeddedArtwork != null) {
                Log.d(TAG, "Loaded album art from embedded metadata for: ${album.name}")
                return embeddedArtwork
            }
        }

        return BaseCoverLoader.loadEmptyAudioCover()
    }
}
