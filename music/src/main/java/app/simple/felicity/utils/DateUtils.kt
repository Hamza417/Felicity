package app.simple.felicity.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {
    fun Long.toDate(): String {
        val sdf = SimpleDateFormat(app.simple.felicity.preferences.FormattingPreferences.getDateFormat(), Locale.getDefault())
        return sdf.format(Date(this))
    }

    fun formatDate(date: Long): String {
        val sdf = SimpleDateFormat("EEE, yyyy MMM dd, hh:mm a", Locale.getDefault())
        return sdf.format(Date(date))
    }

    fun formatDate(date: Long, pattern: String): String {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(Date(date))
    }

    fun getTodayDate(): String {
        val sdf = SimpleDateFormat("EEE, yyyy MMM dd, hh:mm a", Locale.getDefault())
        return sdf.format(Date())
    }
}
