package app.simple.felicity.core.maths

object Number {
    fun Int.absolute(): Int {
        return if (this < 0) -this else this
    }

    fun Int.half(): Int {
        return this / 2
    }
}