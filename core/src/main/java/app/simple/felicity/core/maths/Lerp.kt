package app.simple.felicity.core.maths

import kotlin.math.round

object Lerp {
    fun lerp(start: Float, end: Float, startValue: Float, endValue: Float, value: Float): Float {
        if (value <= startValue) return start
        if (value >= endValue) return end
        val fraction = (value - startValue) / (endValue - startValue)
        return start + fraction * (end - start)
    }

    internal fun Float.roundToInt(): Int = round(this).toInt()
    internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}