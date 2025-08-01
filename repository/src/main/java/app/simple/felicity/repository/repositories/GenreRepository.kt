@file:Suppress("DEPRECATION")

package app.simple.felicity.repository.repositories

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song

class GenreRepository(private val context: Context) {

    companion object {
        private const val TAG = "GenreRepository"
    }

    fun fetchGenres(): List<Genre> {
        val genres = mutableListOf<Genre>()
        val projection = arrayOf(
                MediaStore.Audio.Genres._ID,
                MediaStore.Audio.Genres.NAME
        )

        val cursor = context.contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
        )

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)

            while (it.moveToNext()) {
                genres.add(
                        Genre(
                                id = it.getLong(idCol),
                                name = it.getString(nameCol)
                        )
                )
            }
        }

        Log.d(TAG, "fetchGenres: Fetched ${genres.size} genres")
        return genres
    }

    // Fetch songs for a specific genreId and only those with album art
    fun fetchSongsWithGenreAndAlbumArt(genreId: Long): List<Song> {
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

        val genreUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)

        val cursor = context.contentResolver.query(
                genreUri,
                projection,
                null,
                null,
                null
        )

        cursor?.use {
            val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            // ... other column indices

            while (it.moveToNext()) {
                val albumId = it.getLong(albumIdCol)

                // Query album art for this album
                val albumCursor = context.contentResolver.query(
                        MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Audio.Albums.ALBUM_ART),
                        "${MediaStore.Audio.Albums._ID}=?",
                        arrayOf(albumId.toString()),
                        null
                )

                var albumArt: String? = null
                albumCursor?.use { ac ->
                    if (ac.moveToFirst()) {
                        albumArt = ac.getString(ac.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART))
                    }
                }

                if (!albumArt.isNullOrEmpty()) {
                    // Add your Song object here as in your existing code
                    // Fill in the rest of the Song fields as needed
                }
            }
        }

        return songs
    }

    /**
     * Fetches album art URIs for a specific genre.
     *
     * @param genreId The ID of the genre for which to fetch album art URIs.
     * @param count The number of album art URIs to return (default is 4)
     */
    fun fetchAlbumArtUrisForGenre(genreId: Long, count: Int = 4): List<String> {
        val albumArtUris = mutableListOf<String>()
        val seenAlbumIds = mutableSetOf<Long>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.ALBUM_ID)
        val genreUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)

        val cursor = context.contentResolver.query(
                genreUri,
                projection,
                null,
                null,
                null
        )

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (it.moveToNext()) {
                val songId = it.getLong(idCol)
                val albumId = it.getLong(albumIdCol)
                if (albumId in seenAlbumIds) continue
                seenAlbumIds.add(albumId)

                val artworkUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId).toString()
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
                    artPath?.toUri()?.toString()
                }

                if (!artworkUri.isNullOrEmpty()) {
                    albumArtUris.add(artworkUri)
                }

                if (albumArtUris.size >= count) break
            }
        }

        Log.d("GenreRepository", "fetchAlbumArtUrisForGenre: Fetched ${albumArtUris.size} album art URIs for genre ID $genreId")
        return albumArtUris
    }
}