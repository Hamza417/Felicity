package app.simple.felicity.core.utils

object StringUtils {
    fun String?.ifNullOrBlank(default: String): String {
        return if (this.isNullOrBlank()) default else this
    }

    fun StringBuilder.appendInfo(string: String?) {
        if (isNullOrEmpty()) {
            append(string)
        } else {
            append(" • $string")
        }
    }

    fun sanitizeString(input: String): String {
        val utf8Bytes = input.toByteArray(Charsets.UTF_8)
        val sanitizedString = String(utf8Bytes, Charsets.UTF_8)
        return sanitizedString
    }
}