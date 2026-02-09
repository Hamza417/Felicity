package app.simple.felicity.repository.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import app.simple.felicity.repository.models.Audio
import java.io.File

object AudioUtils {

    private const val TAG = "AudioUtils"
    private const val ALBUM_ART_PATH = "/album_art.png"

    /**
     * Loads audio cover bitmap from multiple sources in order of efficiency:
     * 1. External image files in audio directory (folder.jpg, cover.jpg, etc.)
     * 2. Embedded artwork in audio file (using MediaMetadataRetriever)
     *
     * @param audio Audio model with path
     * @return Bitmap of audio cover or null if not found
     */
    fun loadAudioCover(audio: Audio): Bitmap? {
        val audioPath = audio.path ?: return null

        // Source 1: Check for external album art files (fastest)
        val directory = File(audioPath).parentFile

        if (directory != null && directory.exists()) {
            val externalArtwork = loadExternalAlbumArt(directory, audio.album)
            if (externalArtwork != null) {
                Log.d(TAG, "Loaded audio art from external file for: ${audio.title}")
                return externalArtwork
            }
        }

        // Source 2: Extract from audio file metadata (embedded artwork)
        val embeddedArtwork = loadEmbeddedAudioArt(audioPath)
        if (embeddedArtwork != null) {
            Log.d(TAG, "Loaded audio art from embedded metadata for: ${audio.title}")
            return embeddedArtwork
        }

        // If no artwork found, return null or a default placeholder
        return loadEmptyAudioCover()
    }

    /**
     * Checks for common album art filenames in the audio directory.
     * Checks: folder.jpg, cover.jpg, album.jpg, front.jpg (and PNG variants)
     *
     * @param directory Directory containing audio file
     * @param albumName Optional album name for {album_name}.jpg pattern
     * @return Bitmap or null if not found
     */
    private fun loadExternalAlbumArt(directory: File, albumName: String?): Bitmap? {
        val commonArtworkNames = mutableListOf(
                "folder.jpg", "folder.png",
                "cover.jpg", "cover.png",
                "album.jpg", "album.png",
                "front.jpg", "front.png",
                "albumart.jpg", "albumart.png",
                "AlbumArt.jpg", "AlbumArt.png",
                "Folder.jpg", "Folder.png",
                "Cover.jpg", "Cover.png"
        )

        // Add album name variants if available
        if (!albumName.isNullOrEmpty()) {
            // Sanitize album name for filesystem
            val sanitizedName = albumName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            commonArtworkNames.addAll(listOf(
                    "$sanitizedName.jpg", "$sanitizedName.png",
                    "${sanitizedName}_cover.jpg", "${sanitizedName}_cover.png"
            ))
        }

        for (filename in commonArtworkNames) {
            val artFile = File(directory, filename)
            if (artFile.exists() && artFile.canRead()) {
                try {
                    val bitmap = BitmapFactory.decodeFile(artFile.absolutePath)
                    if (bitmap != null) {
                        Log.d(TAG, "Found external art: ${artFile.name}")
                        return bitmap
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load external art from: ${artFile.name}", e)
                }
            }
        }

        return null
    }

    /**
     * Extracts embedded album artwork from audio file using MediaMetadataRetriever.
     *
     * @param audioPath Audio file path
     * @return Bitmap or null if not found
     */
    private fun loadEmbeddedAudioArt(audioPath: String): Bitmap? {
        val retriever = MediaMetadataRetriever()

        try {
            val file = File(audioPath)
            if (!file.exists() || !file.canRead()) {
                return null
            }

            retriever.setDataSource(audioPath)

            // Extract embedded picture
            val embeddedPicture = retriever.embeddedPicture
            if (embeddedPicture != null && embeddedPicture.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.size)
                if (bitmap != null) {
                    Log.d(TAG, "Extracted embedded art from: ${file.name}")
                    return bitmap
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract artwork from: $audioPath", e)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MediaMetadataRetriever", e)
            }
        }

        return null
    }

    private fun loadEmptyAudioCover(): Bitmap? {
        val stream = AudioUtils::class.java.getResourceAsStream(ALBUM_ART_PATH) ?: return null
        return try {
            BitmapFactory.decodeStream(stream)
        } catch (e: Exception) {
            null
        }
    }
}