package app.simple.felicity.repository.scanners

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import app.simple.felicity.preferences.LibraryPreferences
import java.io.File

/**
 * Finds audio and playlist files on the device. Supports both the traditional
 * file-system approach and the Storage Access Framework (SAF) approach, where
 * the user has granted access to specific folders via the system folder picker.
 *
 * The SAF path now uses [ContentResolver.query] with
 * [DocumentsContract.buildChildDocumentsUriUsingTree] instead of the old
 * [androidx.documentfile.provider.DocumentFile.listFiles] approach. The old way
 * fired a separate Binder IPC call for every single child in every directory —
 * that's why scanning 1333 files took 43 seconds. The new way fetches every
 * child of a directory in one round-trip, which is orders of magnitude faster.
 *
 * @author Hamza417
 */
class AudioScanner {

    companion object {
        private const val TAG = "AudioScanner"

        private val AUDIO_EXTENSIONS = hashSetOf(
                "mp3", "m4a", "aac", "ts", "flac", "mid", "xmf", "mxmf",
                "rtttl", "rtx", "ota", "imy", "ogg", "opus", "wav", "alac",
                "aiff", "wma", "ape", "pcm"
        )

        /** Extensions we recognize as M3U playlist files. */
        private val M3U_EXTENSIONS = hashSetOf("m3u", "m3u8")

        /**
         * The columns we ask for when listing SAF directory children.
         * Fetching exactly what we need keeps the query lean and fast.
         */
        private val SAF_PROJECTION = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
    }

    // File-based scanning (legacy / internal storage)

    fun getAudioFiles(root: File): List<File> {
        if (!root.exists() || !root.isDirectory) {
            Log.e(TAG, "Invalid root directory: ${root.absolutePath}")
            return emptyList()
        }
        Log.d(TAG, "Scanning for audio files in: ${root.absolutePath}")

        val skipHiddenFiles = LibraryPreferences.isSkipHiddenFiles()
        val skipHiddenFolders = LibraryPreferences.isSkipHiddenFolders()

        return collectAudio(root, skipHiddenFiles, skipHiddenFolders)
    }

    fun getM3uFiles(root: File): List<File> {
        if (!root.exists() || !root.isDirectory) {
            Log.e(TAG, "Invalid root directory for M3U scan: ${root.absolutePath}")
            return emptyList()
        }
        Log.d(TAG, "Scanning for M3U files in: ${root.absolutePath}")

        val skipHiddenFiles = LibraryPreferences.isSkipHiddenFiles()
        val skipHiddenFolders = LibraryPreferences.isSkipHiddenFolders()

        return collectM3u(root, skipHiddenFiles, skipHiddenFolders)
    }

    // SAF-based scanning — one ContentResolver.query() per directory instead of
    // one Binder IPC per child attribute. This is the fast path.

    /**
     * Walks a SAF tree URI and returns a list of every matching audio file it contains.
     * All file attributes (size, lastModified) are fetched in the same bulk query that
     * lists the directory, so there are zero extra Binder calls once we have the list.
     *
     * @param context Needed to talk to the ContentResolver.
     * @param treeUri The tree URI returned by the SAF folder picker.
     * @return A flat list of [SAFFile] entries for every matching audio file.
     */
    fun getAudioFiles(context: Context, treeUri: Uri): List<SAFFile> {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        Log.d(TAG, "SAF audio scan starting from tree: $treeUri (rootDocId=$rootDocId)")

        val skipHiddenFiles = LibraryPreferences.isSkipHiddenFiles()
        val skipHiddenFolders = LibraryPreferences.isSkipHiddenFolders()

        return collectAudioSAF(context, treeUri, rootDocId, skipHiddenFiles, skipHiddenFolders)
    }

    /**
     * Walks a SAF tree URI and returns every M3U/M3U8 file it contains.
     *
     * @param context Needed to talk to the ContentResolver.
     * @param treeUri The tree URI returned by the SAF folder picker.
     * @return A flat list of [SAFFile] entries for every matching playlist file.
     */
    fun getM3uFiles(context: Context, treeUri: Uri): List<SAFFile> {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        Log.d(TAG, "SAF M3U scan starting from tree: $treeUri (rootDocId=$rootDocId)")

        val skipHiddenFiles = LibraryPreferences.isSkipHiddenFiles()
        val skipHiddenFolders = LibraryPreferences.isSkipHiddenFolders()

        return collectM3uSAF(context, treeUri, rootDocId, skipHiddenFiles, skipHiddenFolders)
    }

    // Extension helpers for file-type detection.

    private fun File.isAudioFile(): Boolean {
        val ext = extension
        if (ext.isEmpty()) return false
        return AUDIO_EXTENSIONS.contains(ext.lowercase())
    }

    private fun File.isM3uFile(): Boolean {
        val ext = extension
        if (ext.isEmpty()) return false
        return M3U_EXTENSIONS.contains(ext.lowercase())
    }

    /** Returns true if this file name's extension belongs to the audio set. */
    private fun String.isAudioFile(): Boolean {
        val ext = substringAfterLast('.', "")
        if (ext.isEmpty()) return false
        return AUDIO_EXTENSIONS.contains(ext.lowercase())
    }

    /** Returns true if this file name's extension belongs to the M3U set. */
    private fun String.isM3uFile(): Boolean {
        val ext = substringAfterLast('.', "")
        if (ext.isEmpty()) return false
        return M3U_EXTENSIONS.contains(ext.lowercase())
    }

    // File-based recursive walk (unchanged logic from before).

    private fun collectAudio(
            root: File,
            skipHiddenFiles: Boolean,
            skipHiddenFolders: Boolean
    ): List<File> {
        val result = mutableListOf<File>()

        root.walkTopDown()
            .onEnter { dir ->
                if (skipHiddenFolders && dir.name.startsWith(".")) {
                    Log.d(TAG, "Skipping hidden folder: ${dir.absolutePath}")
                    return@onEnter false
                }
                return@onEnter true
            }
            .forEach { file ->
                if (file.isFile && file.length() > 0 && file.isAudioFile()) {
                    if (skipHiddenFiles && file.name.startsWith(".")) {
                        Log.d(TAG, "Skipping hidden file: ${file.absolutePath}")
                        return@forEach
                    }
                    result.add(file)
                }
            }

        return result
    }

    private fun collectM3u(
            root: File,
            skipHiddenFiles: Boolean,
            skipHiddenFolders: Boolean
    ): List<File> {
        val result = mutableListOf<File>()

        root.walkTopDown()
            .onEnter { dir ->
                if (skipHiddenFolders && dir.name.startsWith(".")) return@onEnter false
                return@onEnter true
            }
            .forEach { file ->
                if (file.isFile && file.length() > 0 && file.isM3uFile()) {
                    if (skipHiddenFiles && file.name.startsWith(".")) return@forEach
                    result.add(file)
                }
            }

        return result
    }

    // SAF recursive walk — one ContentResolver.query() per directory level.
    // Each query returns ALL children of that directory in a single IPC call,
    // including their document IDs, MIME types, sizes, and timestamps.

    /**
     * Recursively collects audio files from a SAF directory using fast bulk queries.
     *
     * The trick here is [DocumentsContract.buildChildDocumentsUriUsingTree] —
     * it gives us a URI we can pass to [ContentResolver.query] that returns every
     * child of [dirDocumentId] in one network/Binder round-trip. No looping,
     * no per-file IPC, just one query per directory level.
     *
     * Hidden file/folder filtering is still applied here since that is just a
     * name check — no File objects needed.
     */
    private fun collectAudioSAF(
            context: Context,
            treeUri: Uri,
            dirDocumentId: String,
            skipHiddenFiles: Boolean,
            skipHiddenFolders: Boolean
    ): List<SAFFile> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocumentId)

        data class Row(
                val docId: String,
                val name: String,
                val mimeType: String,
                val size: Long,
                val lastModified: Long
        )

        val rows = mutableListOf<Row>()

        context.contentResolver.query(childrenUri, SAF_PROJECTION, null, null, null)?.use { cursor ->
            val idxDocId = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val idxName = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val idxMime = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val idxSize = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val idxMod = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val name = cursor.getString(idxName) ?: continue
                rows.add(Row(
                        cursor.getString(idxDocId) ?: continue,
                        name,
                        cursor.getString(idxMime) ?: "",
                        cursor.getLong(idxSize),
                        cursor.getLong(idxMod)
                ))
            }
        }

        val result = mutableListOf<SAFFile>()

        for (row in rows) {
            if (row.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                if (skipHiddenFolders && row.name.startsWith(".")) {
                    Log.d(TAG, "SAF: Skipping hidden folder: ${row.name}")
                    continue
                }
                result.addAll(collectAudioSAF(context, treeUri, row.docId, skipHiddenFiles, skipHiddenFolders))
            } else {
                if (row.size <= 0) continue
                if (!row.name.isAudioFile()) continue
                if (skipHiddenFiles && row.name.startsWith(".")) {
                    Log.d(TAG, "SAF: Skipping hidden audio file: ${row.name}")
                    continue
                }
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, row.docId)
                result.add(SAFFile(docUri, row.name, row.size, row.lastModified))
            }
        }

        return result
    }

    private fun collectM3uSAF(
            context: Context,
            treeUri: Uri,
            dirDocumentId: String,
            skipHiddenFiles: Boolean,
            skipHiddenFolders: Boolean
    ): List<SAFFile> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocumentId)

        data class Row(
                val docId: String,
                val name: String,
                val mimeType: String,
                val size: Long,
                val lastModified: Long
        )

        val rows = mutableListOf<Row>()

        context.contentResolver.query(childrenUri, SAF_PROJECTION, null, null, null)?.use { cursor ->
            val idxDocId = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val idxName = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val idxMime = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val idxSize = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val idxMod = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val name = cursor.getString(idxName) ?: continue
                rows.add(Row(
                        cursor.getString(idxDocId) ?: continue,
                        name,
                        cursor.getString(idxMime) ?: "",
                        cursor.getLong(idxSize),
                        cursor.getLong(idxMod)
                ))
            }
        }

        val result = mutableListOf<SAFFile>()

        for (row in rows) {
            if (row.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                if (skipHiddenFolders && row.name.startsWith(".")) continue
                result.addAll(collectM3uSAF(context, treeUri, row.docId, skipHiddenFiles, skipHiddenFolders))
            } else {
                if (row.size <= 0) continue
                if (!row.name.isM3uFile()) continue
                if (skipHiddenFiles && row.name.startsWith(".")) continue
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, row.docId)
                result.add(SAFFile(docUri, row.name, row.size, row.lastModified))
            }
        }

        return result
    }
}
