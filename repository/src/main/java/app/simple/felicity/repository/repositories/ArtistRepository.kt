package app.simple.felicity.repository.repositories

import android.content.Context
import android.provider.MediaStore
import app.simple.felicity.repository.models.Artist

class ArtistRepository(private val context: Context) {

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
                artists.add(
                        Artist(
                                id = it.getLong(idCol),
                                artistName = it.getString(artistCol),
                                albumCount = it.getInt(albumCountCol),
                                trackCount = it.getInt(trackCountCol)
                        )
                )
            }
        }

        return artists
    }
}