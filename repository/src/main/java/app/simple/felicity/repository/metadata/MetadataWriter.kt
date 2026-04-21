package app.simple.felicity.repository.metadata

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import app.simple.felicity.repository.metadata.MetadataWriter.write
import java.io.File

/**
 * Writes audio tag metadata to a file using TagLib through JNI.
 *
 * This replaces the old JAudioTagger implementation. TagLib works
 * directly with raw file descriptors, so it can write through both
 * regular files on disk and SAF content:// URIs without needing to
 * know anything about the actual path.
 *
 * Call [write] with a [Uri] (for SAF) or a [File] (for internal storage)
 * along with a [Fields] object describing what to change.
 *
 * @author Hamza417
 */
object MetadataWriter {

    private const val TAG = "MetadataWriter"

    /**
     * Holds all editable metadata fields for a single audio track.
     *
     * Any field set to null will be left exactly as it is in the file.
     * Setting a field to an empty string ("") will erase it from the tag.
     *
     * @param title       Song title.
     * @param artist      Primary artist name.
     * @param album       Album name.
     * @param albumArtist Album-level artist (e.g. "Various Artists").
     * @param year        Release year string (e.g. "2023").
     * @param trackNumber Track number within the album (e.g. "1" or "1/12").
     * @param numTracks   Total track count in the album.
     * @param discNumber  Disc number (e.g. "1" or "1/2").
     * @param genre       Genre string.
     * @param composer    Composer name.
     * @param writer      Lyricist / writer name.
     * @param compilation Compilation flag ("1" or "").
     * @param comment     Free-form comment embedded in the file.
     * @param lyrics      Unsynchronised lyrics embedded in the file.
     */
    data class Fields(
            val title: String?,
            val artist: String?,
            val album: String?,
            val albumArtist: String?,
            val year: String?,
            val trackNumber: String?,
            val numTracks: String?,
            val discNumber: String?,
            val genre: String?,
            val composer: String?,
            val writer: String?,
            val compilation: String?,
            val comment: String?,
            val lyrics: String?
    )

    /**
     * Writes [fields] into the audio file pointed to by a SAF [uri].
     *
     * This is the preferred path for files the user picked with the system
     * folder picker — Android gives us a content:// URI, and we open it
     * for read/write so TagLib can both read the existing tag structure and
     * then write the updated fields back in one go.
     *
     * @param context Used to access the ContentResolver.
     * @param uri     The content:// URI of the audio file to update.
     * @param fields  The metadata changes to apply.
     * @return `true` if the write succeeded, `false` otherwise.
     */
    fun write(context: Context, uri: Uri, fields: Fields): Boolean {
        return try {
            // Open "rw" so TagLib can both read the current tag structure
            // and write the updated fields back to the same file.
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                callNativeSave(pfd.fd, fields).also {
                    if (it) Log.d(TAG, "Tags saved via SAF: $uri")
                    else Log.w(TAG, "TagLib save returned false for: $uri")
                }
            } ?: run {
                Log.e(TAG, "ContentResolver returned null pfd for: $uri")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write tags to URI: $uri", e)
            false
        }
    }

    /**
     * Writes [fields] into a plain [File] on internal or external storage.
     *
     * Opens the file via [ParcelFileDescriptor] in read/write mode so the
     * same TagLib code path is used regardless of whether we have a File or
     * a URI — consistency is nice, even if the file is just sitting on disk.
     *
     * @param targetFile The audio file whose embedded tags will be updated.
     * @param fields     The metadata changes to apply.
     * @return `true` if the write succeeded, `false` otherwise.
     */
    fun write(targetFile: File, fields: Fields): Boolean {
        return try {
            ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_WRITE).use { pfd ->
                callNativeSave(pfd.fd, fields).also {
                    if (it) Log.d(TAG, "Tags saved to file: ${targetFile.absolutePath}")
                    else Log.w(TAG, "TagLib save returned false for: ${targetFile.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write tags to file: ${targetFile.absolutePath}", e)
            false
        }
    }

    /**
     * Hands the open file descriptor and all the individual field strings off
     * to TagLib via JNI. Extracted into its own little function to avoid
     * repeating the same long argument list in both [write] overloads above.
     */
    private fun callNativeSave(fd: Int, fields: Fields): Boolean {
        return TagLibBridge.nativeSaveToFd(
                fd = fd,
                title = fields.title,
                artist = fields.artist,
                album = fields.album,
                albumArtist = fields.albumArtist,
                year = fields.year,
                trackNumber = fields.trackNumber,
                numTracks = fields.numTracks,
                discNumber = fields.discNumber,
                genre = fields.genre,
                composer = fields.composer,
                lyricist = fields.writer,
                compilation = fields.compilation,
                comment = fields.comment,
                lyrics = fields.lyrics
        )
    }
}
