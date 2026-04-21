package app.simple.felicity.repository.metadata

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import app.simple.felicity.repository.models.Audio
import java.io.FileInputStream

/**
 * The native bridge object that loads the TagLib JNI shared library and
 * exposes its single entry point to Kotlin.
 *
 * The actual heavy lifting (C++ TagLib calls, fd duplication, tag extraction)
 * all lives in taglib_jni.cpp. This Kotlin object is just the thin glue that
 * loads the .so and declares the external function.
 *
 * @author Hamza417
 */
object TagLibBridge {

    init {
        System.loadLibrary("taglib_jni")
    }

    /**
     * Asks TagLib to read the audio file pointed to by [fd] and return all
     * available metadata as a [TagLibMetadata] object.
     *
     * The fd is duplicated inside the native code (via `dup()`), so the caller
     * can close their file descriptor independently of what TagLib does with it.
     *
     * @param fd A readable POSIX file descriptor pointing at an audio file.
     * @return A populated [TagLibMetadata], or null if the format is unsupported
     *         or the file is unreadable.
     */
    external fun nativeLoadFromFd(fd: Int): TagLibMetadata?

    /**
     * Asks TagLib to write the provided tag fields into the audio file pointed
     * to by [fd]. The descriptor must be open for both reading and writing — use
     * `ContentResolver.openFileDescriptor(uri, "rw")` for SAF URIs or
     * `ParcelFileDescriptor.open(file, MODE_READ_WRITE)` for plain files.
     *
     * Fields that are passed as null are left untouched in the file.
     * Passing an empty string will erase that field from the tag entirely.
     *
     * @return `true` if the tags were saved successfully, `false` otherwise.
     */
    /**
     * Asks TagLib to pull the embedded lyrics text out of the audio file
     * pointed to by [fd]. This covers USLT frames (MP3), the ©lyr atom (M4A),
     * LYRICS Vorbis comments (FLAC/OGG), and any other format TagLib
     * recognizes — all in one call, no JAudioTagger required.
     *
     * @param fd A readable POSIX file descriptor pointing at an audio file.
     * @return The embedded lyrics string, or null if none were found.
     */
    external fun nativeExtractLyricsFromFd(fd: Int): String?

    external fun nativeSaveToFd(
            fd: Int,
            title: String?,
            artist: String?,
            album: String?,
            albumArtist: String?,
            year: String?,
            trackNumber: String?,
            numTracks: String?,
            discNumber: String?,
            genre: String?,
            composer: String?,
            lyricist: String?,
            compilation: String?,
            comment: String?,
            lyrics: String?
    ): Boolean
}

/**
 * The Kotlin-side metadata loader that replaces JAudioTagger.
 *
 * Supports both regular File paths (for internal storage) and SAF content
 * URIs (for folders the user picked via the system folder picker). Both paths
 * end up at the same place: an open file descriptor passed to TagLib via JNI.
 *
 * @author Hamza417
 */
object TagLibLoader {

    private const val TAG = "TagLibLoader"

    /**
     * Reads metadata from a local [File] on disk.
     *
     * Opens a [FileInputStream] to get a raw fd, passes it to TagLib via JNI,
     * and converts the result into a fully populated [Audio] model.
     *
     * @param file The audio file to read.
     * @return A populated [Audio], or null if TagLib couldn't read the file.
     */
    fun loadFromFile(file: java.io.File): Audio? {
        return try {
            // ParcelFileDescriptor.open() gives us a raw int fd directly,
            // whereas FileInputStream.fd is a FileDescriptor *object* — not an int.
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                TagLibBridge.nativeLoadFromFd(pfd.fd)
                    ?.toAudio(
                            name = file.name,
                            path = file.absolutePath,
                            size = file.length(),
                            dateModified = file.lastModified()
                    )
            }
        } catch (e: Exception) {
            Log.e(TAG, "TagLib failed for file: ${file.name}", e)
            null
        }
    }

    /**
     * Reads metadata from a SAF content URI.
     *
     * Opens the URI as a [android.os.ParcelFileDescriptor] through the
     * ContentResolver, grabs its raw int fd, and passes it to TagLib via JNI.
     * The size and last-modified values must come from the [DocumentFile] that
     * supplied the URI, since a bare Uri has no built-in way to report them.
     *
     * @param context      Android context for ContentResolver access.
     * @param uri          The content:// URI of the audio file.
     * @param fileSize     File size in bytes (from DocumentFile.length()).
     * @param lastModified Timestamp in ms (from DocumentFile.lastModified()).
     * @return A populated [Audio], or null if TagLib couldn't read the URI.
     */
    fun loadFromUri(context: Context, uri: Uri, fileSize: Long, lastModified: Long): Audio? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                TagLibBridge.nativeLoadFromFd(pfd.fd)
                    ?.toAudio(
                            name = uri.lastPathSegment ?: uri.toString(),
                            path = uri.toString(),
                            size = fileSize,
                            dateModified = lastModified
                    )
            }
        } catch (e: Exception) {
            Log.e(TAG, "TagLib failed for URI: $uri", e)
            null
        }
    }

    /**
     * Converts a raw [TagLibMetadata] bag-of-fields into the full [Audio]
     * database model, filling in the file-system fields (name, path, size,
     * date) that TagLib doesn't know about.
     */
    private fun TagLibMetadata.toAudio(
            name: String,
            path: String,
            size: Long,
            dateModified: Long
    ): Audio {
        val audio = Audio()
        audio.name = name
        audio.uri = path
        audio.setTitle(title)
        audio.artist = artist
        audio.album = album
        audio.genre = genre
        audio.year = year
        audio.composer = composer
        audio.albumArtist = albumArtist
        audio.writer = lyricist
        audio.compilation = compilation
        audio.date = year        // mirror year into the date field for display purposes
        audio.discNumber = discNumber
        audio.trackNumber = trackNumber
        audio.numTracks = numTracks
        audio.duration = duration
        audio.bitrate = bitrate
        audio.samplingRate = sampleRate
        audio.bitPerSample = bitsPerSample
        audio.size = size
        audio.dateModified = dateModified
        audio.dateAdded = System.currentTimeMillis()
        // Generate the stable content hash last, after all fields are set,
        // so the hash reflects the full metadata fingerprint.
        audio.hash = with(MetaDataHelper) { audio.generateStableHash() }
        return audio
    }
}

