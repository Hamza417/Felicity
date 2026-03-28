package app.simple.felicity.repository.covers

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

object MediaStoreCover {
    fun Context.loadCoverFromMediaStore(path: String): Uri? {
        val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media.ALBUM_ID)

        // The DATA column is technically deprecated in Android 10+,
        // but it is still the standard way to query by file path.
        val selection = "${MediaStore.Audio.Media.DATA} = ?"
        val selectionArgs = arrayOf(path)

        var albumId: Long? = null

        // Query the content resolver
        contentResolver.query(
                audioUri,
                projection,
                selection,
                selectionArgs,
                null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                albumId = cursor.getLong(albumIdColumn)
            }
        }

        // Construct and return the album art URI if we found an ID
        return albumId?.let { id ->
            val artworkUri = Uri.parse("content://media/external/audio/albumart")
            ContentUris.withAppendedId(artworkUri, id)
        }
    }

    fun Context.uriToBitmap(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e("MediaStoreCover", "Failed to load bitmap from URI: $uri", e)
            null
        }
    }

    fun Uri.toBitmap(context: Context): Bitmap? {
        return context.uriToBitmap(this)
    }
}