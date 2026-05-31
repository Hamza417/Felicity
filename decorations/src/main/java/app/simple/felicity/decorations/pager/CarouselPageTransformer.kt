package app.simple.felicity.decorations.pager

import android.view.View

/**
 * A callback that lets you inject your own per-frame animations onto each page card when
 * [FelicityPager] is in [PagerMode.CAROUSEL] mode.
 *
 * Assign an instance to [FelicityPager.carouselPageTransformer]. The pager will call
 * [transformPage] for every visible card after it has already positioned the card via
 * [android.view.View.translationX], so you are free to modify any other property —
 * scale, alpha, rotation, elevation — without worrying about layout.
 */
fun interface CarouselPageTransformer {
    /**
     * Apply your custom visual transform to [page].
     *
     * @param page     The card view to animate.
     * @param position How far this card is from the center, measured in "card steps".
     *                 0.0 means the card is exactly centered. -1.0 means it is one full
     *                 step to the left of center. +1.0 means one full step to the right.
     *                 Fractional values occur during mid-swipe scrolling.
     */
    fun transformPage(page: View, position: Float)
}