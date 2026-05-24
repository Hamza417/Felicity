package app.simple.felicity.repository.covers

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.net.toUri
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

        val customNames = BaseCoverLoader.generateCustomArtworkNames(audio.album)

        if (isSAFPath) {
            // For SAF URIs we ask the system to list the parent folder's children and
            // look for any of our known artwork filenames there.
            val externalArtwork = BaseCoverLoader.loadExternalArtworkSAF(context, audioPath.toUri(), customNames)
            if (externalArtwork != null) {
                Log.d("AudioCover", "Found SAF external artwork for ${audio.title}")
                return externalArtwork
            }
        } else {
            // Regular file paths — we can navigate the directory directly.
            val directory = File(audioPath).parentFile
            if (directory != null && directory.exists()) {
                val externalArtwork = BaseCoverLoader.loadExternalArtwork(directory, customNames)
                if (externalArtwork != null) {
                    Log.d("AudioCover", "Found external artwork for ${audio.title} at ${directory.path}")
                    return externalArtwork
                } else {
                    Log.d("AudioCover", "No external artwork found for ${audio.title} in ${directory.path}")
                }
            }
        }

        // Last resort: crack open the audio file and read the embedded image tag.
        // The junction inside BaseCoverLoader handles the extractor order.
        val embeddedArtwork = BaseCoverLoader.loadEmbeddedArtwork(context, audioPath.toUri())
        Log.d("AudioCover", "Embedded artwork ${if (embeddedArtwork != null) "found" else "not found"} for ${audio.title}")
        if (embeddedArtwork != null) return embeddedArtwork

        return BaseCoverLoader.loadEmptyAudioCover()
    }
}

