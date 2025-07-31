package app.simple.felicity.core.utils

import android.util.Log
import net.jpountz.xxhash.XXHashFactory
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
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

    fun generateXXHash64(file: File, seed: Long = 0): Long {
        val factory = XXHashFactory.fastestInstance()
        val hasher = factory.newStreamingHash64(seed)

        FileInputStream(file).use { fis ->
            val channel = fis.channel

            while (true) {
                val bytes = ByteArray(8192)
                val bytesRead = channel.read(ByteBuffer.wrap(bytes))
                if (bytesRead == -1) break
                hasher.update(bytes, 0, bytesRead)
            }
        }

        Log.i("FileUtils", "XXHash64 for file ${file.name}: ${hasher.value}")

        return hasher.value
    }

    fun String.toFile(): File {
        return File(this)
    }
}