package app.simple.felicity.repository.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Core cover art loading functionality shared across all media types.
 * Think of this as the Swiss Army knife for finding album art — it tries
 * every trick in the book before giving up.
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
     * media players, and tagging tools. Ordered by real-world prevalence so the
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
     * Looks for common image files (folder.jpg, cover.jpg, etc.) directly inside
     * a given directory. This is only useful when we have an actual [File] reference
     * to the folder — SAF URIs can't be navigated this way, so callers should skip
     * this method when the audio path is a content URI.
     *
     * @param directory Directory to search for artwork files.
     * @param customNames Optional extra filenames to check (e.g., album or artist name variants).
     * @return Bitmap of the first matching artwork file, or null if none is found.
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
     * Pulls the embedded artwork out of an audio file using [MediaMetadataRetriever].
     * Works with both content:// URIs (SAF) and plain file paths — just pass the
     * right one and this method figures out how to open it.
     *
     * @param context Android context needed to open content URIs.
     * @param audioUri The content:// URI of the audio file.
     * @return Bitmap or null if no embedded artwork is found.
     */
    fun loadEmbeddedArtwork(context: Context, audioUri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, audioUri)

            val embeddedPicture = retriever.embeddedPicture
            if (embeddedPicture != null && embeddedPicture.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.size)
                if (bitmap != null) {
                    Log.d(TAG, "Extracted embedded art via URI: $audioUri")
                    return bitmap
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract artwork from URI: $audioUri", e)
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
     * Scans through a list of song paths (or URIs — both are fine) and returns the
     * embedded artwork from the first file that actually has one. Stops early once
     * artwork is found to avoid unnecessary work.
     *
     * @param context Android context needed to open content URIs via SAF.
     * @param songPaths List of audio file paths or content URI strings to check.
     * @param maxFiles Maximum number of files to check before giving up (default: 5).
     * @return Bitmap or null if none of the checked files had embedded artwork.
     */
    fun loadEmbeddedArtworkFromPaths(context: Context, songPaths: List<String>, maxFiles: Int = 5): Bitmap? {
        val retriever = MediaMetadataRetriever()

        try {
            for (path in songPaths.take(maxFiles)) {
                try {
                    // Both content:// URIs and file paths go through setDataSource(context, uri)
                    // because MediaMetadataRetriever handles both just fine that way.
                    retriever.setDataSource(context, Uri.parse(path))

                    val embeddedPicture = retriever.embeddedPicture
                    if (embeddedPicture != null && embeddedPicture.isNotEmpty()) {
                        val bitmap = BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.size)
                        if (bitmap != null) {
                            Log.d(TAG, "Extracted embedded art from: $path")
                            return bitmap
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to extract artwork from: $path", e)
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
     * Useful for looking up folder.jpg alternatives named after the album or artist.
     *
     * @param name Name to generate variants for (e.g., album or artist name).
     * @return List of filename variants.
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

    /**
     * Returns the built-in placeholder artwork when no cover art can be found anywhere.
     * It's not glamorous, but it's better than a crash or a blank space.
     */
    fun loadEmptyAudioCover(): Bitmap {
        val stream = BaseCoverLoader::class.java.getResourceAsStream(ALBUM_ART_PATH)
        return BitmapFactory.decodeStream(stream)
    }
}
