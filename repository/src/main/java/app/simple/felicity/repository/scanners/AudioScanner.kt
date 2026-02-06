package app.simple.felicity.repository.scanners

import android.util.Log
import java.io.File

private class AudioScanner() {

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
                "wav", // Waveform Audio File Format
                "alac", // Apple Lossless Audio Codec
                "aiff", // Audio Interchange File Format
                "wma", // Windows Media Audio
                "ape", // Monkey's Audio
                "dsd", // Direct Stream Digital
                "pcm", // Pulse-Code Modulation
                "dsf" // DSD Stream File
        )

        private var SKIP_NOMEDIA = true // for .nomedia folders
        private var SKIP_HIDDEN = true // for dot files and folders
    }

    fun getAudioFiles(root: File): List<File> {
        if (!root.exists() || !root.isDirectory) {
            Log.e(TAG, "Invalid root directory: ${root.absolutePath}")
            return emptyList()
        }

        return collectAudio(root)
    }

    private fun File.isAudioFile(): Boolean {
        val ext = extension
        if (ext.isEmpty()) return false
        return AUDIO_EXTENSIONS.contains(ext.lowercase())
    }

    private fun collectAudio(root: File): List<File> {
        val result = mutableListOf<File>()

        root.walkTopDown()
            .onEnter { dir ->
                if (SKIP_NOMEDIA && File(dir, ".nomedia").exists()) {
                    Log.d(TAG, "Skipping .nomedia folder: ${dir.absolutePath}")
                    return@onEnter false
                }
                if (SKIP_HIDDEN && dir.name.startsWith(".")) {
                    Log.d(TAG, "Skipping hidden folder: ${dir.absolutePath}")
                    return@onEnter false
                }
                return@onEnter true
            }   // skip hidden dirs
            .forEach { file ->
                if (file.isFile && file.length() > 0 && file.isAudioFile()) {
                    result.add(file)
                }
            }

        return result
    }
}