package app.simple.felicity.shared.utils

import kotlin.math.pow
import kotlin.math.roundToInt

object NumberUtils {
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