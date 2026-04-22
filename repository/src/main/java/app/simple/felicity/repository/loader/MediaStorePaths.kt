package app.simple.felicity.repository.loader

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
     * Queries MediaStore for every audio file and returns two useful lookups bundled
     * together as a [Pair]:
     * - First: full absolute path → full absolute path (for exact path matching)
     * - Second: lowercase filename → full absolute path (for filename-only matching)
     *
     * Having both maps means we can handle any flavor of M3U path — whether the
     * playlist was created on another machine with a different root, or it just uses
     * a bare filename assuming all tracks live in the same folder.
     */
    @Suppress("DEPRECATION")
    fun Context.buildMediaStoreFilenameMap(): Pair<Map<String, String>, Map<String, String>> {
        val fullPathMap = HashMap<String, String>()
        val filenameMap = HashMap<String, String>()

        val projection = arrayOf(MediaStore.Audio.Media.DATA)

        try {
            contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    null
            )?.use { cursor ->
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val data = cursor.getString(dataCol) ?: continue
                    fullPathMap[data] = data

                    // The filename is the last segment — great for matching relative-path entries.
                    val filename = data.substringAfterLast('/').lowercase()
                    if (filename.isNotEmpty()) {
                        // First match wins, which handles duplicate filenames across folders gracefully.
                        filenameMap.putIfAbsent(filename, data)
                    }
                }

                Log.d(TAG, "Built MediaStore filename map with ${filenameMap.size} entries.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to build MediaStore filename map.", e)
        }

        return fullPathMap to filenameMap
    }

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
                    val data = cursor.getString(dataCol) ?: ""

                    Log.d(TAG, "MediaStore entry: title='$title', artist='$artist', album='$album', path='$data'")

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