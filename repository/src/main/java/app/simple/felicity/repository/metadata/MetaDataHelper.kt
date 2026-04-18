package app.simple.felicity.repository.metadata

import android.util.Log
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

    fun Audio.generateStableHash(): Long {
        // Sanitize strings to prevent minor typo/case mismatches
        val safeTitle = title?.trim()?.lowercase() ?: "unknown_title"
        val safeArtist = artist?.trim()?.lowercase() ?: "unknown_artist"
        val safeAlbum = album?.trim()?.lowercase() ?: "unknown_album"

        // Payload
        val stableString = "$safeTitle|$safeArtist|$safeAlbum|$duration|$trackNumber|$year"

        // Generate the MD5 hash (16 bytes)
        val digest = MessageDigest.getInstance("MD5").digest(stableString.toByteArray())

        // Extract the first 8 bytes and cast directly to a 64-bit Long
        return ByteBuffer.wrap(digest).long
    }

    fun String.extractMetadata(): Audio? {
        return this.toFile().extractMetadata()
    }
}
