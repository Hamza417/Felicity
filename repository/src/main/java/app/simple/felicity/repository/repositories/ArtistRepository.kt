package app.simple.felicity.repository.repositories

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import app.simple.felicity.repository.models.Artist
import javax.inject.Inject

class ArtistRepository @Inject constructor(private val context: Context) {

    fun fetchArtists(): List<Artist> {
        val artists = mutableListOf<Artist>()
        val projection = arrayOf(
                MediaStore.Audio.Artists._ID,
                MediaStore.Audio.Artists.ARTIST,
                MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
                MediaStore.Audio.Artists.NUMBER_OF_TRACKS
        )

        val cursor = context.contentResolver.query(
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
        )

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)
            val albumCountCol = it.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_ALBUMS)
            val trackCountCol = it.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)

            while (it.moveToNext()) {
                val artistId = it.getLong(idCol)
                var artworkUri: Uri? = null

                // Query for one album of this artist to get album art
                val albumCursor = context.contentResolver.query(
                        MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Audio.Albums._ID),
                        MediaStore.Audio.Albums.ARTIST_ID + "=?",
                        arrayOf(artistId.toString()),
                        null
                )
                albumCursor?.use { ac ->
                    if (ac.moveToFirst()) {
                        val albumId = ac.getLong(0)
                        artworkUri = ContentUris.withAppendedId(
                                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                                albumId
                        )
                    }
                }

                artists.add(
                        Artist(
                                id = artistId,
                                artistName = it.getString(artistCol),
                                albumCount = it.getInt(albumCountCol),
                                trackCount = it.getInt(trackCountCol),
                                artworkUri = artworkUri
                        )
                )
            }
        }

        return artists
    }
}