package app.simple.felicity.decorations.pager;

/**
 * Controls whether [FelicityPager] operates in standard full-width mode or the carousel
 * mode where a square card is centered and neighboring pages peek from both sides.
 */
enum class PagerMode {
    /** Every page fills the entire pager width — the original behavior. */
    NORMAL,

    /**
     * The active page is displayed as a square card at the center of the pager.
     * The cards to the left and right are partially visible on each side, giving the
     * classic "peek" carousel effect. Spacing between cards is controlled by
     * [FelicityPager.carouselPageSpacingPx].
     */
    CAROUSEL;

    companion object {
        /** Returns the [PagerMode] whose ordinal matches [value], or [NORMAL] if out of range. */
        fun fromInt(value: Int): PagerMode = entries.getOrElse(value) { NORMAL }
    }
}