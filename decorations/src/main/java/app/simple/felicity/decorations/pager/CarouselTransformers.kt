package app.simple.felicity.decorations.pager

import kotlin.math.abs

/**
 * A set of ready-to-use carousel transformers that you can plug straight into
 * [FelicityPager.carouselPageTransformer]. Each one produces a slightly different
 * visual feel, from a gentle depth fade to a punchy 3D tilt. Mix and match or
 * use them as a starting point for your own custom effect.
 *
 * @author Hamza417
 */
object CarouselTransformers {

    /**
     * Shrinks and fades cards as they move away from center — the most natural-feeling
     * choice for image galleries. The center card is always full-size and fully opaque;
     * side cards are 85 % scale and 50 % alpha.
     */
    val depth: CarouselPageTransformer = CarouselPageTransformer { page, position ->
        val absPos = abs(position).coerceIn(0f, 1f)
        val scale = 1f - 0.15f * absPos
        page.scaleX = scale
        page.scaleY = scale
        page.alpha = 1f - 0.5f * absPos
    }

    /**
     * Shrinks cards toward the sides without touching their alpha — gives the classic
     * "zoom out" feel made popular by the ViewPager2 documentation sample. Side cards
     * sit at 85 % of the center card's size.
     */
    val zoomOut: CarouselPageTransformer = CarouselPageTransformer { page, position ->
        val absPos = abs(position).coerceIn(0f, 1f)
        val scale = 1f - 0.15f * absPos
        page.scaleX = scale
        page.scaleY = scale
        page.alpha = 1f
    }

    /**
     * Dims side cards toward transparency without resizing them. Works especially well
     * when you want the neighbor cards to stay the same physical size but feel "behind"
     * the active card, like flipping through a stack of photos.
     */
    val fade: CarouselPageTransformer = CarouselPageTransformer { page, position ->
        page.alpha = 1f - 0.65f * abs(position).coerceIn(0f, 1f)
        page.scaleX = 1f
        page.scaleY = 1f
    }

    /**
     * Rotates cards around their vertical axis as they scroll off-center, creating a
     * subtle 3D book-flip effect. The center card faces the viewer straight on; side
     * cards tilt up to 20° and shrink slightly to reinforce the depth illusion.
     */
    val tilt: CarouselPageTransformer = CarouselPageTransformer { page, position ->
        val clampedPos = position.coerceIn(-1f, 1f)
        val absPos = abs(clampedPos)
        page.rotationY = -20f * clampedPos
        val scale = 1f - 0.12f * absPos
        page.scaleX = scale
        page.scaleY = scale
        page.alpha = 1f - 0.3f * absPos
    }
}