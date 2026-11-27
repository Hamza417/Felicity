package app.simple.felicity.repository.utils

import android.content.Context
import android.provider.DocumentsContract
import androidx.core.net.toUri
import app.simple.felicity.core.utils.StringUtils.ifNullOrBlank
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.models.Audio
import java.util.Locale

/**
 * Utility functions for audio-related operations.
 *
 * @author Hamza417
 */
object AudioUtils {

    var albumArtistOverArtist: Boolean = LibraryPreferences.isAlbumArtistOverArtist()

    fun Audio.getProperArtists(): String {
        if (albumArtistOverArtist) {
            return albumArtist
                .ifNullOrBlank("Unknown")
        }

        return artist.ifNullOrBlank("Unknown")
    }

    fun Audio.getProperAlbum(): String {
        return album.ifNullOrBlank("Unknown")
    }

    fun Audio.getProperTitle(): String {
        return title.ifNullOrBlank(
                path?.substringAfterLast('/')
                    .ifNullOrBlank(name.toString().substringAfterLast('/'))
                    .ifNullOrBlank("Unknown"))
    }

    /**
     * Returns true when a lyrics or subtitle sidecar exists right next to this audio
     * track in the same SAF directory.
     *
     * We derive the expected sidecar document ID by replacing the audio file's
     * extension in the SAF document ID, build the URI directly, and then ask the
     * document provider for just one column on that URI. The provider returns an
     * empty cursor when the file is absent — no directory listing needed, no
     * traversal, just a single targeted query per candidate.
     *
     * `.lrc` and `.srt` are both timed and drive the lyrics view; `.txt` is the
     * untimed fallback. Any of them means the user has lyrics for this track.
     *
     * @param context Any Android context — only [Context.contentResolver] is needed.
     */
    fun Audio.hasLrc(context: Context): Boolean {
        return hasSidecar(context, "lrc", "srt", "txt")
    }

    /**
     * Returns true when any of the given [extensions] (without the dot) exists as a
     * sibling of this audio file in its SAF tree.
     */
    private fun Audio.hasSidecar(context: Context, vararg extensions: String): Boolean {
        return try {
            val audioUri = uri ?: return false
            val parsed = audioUri.toUri()
            if (!DocumentsContract.isDocumentUri(context, parsed)) return false

            val treeDocId = DocumentsContract.getTreeDocumentId(parsed) ?: return false
            val docId = DocumentsContract.getDocumentId(parsed)
            val baseDocId = docId.substringBeforeLast('.')

            val treeUri = DocumentsContract.buildTreeDocumentUri(parsed.authority!!, treeDocId)

            extensions.any { extension ->
                try {
                    val sidecarUri = DocumentsContract.buildDocumentUriUsingTree(
                            treeUri, "$baseDocId.$extension"
                    )
                    context.contentResolver.query(
                            sidecarUri,
                            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                            null, null, null
                    )?.use { cursor -> cursor.count > 0 } ?: false
                } catch (_: Exception) {
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun Audio.getProperBitrate(): String {
        return when {
            bitrate >= 1000.0 -> {
                // For high bitrates like CD quality (1411 kbps) or Hi-Res
                String.format(Locale.US, "%.1f Mbps", bitrate / 1000.0)
            }
            bitrate > 0 -> {
                // Standard formatting for most audio (e.g., 320 kbps, 900 kbps)
                "$bitrate kbps"
            }
            else -> {
                // Fallback for edge cases where bitrate is missing or 0
                "Unknown"
            }
        }
    }
}