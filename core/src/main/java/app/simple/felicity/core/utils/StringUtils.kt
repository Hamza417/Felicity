package app.simple.felicity.core.utils

object StringUtils {
    fun StringBuilder.appendAdditional(string: String?) {
        if (isNullOrEmpty()) {
            append(string)
        } else {
            append(", $string")
        }
    }
}