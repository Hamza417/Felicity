package app.simple.felicity.repository.scanners

import android.net.Uri

/**
 * A featherweight snapshot of a file discovered during a SAF tree walk.
 *
 * The normal DocumentFile wrapper is convenient, but calling length() or
 * lastModified() on it fires a separate Binder IPC for each file — which is
 * why scanning 1333 songs took 43 seconds. This class caches those values at
 * scan time from the single bulk ContentResolver.query() call, so the rest of
 * the pipeline gets size and timestamp for free.
 *
 * @author Hamza417
 */
data class SAFFile(
        /** The content:// URI that uniquely identifies this document. */
        val uri: Uri,
        /** Display name of the file, e.g. "song.mp3". */
        val name: String,
        /** Size in bytes as reported by the document provider. */
        val size: Long,
        /** Last-modified timestamp in milliseconds since the Unix epoch. */
        val lastModified: Long
)

