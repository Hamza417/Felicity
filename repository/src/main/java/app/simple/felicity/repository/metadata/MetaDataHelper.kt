package app.simple.felicity.repository.metadata

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import app.simple.felicity.core.utils.FileUtils.toFile
import app.simple.felicity.repository.models.Audio
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Central hub for all metadata extraction operations.
 *
 * TagLib (via JNI) is the primary reader — it gives us rich, accurate tags
 * from any format and works with raw file descriptors, which is exactly what
 * we need for SAF content URIs. [MediaMetadataLoader] is the safety net for
 * formats TagLib doesn't recognize; it uses Android's built-in
 * [android.media.MediaMetadataRetriever] so it handles basically everything
 * the OS itself can play.
 *
 * @author Hamza417
 */
object MetaDataHelper {

    private const val TAG = "MetaDataHelper"

    /**
     * Reads all metadata from a regular file on disk.
     *
     * TagLib is tried first. If it returns null (unsupported format, corrupt
     * header, etc.) we fall back to [MediaMetadataLoader] which uses Android's
     * own [android.media.MediaMetadataRetriever].
     */
    fun File.extractMetadata(): Audio? {
        return runCatching {
            TagLibLoader.loadFromFile(this)
        }.getOrElse {
            Log.e(TAG, "TagLib failed for file: ${this.name}", it)
            null
        } ?: runCatching {
            Log.d(TAG, "Falling back to MediaMetadataLoader for: ${this.name}")
            MediaMetadataLoader.loadFromFile(this)
        }.getOrElse {
            Log.e(TAG, "MediaMetadataLoader also failed for: ${this.name}", it)
            null
        }
    }

    /**
     * Reads metadata from a SAF [DocumentFile] via its content URI.
     *
     * TagLib handles this through a raw file descriptor (dup'd from the
     * [android.os.ParcelFileDescriptor] we get from [android.content.ContentResolver]).
     * If TagLib can't handle the format, [MediaMetadataLoader] takes over via
     * its own URI-based [android.media.MediaMetadataRetriever] path.
     *
     * @param context Android context needed to open the content URI stream.
     * @return A populated [Audio] object, or null if both loaders failed.
     */
    fun DocumentFile.extractMetadata(context: Context): Audio? {
        return runCatching {
            TagLibLoader.loadFromUri(context, uri, length(), lastModified())
        }.getOrElse {
            Log.e(TAG, "TagLib failed for SAF URI: $uri", it)
            null
        } ?: runCatching {
            Log.d(TAG, "Falling back to MediaMetadataLoader for SAF URI: $uri")
            MediaMetadataLoader.loadFromUri(context, uri, length(), lastModified())
        }.getOrElse {
            Log.e(TAG, "MediaMetadataLoader also failed for SAF URI: $uri", it)
            null
        }
    }

    /**
     * Generates a stable 64-bit content hash from a track's core metadata.
     *
     * The same song file at two different paths will produce the same hash —
     * that's intentional. It lets play-count statistics stay attached to a
     * track even when the file is moved or temporarily removed.
     */
    fun Audio.generateStableHash(): Long {
        val safeTitle = rawTitle?.trim()?.lowercase() ?: "unknown_title"
        val safeArtist = artist?.trim()?.lowercase() ?: "unknown_artist"
        val safeAlbum = album?.trim()?.lowercase() ?: "unknown_album"

        val stableString = "$safeTitle|$safeArtist|$safeAlbum|$duration|$trackNumber|$year|$name|$size"

        val digest = MessageDigest.getInstance("MD5").digest(stableString.toByteArray())
        return ByteBuffer.wrap(digest).long
    }

    fun String.extractMetadata(): Audio? {
        return this.toFile().extractMetadata()
    }
}
