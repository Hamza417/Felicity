package app.simple.felicity.repository.repositories

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import app.simple.felicity.repository.models.Song
import javax.inject.Inject

@Suppress("DEPRECATION")
class SongRepository @Inject constructor(private val context: Context) {

    /**
     * Fetches all songs from the device's external storage.
     *
     * @return List of [Song] objects representing the songs found.
     */
    fun fetchSongs(): List<Song> = querySongs()

    /**
     * Fetches songs by a specific artist ID.
     *
     * @param id The ID of the artist.
     * @return List of [Song] objects representing the songs by the specified artist.
     */
    fun fetchSongByArtist(id: Long): List<Song> =
        querySongs(
                selection = "${MediaStore.Audio.Media.ARTIST_ID} = ?",
                selectionArgs = arrayOf(id.toString())
        )

    /**
     * Fetches songs by a specific album ID.
     *
     * @param id The ID of the album.
     * @return List of [Song] objects representing the songs in the specified album.
     */
    fun fetchSongsByAlbum(id: Long): List<Song> =
        querySongs(
                selection = "${MediaStore.Audio.Media.ALBUM_ID} = ?",
                selectionArgs = arrayOf(id.toString())
        )

    fun fetchRecentSongs(limit: Int): List<Song> =
        querySongs(
                sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC",
                limit = limit
        )

    // region Internal Query Logic
    private val audioUri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    private val projection: Array<String> = buildList {
        add(MediaStore.Audio.Media._ID)
        add(MediaStore.Audio.Media.TITLE)
        add(MediaStore.Audio.Media.ARTIST)
        add(MediaStore.Audio.Media.ALBUM)
        add(MediaStore.Audio.Media.ALBUM_ID)
        add(MediaStore.Audio.Media.ARTIST_ID)
        add(MediaStore.Audio.Media.DATA) // file path
        add(MediaStore.Audio.Media.DURATION)
        add(MediaStore.Audio.Media.SIZE)
        add(MediaStore.Audio.Media.DATE_ADDED)
        add(MediaStore.Audio.Media.DATE_MODIFIED)
        add(MediaStore.Audio.Media.TRACK)
        add(MediaStore.Audio.Media.COMPOSER)
        add(MediaStore.Audio.Media.YEAR)
        // API dependent / optional columns below (safe get with index check)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Some devices expose BITRATE, IS_MUSIC; keep optional
            try {
                add(MediaStore.Audio.Media.BITRATE)
            } catch (_: Exception) {
            }
            try {
                add(MediaStore.Audio.Media.GENRE)
            } catch (_: Exception) {
            }
        }
        add(MediaStore.Audio.Media.IS_MUSIC)
    }.toTypedArray()

    private data class ColumnIndices(
            val id: Int,
            val title: Int,
            val artist: Int,
            val album: Int,
            val albumId: Int,
            val artistId: Int,
            val data: Int,
            val duration: Int,
            val size: Int,
            val dateAdded: Int,
            val dateModified: Int,
            val track: Int,
            val composer: Int,
            val year: Int,
            val bitrate: Int?,
            val isMusic: Int?,
            val genre: Int?
    )

    private fun Cursor.buildIndices(): ColumnIndices = ColumnIndices(
            id = getColumnIndexOrThrow(MediaStore.Audio.Media._ID),
            title = getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE),
            artist = getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST),
            album = getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM),
            albumId = getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID),
            artistId = getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID),
            data = getColumnIndexOrThrow(MediaStore.Audio.Media.DATA),
            duration = getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION),
            size = getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE),
            dateAdded = getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED),
            dateModified = getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED),
            track = getColumnIndex(MediaStore.Audio.Media.TRACK),
            composer = getColumnIndex(MediaStore.Audio.Media.COMPOSER),
            year = getColumnIndex(MediaStore.Audio.Media.YEAR),
            bitrate = getColumnIndex(MediaStore.Audio.Media.BITRATE).takeIf { it >= 0 },
            isMusic = getColumnIndex(MediaStore.Audio.Media.IS_MUSIC).takeIf { it >= 0 },
            genre = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) getColumnIndex(MediaStore.Audio.Media.GENRE).takeIf { it >= 0 } else null
    )

    private fun Cursor.buildSong(cols: ColumnIndices): Song {
        val id = getLong(cols.id)
        val albumId = getLong(cols.albumId)
        val uri = ContentUris.withAppendedId(audioUri, id)
        return Song(
                id = id,
                title = getString(cols.title),
                artist = getString(cols.artist),
                album = getString(cols.album),
                albumId = albumId,
                artistId = getLong(cols.artistId),
                uri = uri,
                path = getString(cols.data),
                duration = getLong(cols.duration),
                size = getLong(cols.size),
                dateAdded = getLong(cols.dateAdded),
                dateModified = getLong(cols.dateModified),
                genre = cols.genre?.let { if (it >= 0) getString(it) else null },
                trackNumber = cols.track.takeIf { it >= 0 }?.let { getInt(it) },
                composer = cols.composer.takeIf { it >= 0 }?.let { getString(it) },
                year = cols.year.takeIf { it >= 0 }?.let { getInt(it) },
                bitrate = cols.bitrate?.let { if (it >= 0) getInt(it) else null },
                isMusic = cols.isMusic?.let { if (it >= 0) getInt(it) == 1 else true } ?: true
        )
    }

    private fun querySongs(
            selection: String? = null,
            selectionArgs: Array<String>? = null,
            sortOrder: String? = null,
            limit: Int? = null
    ): List<Song> {
        val songs = ArrayList<Song>()
        // For API 30+ we could use a Bundle to support limit natively; pre-30 we post-filter.
        val cursor = try {
            context.contentResolver.query(
                    audioUri,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
            )
        } catch (_: SecurityException) { // Permissions not granted
            null
        } catch (_: Exception) { // Defensive catch for vendor quirks
            null
        }

        cursor?.use { c ->
            if (c.count == 0) return emptyList()
            val cols = c.buildIndices()
            var count = 0
            while (c.moveToNext()) {
                val song = c.buildSong(cols)
                // Filter out obvious non-music files if isMusic flag available
                if (!song.isMusic) continue
                songs.add(song)
                count++
                if (limit != null && count >= limit) break
            }
        }
        return songs
    }
}