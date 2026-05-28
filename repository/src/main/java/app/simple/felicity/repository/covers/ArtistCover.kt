package app.simple.felicity.repository.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.covers.MediaStoreCover.loadCoverFromMediaStore
import app.simple.felicity.repository.covers.MediaStoreCover.uriToBitmap
import app.simple.felicity.repository.models.Artist
import java.io.File

/**
 * Cover art loader for Artist collections.
 *
 * @author Hamza417
 */
object ArtistCover {
    private const val TAG = "ArtistCover"

    /**
     * A dedicated sub-folder inside the app's permanent files directory where
     * artist images are stored. Using [Context.getFilesDir] instead of the cache
     * dir means these images survive cache clears.
     */
    private const val ARTIST_IMAGES_DIR = "artist_images"

    /**
     * Loads the cover art for an artist, trying sources in order until one works.
     *
     * The lookup order is:
     * 1. A previously saved image on disk (in the app's files directory) — this is
     *    checked first so we avoid making a network request for something we already have.
     * 2. MusicBrainz → Wikipedia — fetches the artist's photo over the network when
     *    MusicBrainz is enabled and no saved image exists yet. The result is then
     *    saved to disk so future loads skip the network entirely.
     * 3. MediaStore's album art — a fast local fallback using the system's art cache.
     * 4. Artwork embedded inside the audio file's tags.
     *
     * Folder-based artwork (folder.jpg etc.) is skipped because song paths are
     * content URIs, and we can't walk up to a parent directory from a content URI.
     * MediaStore doesn't index artwork by artist either, so the first song acts
     * as a representative for the whole artist.
     *
     * @param context Android context used for file I/O, MediaStore, and SAF queries.
     * @param artist Artist model whose [Artist.songPaths] drive the lookup.
     * @return Bitmap of artist cover, or a placeholder if no artwork is found.
     */
    fun load(context: Context, artist: Artist): Bitmap {
        val artistName = artist.name ?: ""

        // Check the persistent files directory before touching the network.
        // This is the fastest path on subsequent loads and works completely offline.
        if (artistName.isNotBlank()) {
            val saved = loadFromFilesDir(context, artistName)
            if (saved != null) {
                Log.d(TAG, "Loaded artist art from saved files for: $artistName")
                return saved
            }
        }

        if (LibraryPreferences.isMusicBrainzEnabled() && artistName.isNotBlank()) {
            val musicBrainzArtwork = MusicBrainzArtistCover.fetchArtistImage(artistName)
            if (musicBrainzArtwork != null) {
                Log.d(TAG, "Loaded artist art from MusicBrainz/Wikipedia for: $artistName")
                // Persist the image so the next load reads from disk instead of the network.
                saveToFilesDir(context, artistName, musicBrainzArtwork)
                return musicBrainzArtwork
            }
        } else if (!LibraryPreferences.isMusicBrainzEnabled()) {
            Log.d(TAG, "MusicBrainz disabled, skipping network fetch for artist: $artistName")
        }

        if (artist.songPaths.isNotEmpty()) {
            if (LibraryPreferences.isUseMediaStoreArtwork()) {
                val uri = context.loadCoverFromMediaStore(artist.songPaths.first())
                val mediaStoreBitmap = uri?.let { context.uriToBitmap(it) }
                if (mediaStoreBitmap != null) {
                    Log.d(TAG, "Loaded artist art from MediaStore for: $artistName")
                    return mediaStoreBitmap
                }
            }

            val embeddedArtwork = BaseCoverLoader.loadEmbeddedArtworkFromPaths(context, artist.songPaths)
            if (embeddedArtwork != null) {
                Log.d(TAG, "Loaded artist art from embedded metadata for: $artistName")
                return embeddedArtwork
            }
        }

        return BaseCoverLoader.loadEmptyAudioCover()
    }

    /**
     * Reads a previously saved artist image from the permanent files directory.
     * Returns null if no image has been saved for this artist yet.
     *
     * @param context Used to locate the app's private files directory.
     * @param artistName The display name of the artist, used as the filename.
     * @return The decoded [Bitmap], or null if the file doesn't exist or can't be decoded.
     */
    private fun loadFromFilesDir(context: Context, artistName: String): Bitmap? {
        return try {
            val file = artistImageFile(context, artistName)
            if (!file.exists()) return null
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read saved artist image for: $artistName", e)
            null
        }
    }

    /**
     * Saves an artist image to the permanent files directory so it can be reused on
     * future loads without hitting the network again.
     *
     * We use PNG format to keep the image lossless. The file is written atomically
     * by first writing to a temp file and then renaming, so a half-written file can
     * never end up being read.
     *
     * @param context Used to locate the app's private files directory.
     * @param artistName The display name of the artist, used as the filename.
     * @param bitmap The image to save.
     */
    /**
     * Saves a user-chosen image for an artist, overwriting any previously stored one.
     * This is the public entry point used when the user manually picks an image from
     * their gallery. Under the hood it behaves exactly like the automatic MusicBrainz save.
     *
     * @param context Used to locate the app's private files directory.
     * @param artistName The display name of the artist, used as the filename.
     * @param bitmap The image picked by the user.
     */
    fun saveUserPickedImage(context: Context, artistName: String, bitmap: Bitmap) {
        saveToFilesDir(context, artistName, bitmap)
    }

    private fun saveToFilesDir(context: Context, artistName: String, bitmap: Bitmap) {
        try {
            val dir = File(context.filesDir, ARTIST_IMAGES_DIR)
            if (!dir.exists()) dir.mkdirs()

            val tempFile = File(dir, "${sanitizeFileName(artistName)}.tmp")
            val finalFile = artistImageFile(context, artistName)

            tempFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // Rename from temp to final — this is atomic on most filesystems and
            // prevents a partial file from being read if the app is killed mid-write.
            tempFile.renameTo(finalFile)
            Log.d(TAG, "Saved artist image to files dir for: $artistName")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save artist image for: $artistName", e)
        }
    }

    /**
     * Builds the [File] handle for an artist's saved image inside the files directory.
     * Does not check whether the file actually exists.
     */
    private fun artistImageFile(context: Context, artistName: String): File {
        val dir = File(context.filesDir, ARTIST_IMAGES_DIR)
        return File(dir, "${sanitizeFileName(artistName)}.png")
    }

    /**
     * Strips characters from an artist name that aren't safe to use in a filename.
     * Spaces are replaced with underscores and anything that isn't a letter, digit,
     * hyphen, or underscore is dropped.
     *
     * @param name The raw artist name.
     * @return A filename-safe version of the name, falling back to "unknown" if the
     *         result would be empty.
     */
    private fun sanitizeFileName(name: String): String {
        val sanitized = name.trim()
            .replace(' ', '_')
            .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        return sanitized.ifEmpty { "unknown" }
    }
}
