package app.simple.felicity.core.utils

import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.math.roundToInt

object NumberUtils {
    fun getFormattedTime(timeValue: Long): String {
        return if (timeValue < 3600000) {
            String.format(
                    Locale.getDefault(),
                    "%02d:%02d",
                    TimeUnit.MILLISECONDS.toMinutes(timeValue) % TimeUnit.HOURS.toMinutes(1),
                    TimeUnit.MILLISECONDS.toSeconds(timeValue) % TimeUnit.MINUTES.toSeconds(1)
            )
        } else {
            String.format(
                    Locale.getDefault(),
                    "%02d:%02d:%02d",
                    TimeUnit.MILLISECONDS.toHours(timeValue),
                    TimeUnit.MILLISECONDS.toMinutes(timeValue) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(timeValue)),
                    TimeUnit.MILLISECONDS.toSeconds(timeValue) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(timeValue))
            )
        }
    }

    /**
     * Rounds the decimal places to the specified places
     * @param places is the number of significant digits required
     * @param number is the main value, must be a double or atleast contains some fractional values
     */
    fun round(number: Double, places: Int): Double {
        return try {
            var value = number
            require(places >= 0)
            val factor = 10.0.pow(places.toDouble()).toLong()
            value *= factor
            val tmp = value.roundToInt()
            tmp.toDouble() / factor
        } catch (e: IllegalArgumentException) {
            Double.NaN
        }
    }
}