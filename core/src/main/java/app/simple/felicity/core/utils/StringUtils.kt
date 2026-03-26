package app.simple.felicity.core.utils

object StringUtils {
    fun String?.ifNullOrBlank(default: String): String {
        return if (this.isNullOrBlank()) default else this
    }
}