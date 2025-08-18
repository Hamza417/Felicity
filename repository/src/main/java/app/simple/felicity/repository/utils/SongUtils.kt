package app.simple.felicity.repository.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import app.simple.felicity.repository.models.Song
import app.simple.felicity.repository.models.SongStat
import net.jpountz.xxhash.XXHashFactory

object SongUtils {
    fun getArtworkUri(context: Context, albumId: Long, songId: Long): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
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
            artPath?.toUri()
        }
    }

    fun generateStableId(song: Song): Long {
        // Concatenate fields that best identify the song
        val key = "${song.title}_${song.artist}_${song.album}_${song.duration}"

        // Get an xxHash64 instance (fast + low collision)
        val factory = XXHashFactory.fastestInstance()
        val hasher = factory.hash64()

        // Convert string to bytes
        val bytes = key.toByteArray(Charsets.UTF_8)

        // Compute 64-bit hash with a fixed seed
        val hash: Long = hasher.hash(bytes, 0, bytes.size, 0x9747b28c)

        return hash
    }

    fun Song.createSongStat(songStat: SongStat?): SongStat {
        return songStat?.copy(
                lastPlayed = System.currentTimeMillis(),
                playCount = songStat.playCount + 1
        ) ?: SongStat(
                songId = this.id,
                stableId = generateStableId(this).toString(),
                lastPlayed = System.currentTimeMillis(),
                playCount = 1,
                skipCount = 0,
                isFavorite = false
        )
    }
}