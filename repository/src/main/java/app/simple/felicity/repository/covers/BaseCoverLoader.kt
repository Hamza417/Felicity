package app.simple.felicity.repository.covers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File

/**
 * Core cover art loading functionality shared across all media types.
 * Provides common methods for loading external and embedded artwork.
 *
 * @author Hamza417
 */
internal object BaseCoverLoader {
    private const val TAG = "BaseCoverLoader"
    private const val ALBUM_ART_PATH = "/album_art.png"

    /**
     * Pre-compiled regex for sanitizing names into filesystem-safe filenames.
     * Compiled once at class initialization to avoid repeated compilation overhead.
     */
    private val SANITIZE_REGEX = Regex("[^a-zA-Z0-9.-]")

    /**
     * Hardcoded list of the most common album art filenames used by music rippers,
     * media players, and tagging tools.  Ordered by real-world prevalence so the
     * most likely match is found with the fewest [File.exists] calls.
     */
    private val COMMON_ARTWORK_NAMES = listOf(
            "folder.jpg",    // Most common — Windows Media Player, MusicBrainz Picard
            "cover.jpg",     // beets, MusicBrainz Picard alternate
            "front.jpg",     // EAC, dBpoweramp
            "album.jpg",     // iTunes-style rips
            "albumart.jpg",  // Winamp, foobar2000
            "folder.png",
            "cover.png",
            "front.png",
            "album.png",
            "albumart.png",
            "Folder.jpg",    // Windows Explorer thumbnail convention
            "AlbumArt.jpg"   // Windows Media Player legacy
    )

    /**
     * Checks each hardcoded artwork filename directly via [File.exists] — no directory
     * scan or traversal.  Skipping [File.canRead] avoids an extra [android.system.Os.access]
     * syscall per candidate; a failed [BitmapFactory.decodeFile] is caught gracefully instead.
     *
     * @param directory Directory to search for artwork files
     * @param customNames Optional additional filenames to check (e.g., album/artist name variants)
     * @return Bitmap of the first matching artwork file, or null if none is found
     */
    fun loadExternalArtwork(directory: File, customNames: List<String> = emptyList()): Bitmap? {
        val allNames = COMMON_ARTWORK_NAMES + customNames

        for (filename in allNames) {
            val artFile = File(directory, filename)
            if (artFile.exists()) {
                try {
                    val bitmap = BitmapFactory.decodeFile(artFile.absolutePath)
                    if (bitmap != null) {
                        Log.d(TAG, "Found external art: ${artFile.name}")
                        return bitmap
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decode external art: ${artFile.name}", e)
                }
            }
        }

        return null
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

        val sanitizedName = name.replace(SANITIZE_REGEX, "_")

        return listOf(
                "$sanitizedName.jpg", "$sanitizedName.png",
                "${sanitizedName}_cover.jpg", "${sanitizedName}_cover.png",
                "${sanitizedName}_front.jpg", "${sanitizedName}_back.png",
        )
    }

    fun loadEmptyAudioCover(): Bitmap {
        val stream = BaseCoverLoader::class.java.getResourceAsStream(ALBUM_ART_PATH)
        return BitmapFactory.decodeStream(stream)
    }
}

