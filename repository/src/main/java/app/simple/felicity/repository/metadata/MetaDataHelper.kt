package app.simple.felicity.repository.metadata

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import app.simple.felicity.core.utils.FileUtils.toFile
import app.simple.felicity.repository.models.Audio
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

object MetaDataHelper {

    private const val TAG = "MetaDataHelper"

    fun File.extractMetadata(): Audio? {
        return runCatching {
            JAudioMetadataLoader.loadFromFile(this)
        }.getOrElse { it ->
            Log.e(TAG, "Failed to load metadata using JAudioMetadataLoader for file: ${this.absolutePath}")
            it.printStackTrace()
            runCatching {
                Log.d(TAG, "Attempting to load metadata using MediaMetadataLoader for file: ${this.absolutePath}")
                MediaMetadataLoader.loadFromFile(this)
            }.getOrElse {
                Log.e(TAG, "Failed to load metadata using MediaMetadataLoader for file: ${this.absolutePath}")
                it.printStackTrace()
                null
            }
        }
    }

    /**
     * Extracts metadata from a SAF [DocumentFile] using [MediaMetadataRetriever]
     * via its content URI. Since we can't use JAudioTagger on a URI (it needs a real
     * File), we go straight to the MediaMetadata path here — totally fine for a SAF scan.
     *
     * @param context Android context needed to open the content URI stream.
     * @return A populated [Audio] object, or null if something went wrong.
     */
    fun DocumentFile.extractMetadata(context: Context): Audio? {
        return runCatching {
            MediaMetadataLoader.loadFromUri(context, uri, length(), lastModified())
        }.getOrElse {
            Log.e(TAG, "Failed to extract metadata from SAF URI: $uri", it)
            null
        }
    }

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
