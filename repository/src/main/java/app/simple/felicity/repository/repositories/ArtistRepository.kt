package app.simple.felicity.repository.repositories

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

                artists.add(
                        Artist(
                                id = artistId,
                                name = it.getString(artistCol),
                                albumCount = it.getInt(albumCountCol),
                                trackCount = it.getInt(trackCountCol)
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

                return Artist(
                        id = cursor.getLong(idCol),
                        name = cursor.getString(artistCol),
                        albumCount = cursor.getInt(albumCountCol),
                        trackCount = cursor.getInt(trackCountCol)
                )
            }
        }

        return null
    }

    fun fetchCollaboratorArtists(currentArtist: Artist): List<Artist> {
        val name = currentArtist.name ?: return emptyList()
        val delimiters = arrayOf("&", "ft.", "feat.", ",", "and")
        val regex = delimiters.joinToString("|") { Regex.escape(it) }.toRegex(RegexOption.IGNORE_CASE)

        fun normalizeArtistName(s: String) = s.trim().lowercase()

        val currentArtistName = normalizeArtistName(name)
        val collaboratorNames = mutableSetOf<String>()
        val projection = arrayOf(MediaStore.Audio.Media.ARTIST)

        context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
        )?.use { cursor ->
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (cursor.moveToNext()) {
                val artistField = cursor.getString(artistCol) ?: continue
                // Only consider tracks with multiple artists
                if (!artistField.contains(regex)) continue
                val names = artistField.split(regex).map { normalizeArtistName(it) }.filter { it.isNotEmpty() }
                if (names.contains(currentArtistName)) {
                    names.filter { it != currentArtistName }.forEach { collaboratorNames.add(it) }
                }
            }
        }

        val allArtists = fetchArtists()
        return allArtists.filter { artist ->
            val artistNameNorm = artist.name?.let { normalizeArtistName(it) }
            artistNameNorm != null && collaboratorNames.contains(artistNameNorm)
        }
    }
}