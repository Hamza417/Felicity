package app.simple.felicity.loaders

import android.annotation.SuppressLint
import android.content.Context
import android.provider.MediaStore
import app.simple.felicity.models.Album
import app.simple.felicity.models.Artist
import app.simple.felicity.models.Audio
import app.simple.felicity.models.Genre

object MediaLoader {

    private const val SELECTION = MediaStore.Audio.Media.IS_MUSIC + " != 0"

    private val AUDIO_PROJECTION =
        arrayOf(MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DATE_TAKEN,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.BITRATE,
                MediaStore.Audio.Media.COMPOSER)

    @SuppressLint("InlinedApi")
    private val ALBUM_PROJECTION =
        arrayOf(MediaStore.Audio.Albums._ID,
                MediaStore.Audio.Albums.ALBUM_ID,
                MediaStore.Audio.Albums.ALBUM,
                MediaStore.Audio.Albums.ARTIST_ID,
                MediaStore.Audio.Albums.ARTIST,
                MediaStore.Audio.Albums.NUMBER_OF_SONGS,
                MediaStore.Audio.Albums.NUMBER_OF_SONGS_FOR_ARTIST,
                MediaStore.Audio.Albums.FIRST_YEAR,
                MediaStore.Audio.Albums.LAST_YEAR)

    private val ARTIST_PROJECTION =
        arrayOf(MediaStore.Audio.Artists._ID,
                MediaStore.Audio.Artists.ARTIST,
                MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
                MediaStore.Audio.Artists.NUMBER_OF_TRACKS)

    private val GENRE_PROJECTION =
        arrayOf(MediaStore.Audio.Genres._ID,
                MediaStore.Audio.Genres.NAME)

    fun Context.loadAudios(sortOrder: String = "LOWER (" + MediaStore.Audio.Media.TITLE + ") ASC"): ArrayList<Audio> {
        val audios = ArrayList<Audio>()
        val cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                AUDIO_PROJECTION, SELECTION,
                null, sortOrder)

        if (cursor != null && cursor.moveToFirst()) {
            do {
                audios.add(Audio(cursor))
            } while (cursor.moveToNext())

            cursor.close()
        }

        return audios
    }

    fun Context.loadAlbums(sortOrder: String = "LOWER (" + MediaStore.Audio.Albums.ALBUM + ") ASC"): ArrayList<Album> {
        val albums = ArrayList<Album>()
        val cursor = contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, ALBUM_PROJECTION,
                null, null, sortOrder)

        if (cursor != null && cursor.moveToFirst()) {
            do {
                albums.add(Album(cursor))
            } while (cursor.moveToNext())

            cursor.close()
        }

        return albums
    }

    fun Context.loadArtists(sortOrder: String = "LOWER (" + MediaStore.Audio.Artists.ARTIST + ") ASC"): ArrayList<Artist> {
        val artists = ArrayList<Artist>()
        val cursor = contentResolver.query(
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, ARTIST_PROJECTION,
                null, null, sortOrder)

        if (cursor != null && cursor.moveToFirst()) {
            do {
                artists.add(Artist(cursor))
            } while (cursor.moveToNext())

            cursor.close()
        }

        return artists
    }

    fun Context.loadGenres(sortOrder: String = "LOWER (" + MediaStore.Audio.Genres.NAME + ") ASC"): ArrayList<Genre> {
        val genres = ArrayList<Genre>()
        val cursor = contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI, GENRE_PROJECTION,
                null, null, sortOrder)

        if (cursor != null && cursor.moveToFirst()) {
            do {
                genres.add(Genre(cursor))
            } while (cursor.moveToNext())

            cursor.close()
        }

        return genres
    }

    @SuppressLint("InlinedApi")
    fun Context.loadSongsForGenre(genreId: Long): ArrayList<Audio> {
        val audios = ArrayList<Audio>()
        val uri = MediaStore.Audio.Genres.Members.getContentUri(MediaStore.VOLUME_EXTERNAL, genreId)
        val cursor = contentResolver.query(
                uri,
                AUDIO_PROJECTION,
                null,
                null,
                null
        )

        if (cursor != null && cursor.moveToFirst()) {
            do {
                audios.add(Audio(cursor))
            } while (cursor.moveToNext())

            cursor.close()
        }

        return audios
    }
}
