package app.simple.felicity.repository.covers

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.covers.MediaStoreCover.loadCoverFromMediaStore
import app.simple.felicity.repository.covers.MediaStoreCover.uriToBitmap
import app.simple.felicity.repository.models.Artist
import java.io.File

/**
 * Cover art loader for Artist collections.
 *
 * @author Hamza417
 */
object ArtistCover {
    private const val TAG = "ArtistCover"

    /**
     * Loads artist cover bitmap from multiple sources in order of preference:
     * 1. MediaStore album art cache (when [LibraryPreferences.isUseMediaStoreArtwork] is true)
     * 2. External image files in the audio directory (folder.jpg, cover.jpg, etc.)
     * 3. Embedded artwork extracted from the first matching audio file
     *
     * The first song path in [Artist.songPaths] is used for the MediaStore query since MediaStore
     * indexes album art per album, not per artist. The first song is a reliable representative.
     *
     * @param context Android context used for MediaStore queries
     * @param artist Artist model with [Artist.songPaths]
     * @return Bitmap of artist cover, or null if no artwork is found
     */
    fun load(context: Context, artist: Artist): Bitmap? {
        if (artist.songPaths.isNotEmpty()) {
            if (LibraryPreferences.isUseMediaStoreArtwork()) {
                // Primary: resolve from MediaStore using the first song path.
                val uri = context.loadCoverFromMediaStore(artist.songPaths.first())
                val mediaStoreBitmap = uri?.let { context.uriToBitmap(it) }
                if (mediaStoreBitmap != null) {
                    Log.d(TAG, "Loaded artist art from MediaStore for: ${artist.name}")
                    return mediaStoreBitmap
                }
                // Fall through to file-based sources if MediaStore yielded nothing.
            }

            // Source 1: External image files in the audio directory.
            val directory = File(artist.songPaths.first()).parentFile
            if (directory != null && directory.exists()) {
                val customNames = BaseCoverLoader.generateCustomArtworkNames(artist.name)
                val externalArtwork = BaseCoverLoader.loadExternalArtwork(directory, customNames)
                if (externalArtwork != null) {
                    Log.d(TAG, "Loaded artist art from external file for: ${artist.name}")
                    return externalArtwork
                }
            }

            // Source 2: Artwork embedded in the audio file tags.
            val embeddedArtwork = BaseCoverLoader.loadEmbeddedArtworkFromPaths(artist.songPaths)
            if (embeddedArtwork != null) {
                Log.d(TAG, "Loaded artist art from embedded metadata for: ${artist.name}")
                return embeddedArtwork
            }
        }

        return BaseCoverLoader.loadEmptyAudioCover()
    }
}
