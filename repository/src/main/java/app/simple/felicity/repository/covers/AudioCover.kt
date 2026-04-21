package app.simple.felicity.repository.covers

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.covers.MediaStoreCover.loadCoverFromMediaStore
import app.simple.felicity.repository.covers.MediaStoreCover.uriToBitmap
import app.simple.felicity.repository.models.Audio
import java.io.File

/**
 * Cover art loader for individual Audio (Song) files.
 *
 * Handles both regular file paths and SAF content URIs transparently —
 * the caller doesn't need to know which one it's dealing with.
 *
 * @author Hamza417
 */
object AudioCover {

    /**
     * Loads the cover art for a single audio file, working through sources in order.
     *
     * The lookup order is:
     * 1. MediaStore's album art cache — fastest, no file reading needed at all.
     * 2. External image files next to the audio file (folder.jpg, cover.jpg, etc.)
     *    — only attempted for real file paths, not content URIs.
     * 3. Artwork embedded inside the audio file's tags — works for both paths and URIs.
     *
     * @param context Android context used for MediaStore and SAF queries.
     * @param audio Audio model whose URI or path drives the lookup.
     * @return Bitmap of audio cover, or a placeholder if no artwork is found.
     */
    fun load(context: Context, audio: Audio): Bitmap? {
        val audioPath = audio.uri ?: return null
        val isSAFPath = audioPath.startsWith("content://")

        if (LibraryPreferences.isUseMediaStoreArtwork()) {
            // The fastest path — let the system hand us a pre-decoded album art URI.
            val uri = context.loadCoverFromMediaStore(audioPath)
            val mediaStoreBitmap = uri?.let { context.uriToBitmap(it) }
            if (mediaStoreBitmap != null) return mediaStoreBitmap
        }

        // Folder images only make sense when we can navigate the file system.
        // Content URIs live in a sandbox, so there's no "parent folder" to peek into.
        if (!isSAFPath) {
            val directory = File(audioPath).parentFile
            if (directory != null && directory.exists()) {
                val customNames = BaseCoverLoader.generateCustomArtworkNames(audio.album)
                val externalArtwork = BaseCoverLoader.loadExternalArtwork(directory, customNames)
                if (externalArtwork != null) return externalArtwork
            }
        }

        // Last resort: crack open the audio file and read the embedded image tag.
        // MediaMetadataRetriever handles both content URIs and file paths via context.
        val embeddedArtwork = BaseCoverLoader.loadEmbeddedArtwork(context, Uri.parse(audioPath))
        if (embeddedArtwork != null) return embeddedArtwork

        return BaseCoverLoader.loadEmptyAudioCover()
    }
}

