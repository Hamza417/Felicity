package app.simple.felicity.core.utils

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import java.util.concurrent.TimeUnit

object TimeUtils {

    fun Long.toDynamicTimeString(): String {
        var millis = this
        val days = TimeUnit.MILLISECONDS.toDays(millis)
        millis -= TimeUnit.DAYS.toMillis(days)
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        millis -= TimeUnit.HOURS.toMillis(hours)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        millis -= TimeUnit.MINUTES.toMillis(minutes)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis)

        val parts = mutableListOf<String>()
        if (days > 0) parts.add(days.toString())
        if (hours > 0 || parts.isNotEmpty()) parts.add(hours.toString().padStart(2, '0'))
        if (minutes > 0 || parts.isNotEmpty()) parts.add(minutes.toString().padStart(2, '0'))
        parts.add(seconds.toString().padStart(2, '0'))

        return parts.joinToString(":")
    }

    fun Long.toHighlightedTimeString(color: Int): SpannableString {
        var millis = this
        val days = TimeUnit.MILLISECONDS.toDays(millis)
        millis -= TimeUnit.DAYS.toMillis(days)
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        millis -= TimeUnit.HOURS.toMillis(hours)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        millis -= TimeUnit.MINUTES.toMillis(minutes)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis)

        val timeString = "%02d:%02d:%02d:%02d".format(days, hours, minutes, seconds)
        val spannable = SpannableString(timeString)

        var start = 0
        val units = listOf(days, hours, minutes, seconds)
        for ((i, value) in units.withIndex()) {
            val end = start + 2
            if (value > 0) {
                spannable.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            start = end + 1 // Skip colon
        }
        return spannable
    }
}