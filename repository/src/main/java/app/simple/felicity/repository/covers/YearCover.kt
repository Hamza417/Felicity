package app.simple.felicity.repository.covers

import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.repository.models.YearGroup

/**
 * Cover art loader for YearGroup collections.
 * Loads art from embedded metadata in the first available song for that year.
 */
object YearCover {
    private const val TAG = "YearCover"

    /**
     * Loads cover bitmap for a year group using embedded artwork from its songs.
     *
     * @param yearGroup YearGroup model with songPaths
     * @return Bitmap of cover or null if not found
     */
    fun load(yearGroup: YearGroup): Bitmap? {
        if (yearGroup.songPaths.isNotEmpty()) {
            val embeddedArtwork = CoverLoader.loadEmbeddedArtworkFromPaths(yearGroup.songPaths)
            if (embeddedArtwork != null) {
                Log.d(TAG, "Loaded cover from embedded metadata for year: ${yearGroup.year}")
                return embeddedArtwork
            }
        }
        return null
    }
}

