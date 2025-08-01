@file:Suppress("DEPRECATION")

package app.simple.felicity.repository.repositories

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import app.simple.felicity.repository.models.Song

class SongRepository(private val context: Context) {

    fun fetchSongs(): List<Song> {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATE_MODIFIED,
        )

        val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
        )

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val artistIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateAddedCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (it.moveToNext()) {
                val songId = it.getLong(idCol)
                val albumId = it.getLong(albumIdCol)
                val trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)

                val artworkUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    trackUri
                } else {
                    // For below Android 10, query ALBUM_ART column
                    var artPath: Uri? = null
                    val albumCursor = context.contentResolver.query(
                        MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Audio.Albums.ALBUM_ART),
                        MediaStore.Audio.Albums._ID + "=?",
                        arrayOf(albumId.toString()),
                        null
                    )
                    albumCursor?.use { ac ->
                        if (ac.moveToFirst()) {
                            artPath = ac.getString(0).toUri()
                        }
                    }

                    artPath
                }

                songs.add(
                        Song(
                                id = songId,
                                title = it.getString(titleCol),
                                artist = it.getString(artistCol),
                                album = it.getString(albumCol),
                                albumId = albumId,
                                artistId = it.getLong(artistIdCol),
                                uri = trackUri,
                                path = it.getString(dataCol),
                                duration = it.getLong(durationCol),
                                size = it.getLong(sizeCol),
                                dateAdded = it.getLong(dateAddedCol),
                                dateModified = it.getLong(dateModifiedCol),
                                artworkUri = artworkUri
                        )
                )
            }
        }

        return songs
    }
}