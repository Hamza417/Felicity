package app.simple.felicity.repository.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.simple.felicity.core.utils.StringUtils.ifNullOrBlank
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.models.Audio

/**
 * Utility functions for audio-related operations.
 *
 * @author Hamza417
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
     * Returns true when a `.lrc` sidecar file exists right next to this audio track
     * in the same SAF directory.
     *
     * We derive the expected sidecar document ID by replacing the audio file's
     * extension in the SAF document ID with ".lrc", build the URI directly, and
     * then ask the document provider for just one column on that URI. The provider
     * returns an empty cursor when the file is absent — no directory listing needed,
     * no traversal, just a single targeted query.
     *
     * @param context Any Android context — only [Context.contentResolver] is needed.
     */
    fun Audio.hasLrc(context: Context): Boolean {
        return try {
            val audioUri = uri ?: return false
            val parsed = Uri.parse(audioUri)
            if (!DocumentsContract.isDocumentUri(context, parsed)) return false

            val treeDocId = DocumentsContract.getTreeDocumentId(parsed) ?: return false
            val docId = DocumentsContract.getDocumentId(parsed)
            val lrcDocId = docId.substringBeforeLast('.') + ".lrc"

            val treeUri = DocumentsContract.buildTreeDocumentUri(parsed.authority!!, treeDocId)
            val lrcUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, lrcDocId)

            context.contentResolver.query(
                    lrcUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                    null, null, null
            )?.use { cursor -> cursor.count > 0 } ?: false
        } catch (_: Exception) {
            false
        }
    }
}