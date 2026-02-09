package app.simple.felicity.repository.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.appcompat.widget.AppCompatTextView
import app.simple.felicity.repository.models.Album
import app.simple.felicity.shared.R
import app.simple.felicity.shared.utils.ConditionUtils.isNotZero
import java.io.File

object AlbumUtils {

    fun AppCompatTextView.setAlbumFlag(album: Album) {
        text = buildString {
            append(resources.getQuantityString(R.plurals.number_of_songs, album.songCount, album.songCount))

            if (album.firstYear.isNotZero()) {
                append(" | ")
                append(album.firstYear)
            }

            if (album.lastYear.isNotZero() && album.firstYear != album.lastYear) {
                append(" | ")
                append(album.lastYear)
            }
        }
    }

    /**
     * Loads album cover bitmap from multiple sources in order of efficiency:
     * 1. External image files in audio directory (folder.jpg, cover.jpg, etc.)
     * 2. Embedded artwork in audio files (using MediaMetadataRetriever)
     *
     * @param album Album model with songPaths
     * @return Bitmap of album cover or null if not found
     */
    fun loadAlbumCover(album: Album): Bitmap? {
        // Source 1: Check for external album art files (fastest)
        if (album.songPaths.isNotEmpty()) {
            val firstSongPath = album.songPaths.first()
            val directory = File(firstSongPath).parentFile

            if (directory != null && directory.exists()) {
                val externalArtwork = loadExternalAlbumArt(directory, album.name)
                if (externalArtwork != null) {
                    Log.d(TAG, "Loaded album art from external file for: ${album.name}")
                    return externalArtwork
                }
            }
        }

        // Source 2: Extract from audio file metadata (embedded artwork)
        if (album.songPaths.isNotEmpty()) {
            val embeddedArtwork = loadEmbeddedAlbumArt(album.songPaths)
            if (embeddedArtwork != null) {
                Log.d(TAG, "Loaded album art from embedded metadata for: ${album.name}")
                return embeddedArtwork
            }
        }

        return null
    }

    /**
     * Checks for common album art filenames in the audio directory.
     * Checks: folder.jpg, cover.jpg, album.jpg, front.jpg (and PNG variants)
     *
     * @param directory Directory containing audio files
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
     * Extracts embedded album artwork from audio files using MediaMetadataRetriever.
     * Checks files in order and returns the first one with embedded artwork.
     *
     * @param songPaths List of audio file paths
     * @return Bitmap or null if not found
     */
    private fun loadEmbeddedAlbumArt(songPaths: List<String>): Bitmap? {
        val retriever = MediaMetadataRetriever()

        try {
            // Check each audio file (up to first 5 for efficiency)
            for (path in songPaths.take(5)) {
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

    private const val TAG = "AlbumUtils"
}