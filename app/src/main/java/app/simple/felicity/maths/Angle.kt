package app.simple.felicity.maths

object Angle {
    /**
     * Normalizes Euler angle within an acceptable range
     * such as an image rotation could exceed 360° in some cases where
     * few extra values are simplified together, in short this fun limits
     * the value range to be within 0° - 360° even if the actual values are
     * more or less
     *
     * @param [inverseResult] inverses the final value by 360° useful in cases
     * like compass rotation where degrees are inverted for fetching direction
     *
     * @return [Float]
     */
    fun Float.normalizeEulerAngle(inverseResult: Boolean): Float {
        var normalized = this % 360
        if (normalized < 0) normalized += 360
        return if (inverseResult) normalized - 360F else normalized
    }
}
