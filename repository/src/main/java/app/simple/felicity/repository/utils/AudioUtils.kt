package app.simple.felicity.repository.utils

import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.SongStat
import net.jpountz.xxhash.XXHashFactory

object AudioUtils {
    fun generateStableId(song: Audio): Long {
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

    fun Audio.createSongStat(songStat: SongStat?): SongStat {
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