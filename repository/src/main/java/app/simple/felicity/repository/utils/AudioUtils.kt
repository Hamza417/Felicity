package app.simple.felicity.repository.utils

import app.simple.felicity.core.utils.StringUtils.ifNullOrBlank
import app.simple.felicity.preferences.ListPreferences
import app.simple.felicity.repository.models.Audio
import net.jpountz.xxhash.XXHashFactory

/**
 * Utility functions for audio-related operations.
 */
object AudioUtils {

    var albumArtistOverArtist: Boolean = ListPreferences.isAlbumArtistOverArtist()

    /**
     * Generates a stable 64-bit identifier from a subset of [Audio] metadata fields.
     *
     * <p>The key is composed of title, artist, album, and duration so that files
     * which differ only in their on-disk location are still treated as the same song.
     * Uses XXHash64 with a fixed seed for speed and low collision probability.</p>
     *
     * @param song The [Audio] object whose metadata is used to generate the hash.
     * @return A 64-bit hash value that is stable across rescans for the same physical track.
     */
    fun generateStableId(song: Audio): Long {
        val key = "${song.title}_${song.artist}_${song.album}_${song.duration}"
        val factory = XXHashFactory.fastestInstance()
        val hasher = factory.hash64()
        val bytes = key.toByteArray(Charsets.UTF_8)
        return hasher.hash(bytes, 0, bytes.size, 0x9747b28c)
    }

    fun Audio.getArtists(): String {
        if (albumArtistOverArtist) {
            return albumArtist.ifNullOrBlank("Unknown")
        }

        return artist.ifNullOrBlank("Unknown")
    }
}