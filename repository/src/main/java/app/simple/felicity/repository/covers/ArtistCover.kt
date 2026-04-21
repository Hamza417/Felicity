package app.simple.felicity.repository.covers

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.covers.MediaStoreCover.loadCoverFromMediaStore
import app.simple.felicity.repository.covers.MediaStoreCover.uriToBitmap
import app.simple.felicity.repository.models.Artist

/**
 * Cover art loader for Artist collections.
 *
 * @author Hamza417
 */
object ArtistCover {
    private const val TAG = "ArtistCover"

    /**
     * Loads the cover art for an artist, trying sources in order until one works.
     *
     * The lookup order is:
     * 1. MediaStore's album art cache — the fastest option, no file I/O needed.
     * 2. Artwork embedded inside the audio file's tags.
     *
     * Folder-based artwork (folder.jpg etc.) is skipped because song paths are
     * content URIs and we can't walk up to a parent directory from a content URI.
     * MediaStore doesn't index artwork by artist either, so the first song acts
     * as a representative for the whole artist.
     *
     * @param context Android context used for MediaStore and SAF queries.
     * @param artist Artist model whose [Artist.songPaths] drive the lookup.
     * @return Bitmap of artist cover, or a placeholder if no artwork is found.
     */
    fun load(context: Context, artist: Artist): Bitmap {
        if (artist.songPaths.isNotEmpty()) {
            if (LibraryPreferences.isUseMediaStoreArtwork()) {
                val uri = context.loadCoverFromMediaStore(artist.songPaths.first())
                val mediaStoreBitmap = uri?.let { context.uriToBitmap(it) }
                if (mediaStoreBitmap != null) {
                    Log.d(TAG, "Loaded artist art from MediaStore for: ${artist.name}")
                    return mediaStoreBitmap
                }
            }

            val embeddedArtwork = BaseCoverLoader.loadEmbeddedArtworkFromPaths(context, artist.songPaths)
            if (embeddedArtwork != null) {
                Log.d(TAG, "Loaded artist art from embedded metadata for: ${artist.name}")
                return embeddedArtwork
            }
        }

        return BaseCoverLoader.loadEmptyAudioCover()
    }
}
