package app.simple.felicity.repository.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

object ArtistUtils {
    fun getArtistArtworkUri(context: Context, artistId: Long): Uri? {
        // Pre-fetch first albumId for the artist
        val albumCursor = context.contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Albums._ID),
                "${MediaStore.Audio.Albums.ARTIST_ID} = ?",
                arrayOf(artistId.toString()),
                null
        )
        val albumId = albumCursor?.use { ac ->
            if (ac.moveToFirst()) {
                ac.getLong(ac.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID))
            } else null
        }
        return albumId?.let {
            ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, it)
        }
    }
}