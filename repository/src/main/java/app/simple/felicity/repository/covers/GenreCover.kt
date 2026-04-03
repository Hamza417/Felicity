package app.simple.felicity.repository.covers

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.covers.MediaStoreCover.loadCoverFromMediaStore
import app.simple.felicity.repository.covers.MediaStoreCover.uriToBitmap
import app.simple.felicity.repository.models.Genre
import java.io.File

/**
 * Cover art loader for Genre collections.
 *
 * @author Hamza417
 */
object GenreCover {
    private const val TAG = "GenreCover"

    /**
     * Loads genre cover bitmap from multiple sources in order of preference:
     * 1. MediaStore album art cache (when [LibraryPreferences.isUseMediaStoreArtwork] is true)
     * 2. External image files in the audio directory (folder.jpg, cover.jpg, etc.)
     * 3. Embedded artwork extracted from the first matching audio file
     *
     * The first song path in [Genre.songPaths] is used for the MediaStore query since MediaStore
     * does not natively index artwork by genre — the first song is a reliable representative.
     *
     * @param context Android context used for MediaStore queries
     * @param genre Genre model with [Genre.songPaths]
     * @return Bitmap of genre cover, or null if no artwork is found
     */
    fun load(context: Context, genre: Genre): Bitmap {
        if (genre.songPaths.isNotEmpty()) {
            if (LibraryPreferences.isUseMediaStoreArtwork()) {
                // Primary: resolve from MediaStore using the first song path.
                val uri = context.loadCoverFromMediaStore(genre.songPaths.first())
                val mediaStoreBitmap = uri?.let { context.uriToBitmap(it) }
                if (mediaStoreBitmap != null) {
                    Log.d(TAG, "Loaded genre art from MediaStore for: ${genre.name}")
                    return mediaStoreBitmap
                }
                // Fall through to file-based sources if MediaStore yielded nothing.
            }

            // Source 1: External image files in the audio directory.
            val directory = File(genre.songPaths.first()).parentFile
            if (directory != null && directory.exists()) {
                val customNames = BaseCoverLoader.generateCustomArtworkNames(genre.name)
                val externalArtwork = BaseCoverLoader.loadExternalArtwork(directory, customNames)
                if (externalArtwork != null) {
                    Log.d(TAG, "Loaded genre art from external file for: ${genre.name}")
                    return externalArtwork
                }
            }

            // Source 2: Artwork embedded in the audio file tags.
            val embeddedArtwork = BaseCoverLoader.loadEmbeddedArtworkFromPaths(genre.songPaths)
            if (embeddedArtwork != null) {
                Log.d(TAG, "Loaded genre art from embedded metadata for: ${genre.name}")
                return embeddedArtwork
            }
        }

        return BaseCoverLoader.loadEmptyAudioCover()
    }
}
