package app.simple.felicity.repository.covers

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri

object MediaStoreCover {

    /**
     * Resolves the album art URI for a given audio file path or content URI.
     *
     * When the app uses SAF, song paths are content URIs like
     * `content://media/external/audio/media/123`. We can query that URI directly
     * instead of going through the deprecated DATA column. Both styles are
     * handled here so nothing falls through the cracks.
     *
     * @param path Either a file path or a content:// URI string for the audio file.
     * @return A URI pointing to the album art, or null if MediaStore has nothing for it.
     */
    fun Context.loadCoverFromMediaStore(path: String): Uri? {
        val projection = arrayOf(MediaStore.Audio.Media.ALBUM_ID)
        var albumId: Long? = null

        if (path.startsWith("content://")) {
            // The path is already a content URI — query it directly so we don't
            // have to rely on the deprecated DATA column at all.
            val audioUri = path.toUri()

            try {
                contentResolver.query(audioUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val col = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                        if (col >= 0) albumId = cursor.getLong(col)
                    }
                }
            } catch (e: Exception) {
                Log.w("MediaStoreCover", "Failed to query album ID from URI: $path", e)
            }
        } else {
            // Legacy file path — use the DATA column (still works on older devices).
            val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val selection = "${MediaStore.Audio.Media.DATA} = ?"
            contentResolver.query(audioUri, projection, selection, arrayOf(path), null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val col = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                    if (col >= 0) albumId = cursor.getLong(col)
                }
            }
        }

        // Turn the album ID into the well-known album art content URI.
        return albumId?.let { id ->
            val artworkUri = "content://media/external/audio/albumart".toUri()
            ContentUris.withAppendedId(artworkUri, id)
        }
    }

    fun Context.uriToBitmap(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (_: Exception) {
            Log.e("MediaStoreCover", "Failed to load bitmap from URI: $uri")
            null
        }
    }

    fun Uri.toBitmap(context: Context): Bitmap? {
        return context.uriToBitmap(this)
    }
}