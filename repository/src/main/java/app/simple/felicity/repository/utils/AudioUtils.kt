package app.simple.felicity.repository.utils

import app.simple.felicity.core.utils.FileUtils.toFile
import app.simple.felicity.core.utils.StringUtils.ifNullOrBlank
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.models.Audio

/**
 * Utility functions for audio-related operations.
 */
object AudioUtils {

    var albumArtistOverArtist: Boolean = LibraryPreferences.isAlbumArtistOverArtist()

    fun Audio.getArtists(): String {
        if (albumArtistOverArtist) {
            return albumArtist.ifNullOrBlank("Unknown")
        }

        return artist.ifNullOrBlank("Unknown")
    }

    fun Audio.hasLrc(): Boolean {
        val path = this.uri
        val lrcPath = path.substringBeforeLast('.').plus(".lrc")
        return lrcPath.toFile().exists()
    }
}