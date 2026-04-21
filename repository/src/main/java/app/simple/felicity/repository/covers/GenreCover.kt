package app.simple.felicity.repository.covers

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.covers.MediaStoreCover.loadCoverFromMediaStore
import app.simple.felicity.repository.covers.MediaStoreCover.uriToBitmap
import app.simple.felicity.repository.models.Genre

/**
 * Cover art loader for Genre collections.
 *
 * @author Hamza417
 */
object GenreCover {
    private const val TAG = "GenreCover"

    /**
     * Loads the cover art for a genre, trying sources in order until one works.
     *
     * The lookup order is:
     * 1. MediaStore's album art cache — pre-indexed by the system, super fast.
     * 2. Artwork embedded inside one of the genre's audio files.
     *
     * MediaStore doesn't index artwork by genre, so the first song in the list
     * stands in as the genre representative. Folder images are skipped because
     * song paths are content URIs and we can't navigate parent directories from them.
     *
     * @param context Android context used for MediaStore and SAF queries.
     * @param genre Genre model whose [Genre.songPaths] drive the lookup.
     * @return Bitmap of genre cover, or a placeholder if no artwork is found.
     */
    fun load(context: Context, genre: Genre): Bitmap {
        if (genre.songPaths.isNotEmpty()) {
            if (LibraryPreferences.isUseMediaStoreArtwork()) {
                val uri = context.loadCoverFromMediaStore(genre.songPaths.first())
                val mediaStoreBitmap = uri?.let { context.uriToBitmap(it) }
                if (mediaStoreBitmap != null) {
                    Log.d(TAG, "Loaded genre art from MediaStore for: ${genre.name}")
                    return mediaStoreBitmap
                }
            }

            val embeddedArtwork = BaseCoverLoader.loadEmbeddedArtworkFromPaths(context, genre.songPaths)
            if (embeddedArtwork != null) {
                Log.d(TAG, "Loaded genre art from embedded metadata for: ${genre.name}")
                return embeddedArtwork
            }
        }

        return BaseCoverLoader.loadEmptyAudioCover()
    }
}
