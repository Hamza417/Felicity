package app.simple.felicity.repository.covers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File

/**
 * Core cover art loading functionality shared across all media types.
 * Provides common methods for loading external and embedded artwork.
 */
internal object CoverLoader {
    private const val TAG = "CoverLoader"
    private const val ALBUM_ART_PATH = "/album_art.png"

    /**
     * Common album art filenames to check in audio directories.
     */
    private val COMMON_ARTWORK_NAMES = listOf(
            "folder.jpg", "folder.png",
            "cover.jpg", "cover.png",
            "album.jpg", "album.png",
            "front.jpg", "front.png",
            "albumart.jpg", "albumart.png",
            "AlbumArt.jpg", "AlbumArt.png",
            "Folder.jpg", "Folder.png",
            "Cover.jpg", "Cover.png"
    )

    /**
     * Checks for common album art filenames in the specified directory.
     *
     * @param directory Directory to search for artwork files
     * @param customNames Optional custom filenames to check (e.g., album/artist name variants)
     * @return Bitmap or null if not found
     */
    fun loadExternalArtwork(directory: File, customNames: List<String> = emptyList()): Bitmap? {
        val allNames = COMMON_ARTWORK_NAMES + customNames

        for (filename in allNames) {
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

        // Look for first image file in the directory as a fallback
        val imageFiles = directory.listFiles { file ->
            file.isFile && file.canRead() && (file.name.endsWith(".jpg", true
            ) || file.name.endsWith(".png", true))
        } ?: return null

        return imageFiles.asSequence()
            .mapNotNull { file ->
                try {
                    BitmapFactory.decodeFile(file.absolutePath)?.also {
                        Log.d(TAG, "Found fallback external art: ${file.name}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load fallback external art from: ${file.name}", e)
                    null
                }
            }
            .firstOrNull()
    }

    /**
     * Extracts embedded artwork from a single audio file using MediaMetadataRetriever.
     *
     * @param audioPath Path to the audio file
     * @return Bitmap or null if not found
     */
    fun loadEmbeddedArtwork(audioPath: String): Bitmap? {
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

    /**
     * Extracts embedded artwork from multiple audio files.
     * Checks files in order and returns the first one with embedded artwork.
     *
     * @param songPaths List of audio file paths to check
     * @param maxFiles Maximum number of files to check (default: 5)
     * @return Bitmap or null if not found
     */
    fun loadEmbeddedArtworkFromPaths(songPaths: List<String>, maxFiles: Int = 5): Bitmap? {
        val retriever = MediaMetadataRetriever()

        try {
            // Check each audio file (up to maxFiles for efficiency)
            for (path in songPaths.take(maxFiles)) {
                try {
                    val file = File(path)
                    if (!file.exists() || !file.canRead()) {
                        continue
                    }

                    retriever.setDataSource(path)

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
                    Log.w(TAG, "Failed to extract artwork from: $path", e)
                    // Continue to next file
                }
            }
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MediaMetadataRetriever", e)
            }
        }

        return null
    }

    /**
     * Generate custom artwork filename variants based on a name.
     *
     * @param name Name to generate variants for (e.g., album or artist name)
     * @return List of filename variants
     */
    fun generateCustomArtworkNames(name: String?): List<String> {
        if (name.isNullOrEmpty()) return emptyList()

        // Sanitize name for filesystem
        val sanitizedName = name.replace(Regex("[^a-zA-Z0-9.-]"), "_")

        return listOf(
                "$sanitizedName.jpg", "$sanitizedName.png",
                "${sanitizedName}_cover.jpg", "${sanitizedName}_cover.png",
                "${sanitizedName}_front.jpg", "${sanitizedName}_back.png",
        )
    }

    fun loadEmptyAudioCover(): Bitmap? {
        val stream = CoverLoader::class.java.getResourceAsStream(ALBUM_ART_PATH) ?: return null
        return try {
            BitmapFactory.decodeStream(stream)
        } catch (e: Exception) {
            null
        }
    }
}

