package app.simple.felicity.repository.repositories

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import app.simple.felicity.repository.models.Album

class AlbumRepository(private val context: Context) {

    @SuppressLint("InlinedApi")
    fun fetchAlbums(): List<Album> {
        val albums = mutableListOf<Album>()
        val projection = arrayOf(
                MediaStore.Audio.Albums._ID,
                MediaStore.Audio.Albums.ALBUM,
                MediaStore.Audio.Albums.ARTIST,
                MediaStore.Audio.Albums.ARTIST_ID,
                MediaStore.Audio.Albums.ALBUM_ART,
                MediaStore.Audio.Albums.NUMBER_OF_SONGS
        )

        val cursor = context.contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
        )

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            val artistIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST_ID)
            val albumArtCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART)
            val songCountCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)

            while (it.moveToNext()) {
                val albumId = it.getLong(idCol)
                val artworkUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                        albumId
                ).toString()

                albums.add(
                        Album(
                                id = albumId,
                                name = it.getString(albumCol),
                                artist = it.getString(artistCol),
                                artistId = it.getLong(artistIdCol),
                                artworkUri = artworkUri,
                                songCount = it.getInt(songCountCol)
                        )
                )
            }
        }

        return albums
    }
}