package app.simple.felicity.core.utils

import java.util.Locale

object DateUtils {
    fun Long.getYear(): String {
        val year = String.format(Locale.getDefault(), "%tY", this)
        return year
    }
}