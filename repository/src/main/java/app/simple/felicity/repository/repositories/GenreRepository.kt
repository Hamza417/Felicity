@file:Suppress("DEPRECATION")

package app.simple.felicity.repository.repositories

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song
import javax.inject.Inject

public class GenreRepository @Inject constructor(private val context: Context) {

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

    fun fetchGenreSongs(genreId: Long): List<Song> {
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
                MediaStore.Audio.Media.DATE_MODIFIED
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
                val path = it.getString(dataCol)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)

                songs.add(
                        Song(
                                id = songId,
                                title = it.getString(titleCol),
                                artist = it.getString(artistCol),
                                album = it.getString(albumCol),
                                albumId = albumId,
                                artistId = it.getLong(artistIdCol),
                                uri = uri,
                                path = path,
                                duration = it.getLong(durationCol),
                                size = it.getLong(sizeCol),
                                dateAdded = it.getLong(dateAddedCol),
                                dateModified = it.getLong(dateModifiedCol)
                        )
                )
            }
        }

        return songs
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

    fun fetchAlbumsInGenre(genreId: Long): List<Album> {
        val albums = mutableListOf<Album>()
        val albumIds = mutableSetOf<Long>()
        val albumIdToArtistId = mutableMapOf<Long, Long>()

        // Step 1: Get unique album IDs and their artist IDs from songs in the genre
        val songCursor = context.contentResolver.query(
                MediaStore.Audio.Genres.Members.getContentUri("external", genreId),
                arrayOf(MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.ARTIST_ID),
                null,
                null,
                null
        )

        songCursor?.use {
            val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val artistIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            while (it.moveToNext()) {
                val albumId = it.getLong(albumIdCol)
                val artistId = it.getLong(artistIdCol)
                albumIds.add(albumId)
                albumIdToArtistId[albumId] = artistId
            }
        }

        if (albumIds.isEmpty()) return albums

        // Step 2: Query albums table for all IDs at once
        val selection = "${MediaStore.Audio.Albums._ID} IN (${albumIds.joinToString(",")})"
        val albumCursor = context.contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                arrayOf(
                        MediaStore.Audio.Albums._ID,
                        MediaStore.Audio.Albums.ALBUM,
                        MediaStore.Audio.Albums.ARTIST
                ),
                selection,
                null,
                null
        )

        albumCursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            while (it.moveToNext()) {
                val albumId = it.getLong(idCol)
                albums.add(
                        Album(
                                id = albumId,
                                name = it.getString(nameCol),
                                artist = it.getString(artistCol),
                                artistId = albumIdToArtistId[albumId] ?: 0L,
                                songCount = 0
                        )
                )
            }
        }

        Log.d(TAG, "fetchAlbumsInGenre: Fetched ${albums.size} albums for genre ID $genreId")
        return albums
    }

    fun fetchArtistsInGenre(genreId: Long): List<Long> {
        val artists = mutableSetOf<Long>()
        val projection = arrayOf(MediaStore.Audio.Media.ARTIST_ID)

        val genreUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)

        val cursor = context.contentResolver.query(
                genreUri,
                projection,
                null,
                null,
                null
        )

        cursor?.use {
            val artistIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            while (it.moveToNext()) {
                artists.add(it.getLong(artistIdCol))
            }
        }

        return artists.toList()
    }

    fun fetchGenreByArtist(id: Long): List<Genre> {
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

        return genres
    }

    fun fetchGenresForAlbum(albumId: Long): List<Genre> {
        val genres = mutableListOf<Genre>()
        val songIds = mutableListOf<Long>()

        // Get all song IDs for the album
        val songCursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.ALBUM_ID}=?",
                arrayOf(albumId.toString()),
                null
        )

        songCursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            while (it.moveToNext()) {
                songIds.add(it.getLong(idCol))
            }
        }

        if (songIds.isEmpty()) return genres

        // Get all genres
        val genreCursor = context.contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null,
                null,
                null
        )

        genreCursor?.use {
            val genreIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
            val genreNameCol = it.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
            while (it.moveToNext()) {
                val genreId = it.getLong(genreIdCol)
                val genreName = it.getString(genreNameCol)
                // Query all song IDs at once for this genre
                val selection = "${MediaStore.Audio.Media._ID} IN (${songIds.joinToString(",")})"
                val memberCursor = context.contentResolver.query(
                        MediaStore.Audio.Genres.Members.getContentUri("external", genreId),
                        arrayOf(MediaStore.Audio.Media._ID),
                        selection,
                        null,
                        null
                )
                memberCursor?.use { mc ->
                    if (mc.moveToFirst()) {
                        genres.add(Genre(id = genreId, name = genreName))
                    }
                }
            }
        }

        return genres
    }
}