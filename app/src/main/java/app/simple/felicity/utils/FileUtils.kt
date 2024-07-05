package app.simple.felicity.utils

import java.io.File

object FileUtils {

    /**
     * Converts string to file
     * @return file
     */
    fun String.toFile(): File {
        return File(this)
    }
}
