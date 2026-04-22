package app.simple.felicity.repository.metadata

import android.content.Context
import android.util.Log
import androidx.core.net.toUri

/**
 * A small helper that knows how to pull lyrics out of an audio file's tags.
 *
 * Instead of a heavy third-party library, this goes straight to our native
 * TagLib bridge — same one used for all other metadata — so we get support
 * for MP3 (USLT), M4A (©lyr), FLAC/OGG (LYRICS Vorbis comment), and more
 * with zero extra dependencies. Pretty neat, right?
 *
 * @author Hamza417
 */
object LyricsMetaHelper {

    private const val TAG = "LyricsMetaHelper"

    /**
     * Opens [audioUri] through the system's content resolver, hands the raw
     * file descriptor to TagLib's JNI bridge, and returns whatever lyrics are
     * baked into the file's tags — or null if there are none.
     *
     * This works with both regular file paths and SAF content:// URIs because
     * we never need the actual path: only the file descriptor matters.
     *
     * @param context  Any valid Android context — needed to reach the ContentResolver.
     * @param audioUri The URI (content:// or file://) of the audio file to inspect.
     * @return The embedded lyrics string if found, or null if the file has none.
     */
    fun extractEmbeddedLyrics(context: Context, audioUri: String): String? {
        return try {
            val uri = audioUri.toUri()
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val lyrics = TagLibBridge.nativeExtractLyricsFromFd(pfd.fd)
                if (!lyrics.isNullOrBlank()) {
                    Log.d(TAG, "Found embedded lyrics in ${audioUri.substringAfterLast('/')}")
                }
                lyrics?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            // If the file can't be opened (missing permissions, bad URI, etc.)
            // we just quietly return null — no crash, no drama.
            Log.w(TAG, "Could not open $audioUri to extract embedded lyrics: ${e.message}")
            null
        }
    }
}