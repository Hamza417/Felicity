package app.simple.felicity.utils

object StringUtils {
    fun sanitizeString(input: String): String {
        val utf8Bytes = input.toByteArray(Charsets.UTF_8)
        val sanitizedString = String(utf8Bytes, Charsets.UTF_8)
        return sanitizedString
    }
}
