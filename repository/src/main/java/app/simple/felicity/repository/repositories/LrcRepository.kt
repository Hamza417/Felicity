package app.simple.felicity.repository.repositories

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.simple.felicity.repository.models.LrcFile
import java.util.ArrayDeque
import javax.inject.Inject

class LrcRepository @Inject constructor(private val context: Context) {

    /**
     * Scans all persisted SAF permissions for .lrc files.
     * @return A list of LrcFile objects found across all granted trees.
     */
    fun scanAllPersistedTrees(): List<LrcFile> {
        val allFiles = mutableListOf<LrcFile>()
        val permissions = context.contentResolver.persistedUriPermissions

        for (permission in permissions) {
            if (permission.isReadPermission) {
                allFiles.addAll(scanTree(permission.uri))
            }
        }
        return allFiles
    }

    private fun scanTree(treeUri: Uri): List<LrcFile> {
        val foundFiles = mutableListOf<LrcFile>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)

        // Stack for the directory IDs to visit (Iterative DFS)
        val dirStack = ArrayDeque<String>()
        dirStack.push(rootId)

        val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        while (dirStack.isNotEmpty()) {
            val currentDirId = dirStack.pop()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDirId)

            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx)
                    val name = cursor.getString(nameIdx) ?: "Unknown"
                    val mime = cursor.getString(mimeIdx)

                    if (DocumentsContract.Document.MIME_TYPE_DIR == mime) {
                        // Push subfolder ID onto stack to scan in next iterations
                        dirStack.push(docId)
                    } else if (name.lowercase().endsWith(".lrc")) {
                        // Found a lyric file!
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        foundFiles.add(LrcFile(name, fileUri, currentDirId))
                    }
                }
            }
        }
        return foundFiles
    }
}