package app.simple.felicity.repository.scanners

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import app.simple.felicity.preferences.LibraryPreferences
import java.io.File

/**
 * Finds audio and playlist files on the device. Supports both the traditional
 * file-system approach and the Storage Access Framework (SAF) approach, where
 * the user has granted access to specific folders via the system folder picker.
 *
 * @author Hamza417
 */
class AudioScanner {

    companion object {
        private const val TAG = "AudioScanner"

        private val AUDIO_EXTENSIONS = hashSetOf(
                "mp3", "m4a", "aac", "ts", "flac", "mid", "xmf", "mxmf",
                "rtttl", "rtx", "ota", "imy", "ogg", "opus", "wav", "alac",
                "aiff", "wma", "ape", "dsd", "pcm", "dsf"
        )

        /** Extensions we recognize as M3U playlist files. */
        private val M3U_EXTENSIONS = hashSetOf("m3u", "m3u8")
    }

    // File-based scanning (legacy / internal storage)

    fun getAudioFiles(root: File): List<File> {
        if (!root.exists() || !root.isDirectory) {
            Log.e(TAG, "Invalid root directory: ${root.absolutePath}")
            return emptyList()
        }
        Log.d(TAG, "Scanning for audio files in: ${root.absolutePath}")

        val skipNomedia = LibraryPreferences.isSkipNomedia()
        val skipHiddenFiles = LibraryPreferences.isSkipHiddenFiles()
        val skipHiddenFolders = LibraryPreferences.isSkipHiddenFolders()
        val excludedFolders = LibraryPreferences.getExcludedFolders()

        return collectAudio(root, skipNomedia, skipHiddenFiles, skipHiddenFolders, excludedFolders)
    }

    fun getM3uFiles(root: File): List<File> {
        if (!root.exists() || !root.isDirectory) {
            Log.e(TAG, "Invalid root directory for M3U scan: ${root.absolutePath}")
            return emptyList()
        }
        Log.d(TAG, "Scanning for M3U files in: ${root.absolutePath}")

        val skipNomedia = LibraryPreferences.isSkipNomedia()
        val skipHiddenFiles = LibraryPreferences.isSkipHiddenFiles()
        val skipHiddenFolders = LibraryPreferences.isSkipHiddenFolders()
        val excludedFolders = LibraryPreferences.getExcludedFolders()

        return collectM3u(root, skipNomedia, skipHiddenFiles, skipHiddenFolders, excludedFolders)
    }

    // SAF-based scanning — uses DocumentFile to walk the folder tree the
    // user granted us via the system folder picker.

    /**
     * Walks a SAF tree URI and collects every non-empty audio file it contains.
     * Applies the same filter rules (hidden files, .nomedia, excluded folders)
     * as the regular file scanner so behavior is consistent across both paths.
     *
     * @param context Needed to talk to the ContentResolver.
     * @param treeUri The tree URI returned by the SAF folder picker.
     * @return A flat list of [DocumentFile] entries for every matching audio file.
     */
    fun getAudioFiles(context: Context, treeUri: Uri): List<DocumentFile> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: run {
            Log.e(TAG, "Could not open tree URI: $treeUri")
            return emptyList()
        }
        Log.d(TAG, "SAF scan for audio in: ${root.uri}")

        val skipNomedia = LibraryPreferences.isSkipNomedia()
        val skipHiddenFiles = LibraryPreferences.isSkipHiddenFiles()
        val skipHiddenFolders = LibraryPreferences.isSkipHiddenFolders()
        val excludedFolders = LibraryPreferences.getExcludedFolders()

        return collectAudioSAF(context, root, skipNomedia, skipHiddenFiles, skipHiddenFolders, excludedFolders)
    }

    /**
     * Walks a SAF tree URI and collects every M3U/M3U8 file it contains,
     * applying the same visibility filters as the audio scanner.
     *
     * @param context Needed to talk to the ContentResolver.
     * @param treeUri The tree URI returned by the SAF folder picker.
     * @return A flat list of [DocumentFile] entries for every matching playlist file.
     */
    fun getM3uFiles(context: Context, treeUri: Uri): List<DocumentFile> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: run {
            Log.e(TAG, "Could not open tree URI for M3U scan: $treeUri")
            return emptyList()
        }
        Log.d(TAG, "SAF scan for M3U in: ${root.uri}")

        val skipNomedia = LibraryPreferences.isSkipNomedia()
        val skipHiddenFiles = LibraryPreferences.isSkipHiddenFiles()
        val skipHiddenFolders = LibraryPreferences.isSkipHiddenFolders()
        val excludedFolders = LibraryPreferences.getExcludedFolders()

        return collectM3uSAF(context, root, skipNomedia, skipHiddenFiles, skipHiddenFolders, excludedFolders)
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

    private fun DocumentFile.isAudioFile(): Boolean {
        val ext = name?.substringAfterLast('.', "") ?: return false
        if (ext.isEmpty()) return false
        return AUDIO_EXTENSIONS.contains(ext.lowercase())
    }

    private fun DocumentFile.isM3uFile(): Boolean {
        val ext = name?.substringAfterLast('.', "") ?: return false
        if (ext.isEmpty()) return false
        return M3U_EXTENSIONS.contains(ext.lowercase())
    }

    // File-based recursive walk (unchanged logic from before).

    private fun collectAudio(
            root: File,
            skipNomedia: Boolean,
            skipHiddenFiles: Boolean,
            skipHiddenFolders: Boolean,
            excludedFolders: Set<String>
    ): List<File> {
        val result = mutableListOf<File>()

        root.walkTopDown()
            .onEnter { dir ->
                if (excludedFolders.any { dir.absolutePath.startsWith(it) }) {
                    Log.d(TAG, "Skipping excluded folder: ${dir.absolutePath}")
                    return@onEnter false
                }
                if (skipNomedia && File(dir, ".nomedia").exists()) {
                    Log.d(TAG, "Skipping .nomedia folder: ${dir.absolutePath}")
                    return@onEnter false
                }
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
            skipNomedia: Boolean,
            skipHiddenFiles: Boolean,
            skipHiddenFolders: Boolean,
            excludedFolders: Set<String>
    ): List<File> {
        val result = mutableListOf<File>()

        root.walkTopDown()
            .onEnter { dir ->
                if (excludedFolders.any { dir.absolutePath.startsWith(it) }) return@onEnter false
                if (skipNomedia && File(dir, ".nomedia").exists()) return@onEnter false
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

    // SAF-based recursive walk — uses DocumentFile.listFiles() since we cannot
    // use java.io.File on URIs that came from the folder picker.

    private fun collectAudioSAF(
            context: Context,
            dir: DocumentFile,
            skipNomedia: Boolean,
            skipHiddenFiles: Boolean,
            skipHiddenFolders: Boolean,
            excludedFolders: Set<String>
    ): List<DocumentFile> {
        val result = mutableListOf<DocumentFile>()
        val children = dir.listFiles()

        // Check for a .nomedia marker inside this directory before descending.
        if (skipNomedia && children.any { it.isFile && it.name == ".nomedia" }) {
            Log.d(TAG, "SAF: Skipping .nomedia directory: ${dir.uri}")
            return result
        }

        for (child in children) {
            val name = child.name ?: continue

            if (child.isDirectory) {
                // Skip hidden folders (names starting with a dot) if the user wants that.
                if (skipHiddenFolders && name.startsWith(".")) {
                    Log.d(TAG, "SAF: Skipping hidden folder: $name")
                    continue
                }
                // Skip folders the user has explicitly blacklisted.
                if (excludedFolders.any { child.uri.toString().contains(it) }) {
                    Log.d(TAG, "SAF: Skipping excluded folder: ${child.uri}")
                    continue
                }
                result.addAll(collectAudioSAF(context, child, skipNomedia, skipHiddenFiles, skipHiddenFolders, excludedFolders))
            } else if (child.isFile && child.length() > 0 && child.isAudioFile()) {
                if (skipHiddenFiles && name.startsWith(".")) {
                    Log.d(TAG, "SAF: Skipping hidden audio file: $name")
                    continue
                }
                result.add(child)
            }
        }

        return result
    }

    private fun collectM3uSAF(
            context: Context,
            dir: DocumentFile,
            skipNomedia: Boolean,
            skipHiddenFiles: Boolean,
            skipHiddenFolders: Boolean,
            excludedFolders: Set<String>
    ): List<DocumentFile> {
        val result = mutableListOf<DocumentFile>()
        val children = dir.listFiles()

        if (skipNomedia && children.any { it.isFile && it.name == ".nomedia" }) {
            return result
        }

        for (child in children) {
            val name = child.name ?: continue

            if (child.isDirectory) {
                if (skipHiddenFolders && name.startsWith(".")) continue
                if (excludedFolders.any { child.uri.toString().contains(it) }) continue
                result.addAll(collectM3uSAF(context, child, skipNomedia, skipHiddenFiles, skipHiddenFolders, excludedFolders))
            } else if (child.isFile && child.length() > 0 && child.isM3uFile()) {
                if (skipHiddenFiles && name.startsWith(".")) continue
                result.add(child)
            }
        }

        return result
    }
}
