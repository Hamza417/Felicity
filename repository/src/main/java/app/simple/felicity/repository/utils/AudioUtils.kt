package app.simple.felicity.repository.utils

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
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

    /**
     * Returns true when the app has a saved LRC sidecar file for this audio track.
     *
     * Lyrics are stored in app-private storage (not next to the audio file) using a
     * Base64-encoded version of the audio URI as the filename — so we can't derive the
     * path from the URI string directly like the old File-based approach tried to do.
     *
     * We replicate the same filename logic used by [LrcRepository] here so we don't
     * need to inject the whole repository just for a boolean check.
     *
     * @param context Any context — only [Context.filesDir] is needed.
     */
    fun Audio.hasLrc(context: Context): Boolean {
        val uri = uri.replaceAfterLast(".", "lrc").toUri()
        DocumentFile.fromSingleUri(context, uri)?.let { docFile ->
            if (docFile.exists()) return true
        }

        return false
    }
}