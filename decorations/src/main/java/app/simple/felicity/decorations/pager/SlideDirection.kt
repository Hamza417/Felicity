package app.simple.felicity.decorations.pager

/**
 * Controls which axis [FelicityPager] scrolls along. Horizontal is the classic left-right
 * swipe that most people are used to. Vertical flips that 90 degrees so pages slide up and
 * down instead — useful for feed-style layouts or portrait cover-art stacks.
 *
 * @author Hamza417
 */
enum class SlideDirection {
    /** Pages slide left and right — the default behavior. */
    HORIZONTAL,

    /** Pages slide up and down, like a vertical feed. */
    VERTICAL;

    companion object {
        /** Returns the [SlideDirection] whose ordinal matches [value], or [HORIZONTAL] if out of range. */
        fun fromInt(value: Int): SlideDirection = entries.getOrElse(value) { HORIZONTAL }
    }
}

