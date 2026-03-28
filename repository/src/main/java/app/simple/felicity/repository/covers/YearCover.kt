package app.simple.felicity.repository.covers

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.covers.MediaStoreCover.loadCoverFromMediaStore
import app.simple.felicity.repository.covers.MediaStoreCover.uriToBitmap
import app.simple.felicity.repository.models.YearGroup

/**
 * Cover art loader for YearGroup collections.
 *
 * @author Hamza417
 */
object YearCover {
    private const val TAG = "YearCover"

    /**
     * Loads cover bitmap for a year group from multiple sources in order of preference:
     * 1. MediaStore album art cache (when [LibraryPreferences.isUseMediaStoreArtwork] is true)
     * 2. Embedded artwork extracted from the first matching audio file
     *
     * The first song path in [YearGroup.songPaths] is used for the MediaStore query since
     * MediaStore does not group artwork by year; the first song is a reliable representative.
     *
     * @param context Android context used for MediaStore queries
     * @param yearGroup YearGroup model with [YearGroup.songPaths]
     * @return Bitmap of cover, or null if no artwork is found
     */
    fun load(context: Context, yearGroup: YearGroup): Bitmap? {
        if (yearGroup.songPaths.isNotEmpty()) {
            if (LibraryPreferences.isUseMediaStoreArtwork()) {
                // Primary: resolve from MediaStore using the first song path.
                val uri = context.loadCoverFromMediaStore(yearGroup.songPaths.first())
                val mediaStoreBitmap = uri?.let { context.uriToBitmap(it) }
                if (mediaStoreBitmap != null) {
                    Log.d(TAG, "Loaded cover from MediaStore for year: ${yearGroup.year}")
                    return mediaStoreBitmap
                }
                // Fall through to embedded sources if MediaStore yielded nothing.
            }

            // Source 1: Artwork embedded in the audio file tags.
            val embeddedArtwork = BaseCoverLoader.loadEmbeddedArtworkFromPaths(yearGroup.songPaths)
            if (embeddedArtwork != null) {
                Log.d(TAG, "Loaded cover from embedded metadata for year: ${yearGroup.year}")
                return embeddedArtwork
            }
        }

        return null
    }
}
