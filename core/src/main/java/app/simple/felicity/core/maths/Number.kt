package app.simple.felicity.core.maths

import kotlin.math.abs

object Number {
    fun Int.absolute(): Int {
        return if (this < 0) -this else this
    }

    fun Int.half(): Int {
        return this / 2
    }

    fun Long.half(): Long {
        return this / 2
    }

    fun Int.fourth(): Int {
        return this / 4
    }

    fun Long.fourth(): Long {
        return this / 4
    }

    fun Int.twice(): Int {
        return this * 2
    }

    fun Int.wrapToRange(min: Int, max: Int): Int {
        val range = max - min + 1
        var wrapped = (this - min) % range
        if (wrapped < 0) wrapped += range
        return wrapped + min
    }

    fun Int.toNegative(): Int = -abs(this)
    fun Long.toNegative(): Long = -abs(this)
    fun Float.toNegative(): Float = -abs(this)
    fun Double.toNegative(): Double = -abs(this)
}