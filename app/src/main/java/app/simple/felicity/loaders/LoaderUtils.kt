package app.simple.felicity.loaders

import java.io.File

object LoaderUtils {
    private val supportedExtensions = arrayOf(
            "mp3", "mp4", "m4a", "aac", "ts", "flac", "mid", "xmf", "mxmf", "rtttl", "rtx", "ota", "imy", "ogg", "mkv", "wav"
    )

    fun File.isAudioFile(): Boolean {
        return this.extension.lowercase() in supportedExtensions
    }
}
