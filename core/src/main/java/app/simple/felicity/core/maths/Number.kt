package app.simple.felicity.core.maths

object Number {
    fun Int.absolute(): Int {
        return if (this < 0) -this else this
    }

    fun Int.half(): Int {
        return this / 2
    }

    fun Int.fourth(): Int {
        return this / 4
    }

    fun Int.twice(): Int {
        return this * 2
    }
}