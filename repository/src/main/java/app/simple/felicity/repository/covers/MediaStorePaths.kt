package app.simple.felicity.repository.covers

import android.content.Context
import android.provider.MediaStore
import android.util.Log

/**
 * Resolves real filesystem paths for audio files by cross-referencing the app's
 * local database with a single bulk query to MediaStore.
 *
 * Since SAF document URIs can't be queried for the DATA column directly, we take
 * a different approach: ask MediaStore for every audio file it knows about
 * (title, artist, album, path), build an in-memory lookup table, then match each
 * audio row in our DB using the same three tags. One ContentResolver query for
 * the entire library — fast and reliable.
 *
 * @author Hamza417
 */
object MediaStorePaths {

    private const val TAG = "MediaStorePaths"

    /**
     * Queries MediaStore for every audio file and returns a map of
     * (title, artist, album) → absolute filesystem path.
     *
     * All three keys are lower-cased so the lookup is case-insensitive — MediaStore
     * and the tag parser occasionally disagree on casing, and this keeps them in sync.
     * Null tag values become empty strings so every audio file gets a key.
     */
    @Suppress("DEPRECATION")
    fun Context.buildMediaStorePathMap(): Map<Triple<String, String, String>, String> {
        val map = HashMap<Triple<String, String, String>, String>()

        val projection = arrayOf(
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA
        )

        try {
            contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    null
            )?.use { cursor ->
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val title = cursor.getString(titleCol)?.lowercase() ?: ""
                    val artist = cursor.getString(artistCol)?.lowercase() ?: ""
                    val album = cursor.getString(albumCol)?.lowercase() ?: ""
                    val data = cursor.getString(dataCol) ?: continue

                    map[Triple(title, artist, album)] = data
                }

                Log.d(TAG, "Built path map with ${map.size} entries from MediaStore.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to build MediaStore path map — folder hierarchy may be empty.", e)
        }

        return map
    }
}
