package app.simple.felicity.core.maths

object Lerp {
    fun lerp(start: Float, end: Float, startValue: Float, endValue: Float, value: Float): Float {
        if (value <= startValue) return start
        if (value >= endValue) return end
        val fraction = (value - startValue) / (endValue - startValue)
        return start + fraction * (end - start)
    }

    fun lerp(start: Float, stop: Float, fraction: Float): Float {
        return (start * (1 - fraction)) + (stop * fraction)
    }
}