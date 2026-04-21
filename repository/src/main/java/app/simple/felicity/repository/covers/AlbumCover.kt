package app.simple.felicity.repository.covers

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.covers.MediaStoreCover.loadCoverFromMediaStore
import app.simple.felicity.repository.covers.MediaStoreCover.uriToBitmap
import app.simple.felicity.repository.models.Album

/**
 * Cover art loader for Album collections.
 *
 * @author Hamza417
 */
object AlbumCover {
    private const val TAG = "AlbumCover"

    /**
     * Loads the cover art for an album, trying a few different places in order
     * until something works (or we run out of options and return a placeholder).
     *
     * The lookup order is:
     * 1. MediaStore's album art cache — fastest, pre-indexed by the system.
     * 2. Artwork embedded directly inside the audio file's tags.
     *
     * External folder images (folder.jpg etc.) are skipped because song paths
     * are now content URIs and there is no way to navigate to a parent folder
     * from a content URI without directory access grants.
     *
     * @param context Android context used for MediaStore and SAF queries.
     * @param album Album model whose [Album.songPaths] drive the lookup.
     * @return Bitmap of album cover, or a placeholder if no artwork is found.
     */
    fun load(context: Context, album: Album): Bitmap {
        if (album.songPaths.isNotEmpty()) {
            if (LibraryPreferences.isUseMediaStoreArtwork()) {
                // Primary: grab the pre-indexed album art straight from MediaStore.
                val uri = context.loadCoverFromMediaStore(album.songPaths.first())
                val mediaStoreBitmap = uri?.let { context.uriToBitmap(it) }
                if (mediaStoreBitmap != null) {
                    Log.d(TAG, "Loaded album art from MediaStore for: ${album.name}")
                    return mediaStoreBitmap
                }
            }

            // Fallback: dig through the audio file tags for embedded artwork.
            val embeddedArtwork = BaseCoverLoader.loadEmbeddedArtworkFromPaths(context, album.songPaths)
            if (embeddedArtwork != null) {
                Log.d(TAG, "Loaded album art from embedded metadata for: ${album.name}")
                return embeddedArtwork
            }
        }

        return BaseCoverLoader.loadEmptyAudioCover()
    }
}
