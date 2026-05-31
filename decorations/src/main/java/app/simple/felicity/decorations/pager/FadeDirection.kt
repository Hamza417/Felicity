package app.simple.felicity.decorations.pager;

/**
 * The direction in which the [FelicityPager] edge-fade gradient travels.
 *
 * The "transparent" end is the edge described by the name. For example, [TOP_TO_BOTTOM]
 * leaves the top fully opaque and fades the bottom toward 0 % alpha.
 */
enum class FadeDirection {
    /** Fade from opaque at the top toward transparent at the bottom. */
    TOP_TO_BOTTOM,

    /** Fade from opaque at the bottom toward transparent at the top. */
    BOTTOM_TO_TOP,

    /** Fade from opaque on the left toward transparent on the right. */
    LEFT_TO_RIGHT,

    /** Fade from opaque on the right toward transparent on the left. */
    RIGHT_TO_LEFT;

    companion object {
        /**
         * Returns the [FadeDirection] whose ordinal matches [value],
         * or [TOP_TO_BOTTOM] when [value] is out of range.
         *
         * @param value Integer ordinal sourced from a typed-array attribute.
         */
        fun fromInt(value: Int): FadeDirection = entries.getOrElse(value) { TOP_TO_BOTTOM }
    }
}