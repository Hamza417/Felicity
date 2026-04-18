package app.simple.felicity.core.utils

import java.io.File
import java.security.MessageDigest

object FileUtils {
    fun File.getMD5(): String {
        val digest = MessageDigest.getInstance("MD5")
        val inputStream = this.inputStream()
        val buffer = ByteArray(8192)
        var bytesRead: Int

        try {
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        } finally {
            inputStream.close()
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun String.toFile(): File {
        return File(this)
    }
}