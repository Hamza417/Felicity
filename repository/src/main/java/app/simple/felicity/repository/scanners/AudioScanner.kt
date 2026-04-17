package app.simple.felicity.repository.scanners

import android.util.Log
import app.simple.felicity.preferences.LibraryPreferences
import java.io.File

class AudioScanner() {

    companion object {
        private const val TAG = "AudioScanner"

        private val AUDIO_EXTENSIONS = hashSetOf(
                "mp3", // MPEG Layer III Audio
                "m4a", // MPEG-4 Audio
                "aac", // Advanced Audio Coding
                "ts", // MPEG Transport Stream
                "flac", // Free Lossless Audio Codec
                "mid", // MIDI Audio
                "xmf", // eXtensible Music Format
                "mxmf", // Mobile eXtensible Music Format
                "rtttl", // Ring Tone Text Transfer Language
                "rtx", // Ring Tone XML
                "ota", // Over The Air
                "imy", // iMelody
                "ogg", // Ogg Vorbis Audio
                "opus", // Opus Audio Codec
                "wav", // Waveform Audio File Format
                "alac", // Apple Lossless Audio Codec
                "aiff", // Audio Interchange File Format
                "wma", // Windows Media Audio
                "ape", // Monkey's Audio
                "dsd", // Direct Stream Digital
                "pcm", // Pulse-Code Modulation
                "dsf" // DSD Stream File
        )

        /** Extensions we recognize as M3U playlist files. */
        private val M3U_EXTENSIONS = hashSetOf("m3u", "m3u8")
    }

    fun getAudioFiles(root: File): List<File> {
        if (!root.exists() || !root.isDirectory) {
            Log.e(TAG, "Invalid root directory: ${root.absolutePath}")
            return emptyList()
        } else {
            Log.d(TAG, "Scanning for audio files in: ${root.absolutePath}")
        }

        // Read user preferences fresh on every scan.
        val skipNomedia = LibraryPreferences.isSkipNomedia()
        val skipHiddenFiles = LibraryPreferences.isSkipHiddenFiles()
        val skipHiddenFolders = LibraryPreferences.isSkipHiddenFolders()
        val excludedFolders = LibraryPreferences.getExcludedFolders()

        return collectAudio(root, skipNomedia, skipHiddenFiles, skipHiddenFolders, excludedFolders)
    }

    /**
     * Walks [root] and returns every M3U or M3U8 playlist file found inside.
     *
     * <p>The same visibility filters that guard audio scanning (hidden files,
     * hidden folders, .nomedia) are applied here too, so a folder that is off-limits
     * for audio files will also be skipped when searching for playlists.</p>
     *
     * @param root The directory to start scanning from.
     * @return A flat list of all M3U files discovered under [root].
     */
    fun getM3uFiles(root: File): List<File> {
        if (!root.exists() || !root.isDirectory) {
            Log.e(TAG, "Invalid root directory for M3U scan: ${root.absolutePath}")
            return emptyList()
        } else {
            Log.d(TAG, "Scanning for M3U files in: ${root.absolutePath}")
        }

        val skipNomedia = LibraryPreferences.isSkipNomedia()
        val skipHiddenFiles = LibraryPreferences.isSkipHiddenFiles()
        val skipHiddenFolders = LibraryPreferences.isSkipHiddenFolders()
        val excludedFolders = LibraryPreferences.getExcludedFolders()

        return collectM3u(root, skipNomedia, skipHiddenFiles, skipHiddenFolders, excludedFolders)
    }

    private fun File.isAudioFile(): Boolean {
        val ext = extension
        if (ext.isEmpty()) return false
        return AUDIO_EXTENSIONS.contains(ext.lowercase())
    }

    /**
     * Returns true when this file has an extension that we recognize as an M3U playlist.
     * Simple, but honestly that's all we need here.
     */
    private fun File.isM3uFile(): Boolean {
        val ext = extension
        if (ext.isEmpty()) return false
        return M3U_EXTENSIONS.contains(ext.lowercase())
    }

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
                // Never descend into a folder the user has explicitly blacklisted.
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

    /**
     * Walks [root] recursively and collects every non-empty M3U/M3U8 file,
     * applying the same folder and file visibility rules as audio scanning.
     */
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
                if (excludedFolders.any { dir.absolutePath.startsWith(it) }) {
                    Log.d(TAG, "Skipping excluded folder for M3U scan: ${dir.absolutePath}")
                    return@onEnter false
                }
                if (skipNomedia && File(dir, ".nomedia").exists()) {
                    Log.d(TAG, "Skipping .nomedia folder for M3U scan: ${dir.absolutePath}")
                    return@onEnter false
                }
                if (skipHiddenFolders && dir.name.startsWith(".")) {
                    Log.d(TAG, "Skipping hidden folder for M3U scan: ${dir.absolutePath}")
                    return@onEnter false
                }
                return@onEnter true
            }
            .forEach { file ->
                if (file.isFile && file.length() > 0 && file.isM3uFile()) {
                    if (skipHiddenFiles && file.name.startsWith(".")) {
                        Log.d(TAG, "Skipping hidden M3U file: ${file.absolutePath}")
                        return@forEach
                    }
                    result.add(file)
                }
            }

        return result
    }
}

