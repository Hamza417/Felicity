package app.simple.felicity.repository.repositories

import android.annotation.SuppressLint
import android.content.Context
import android.provider.MediaStore
import app.simple.felicity.repository.models.Album
import javax.inject.Inject

class AlbumRepository @Inject constructor(private val context: Context) {

    @SuppressLint("InlinedApi")
    fun fetchAlbums(): List<Album> {
        val albums = mutableListOf<Album>()
        val projection = arrayOf(
                MediaStore.Audio.Albums._ID,
                MediaStore.Audio.Albums.ALBUM,
                MediaStore.Audio.Albums.ARTIST,
                MediaStore.Audio.Albums.ARTIST_ID,
                MediaStore.Audio.Albums.NUMBER_OF_SONGS,
                MediaStore.Audio.Albums.FIRST_YEAR,
                MediaStore.Audio.Albums.LAST_YEAR
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
            val songCountCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)
            val firstYearCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums.FIRST_YEAR)
            val lastYearCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums.LAST_YEAR)

            while (it.moveToNext()) {
                val albumId = it.getLong(idCol)
                albums.add(
                        Album(
                                id = albumId,
                                name = it.getString(albumCol),
                                artist = it.getString(artistCol),
                                artistId = it.getLong(artistIdCol),
                                songCount = it.getInt(songCountCol),
                                firstYear = it.getLong(firstYearCol),
                                lastYear = it.getLong(lastYearCol)
                        )
                )
            }
        }

        return albums
    }

    fun fetchAlbumsFromArtist(id: Long): MutableList<Album> {
        val projection = arrayOf(
                MediaStore.Audio.Albums._ID,
                MediaStore.Audio.Albums.ALBUM,
                MediaStore.Audio.Albums.ARTIST,
                MediaStore.Audio.Albums.ARTIST_ID,
                MediaStore.Audio.Albums.NUMBER_OF_SONGS,
                MediaStore.Audio.Albums.FIRST_YEAR,
                MediaStore.Audio.Albums.LAST_YEAR
        )

        val selection = "${MediaStore.Audio.Albums.ARTIST_ID} = ?"
        val selectionArgs = arrayOf(id.toString())

        val cursor = context.contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
        )

        val albums = mutableListOf<Album>()

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            val artistIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST_ID)
            val songCountCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)
            val firstYearCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums.FIRST_YEAR)
            val lastYearCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums.LAST_YEAR)

            while (it.moveToNext()) {
                val albumId = it.getLong(idCol)
                albums.add(
                        Album(
                                id = albumId,
                                name = it.getString(albumCol),
                                artist = it.getString(artistCol),
                                artistId = it.getLong(artistIdCol),
                                songCount = it.getInt(songCountCol),
                                firstYear = it.getLong(firstYearCol),
                                lastYear = it.getLong(lastYearCol)
                        )
                )
            }
        }

        return albums
    }
}