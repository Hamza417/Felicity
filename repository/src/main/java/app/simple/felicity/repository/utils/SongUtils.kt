package app.simple.felicity.repository.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri

object SongUtils {
    fun getArtworkUri(context: Context, albumId: Long, songId: Long): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
        } else {
            var artPath: String? = null
            val albumCursor = context.contentResolver.query(
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Albums.ALBUM_ART),
                    "${MediaStore.Audio.Albums._ID}=?",
                    arrayOf(albumId.toString()),
                    null
            )
            albumCursor?.use { ac ->
                if (ac.moveToFirst()) {
                    artPath = ac.getString(0)
                }
            }
            artPath?.toUri()
        }
    }
}