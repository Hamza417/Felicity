package app.simple.felicity.repository.repositories

import android.content.ContentUris
import android.content.Context
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

        // Pre-fetch a map of artistId -> albumId
        val artistAlbumMap = mutableMapOf<Long, Long>()
        val albumCursor = context.contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ARTIST_ID),
                null,
                null,
                null
        )
        albumCursor?.use { ac ->
            val albumIdCol = ac.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val artistIdCol = ac.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST_ID)
            while (ac.moveToNext()) {
                val albumId = ac.getLong(albumIdCol)
                val artistId = ac.getLong(artistIdCol)
                // Only keep the first albumId for each artistId
                if (!artistAlbumMap.containsKey(artistId)) {
                    artistAlbumMap[artistId] = albumId
                }
            }
        }

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
                val albumId = artistAlbumMap[artistId]
                val artworkUri = albumId?.let {
                    ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, it)
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

    fun fetchArtistDetails(artistId: Long): Artist? {
        val projection = arrayOf(
                MediaStore.Audio.Artists._ID,
                MediaStore.Audio.Artists.ARTIST,
                MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
                MediaStore.Audio.Artists.NUMBER_OF_TRACKS
        )

        val selection = "${MediaStore.Audio.Artists._ID} = ?"
        val selectionArgs = arrayOf(artistId.toString())

        context.contentResolver.query(
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)
                val albumCountCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_ALBUMS)
                val trackCountCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)

                // Fetch first albumId for this artist
                val albumCursor = context.contentResolver.query(
                        MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Audio.Albums._ID),
                        "${MediaStore.Audio.Albums.ARTIST_ID} = ?",
                        arrayOf(artistId.toString()),
                        null
                )
                val artworkUri = albumCursor?.use { ac ->
                    if (ac.moveToFirst()) {
                        val albumId = ac.getLong(ac.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID))
                        ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
                    } else null
                }

                return Artist(
                        id = cursor.getLong(idCol),
                        artistName = cursor.getString(artistCol),
                        albumCount = cursor.getInt(albumCountCol),
                        trackCount = cursor.getInt(trackCountCol),
                        artworkUri = artworkUri
                )
            }
        }

        return null
    }
}