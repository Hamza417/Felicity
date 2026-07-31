package app.simple.felicity.decorations.pager

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.withStyledAttributes
import app.simple.felicity.decoration.R
import app.simple.felicity.shared.utils.UnitUtils.dpToPx

/**
 * A self-contained image-slider compound widget built on top of [FelicityPager].
 *
 * ## Features
 * - Auto-slides through pages with a configurable delay.
 * - Dot indicators that follow the scroll direction; the highlighted dot chases the current
 *   page using an underdamped spring for an elastic, droppy feel.
 * - A physics "swiftness" parameter controls the slide animation speed.
 * - User swipe support inherited from [FelicityPager].
 * - Optional directional edge-fade that grades the content from full alpha down to 0 %.
 * - Supports [PagerMode.NORMAL] (full-size pages) and [PagerMode.CAROUSEL] (centered square
 *   cards with neighbors peeking in from the sides).
 * - Supports [SlideDirection.HORIZONTAL] (left/right) and [SlideDirection.VERTICAL] (up/down)
 *   scrolling in both pager modes. The dot indicator repositions itself automatically to match
 *   the chosen scroll axis.
 *
 * ## Usage (XML)
 * ```xml
 * <app.simple.felicity.decorations.pager.FelicitySlider
 *     android:id="@+id/felicity_slider"
 *     android:layout_width="match_parent"
 *     android:layout_height="256dp"
 *     app:slideIntervalMs="3000"
 *     app:slideSwiftness="0.7"
 *     app:springStiffness="420"
 *     app:springDamping="0.62"
 *     app:showIndicator="true"
 *     app:fadeEnabled="true"
 *     app:fadeStartFraction="0.5"
 *     app:fadeDirection="top_to_bottom"
 *     app:slideDirection="vertical"
 *     app:pagerMode="carousel" />
 * ```
 *
 * ## Usage (code)
 * ```kotlin
 * slider.setAdapter(myPageAdapter)
 * slider.slideDirection = SlideDirection.VERTICAL
 * slider.pagerMode = PagerMode.CAROUSEL
 * slider.start()   // begin auto-sliding
 * slider.stop()    // pause
 * ```
 *
 * @param slideIntervalMs    Milliseconds between automatic page advances (default 3 000).
 * @param slideSwiftness     0..1 – how "swift" each transition feels.
 *                           1.0 = very fast (≈ 220 ms), 0.0 = very slow (≈ 900 ms).
 *                           Default 0.6.
 * @param springStiffness    Spring stiffness for the dot indicator blob (default 420).
 * @param springDamping      Damping ratio for the dot indicator blob (default 0.62);
 *                           values below 1 produce bouncy overshoot.
 * @param showIndicator      Whether the dot indicator row is visible (default true).
 * @param fadeEnabled        Whether the directional edge-fade effect is applied (default false).
 * @param fadeStartFraction  Normalized position in [0..1] along the fade direction at which
 *                           the gradient begins. 0.0 = fade covers the entire view from the
 *                           opaque edge; 0.5 = the first half is fully visible, the second
 *                           half fades out (default); 1.0 = no visible fade.
 * @param fadeDirection      The direction the gradient travels (default [FadeDirection.TOP_TO_BOTTOM]).
 * @param slideDirection     Which axis pages scroll along (default [SlideDirection.HORIZONTAL]).
 * @param pagerMode          Whether pages fill the full view ([PagerMode.NORMAL]) or appear as
 *                           centered cards ([PagerMode.CAROUSEL]) (default [PagerMode.NORMAL]).
 *
 * @author Hamza417
 */
class FelicitySlider @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    // ── Sub-views ────────────────────────────────────────────────────────────────
    val pager: FelicityPager = FelicityPager(context)
    val dots: DotsIndicatorView = DotsIndicatorView(context)

    // ── Configurable properties ──────────────────────────────────────────────────

    /**
     * Interval in milliseconds between automatic page advances.
     * Set to 0 to disable auto-sliding. Default: 3 000 ms.
     */
    var slideIntervalMs: Long = 3_000L
        set(v) {
            field = v.coerceAtLeast(0L)
            if (isRunning) restartAutoSlide()
        }

    /**
     * Controls slide animation speed.
     * Range: 0.0 (very slow, ~900 ms) … 1.0 (very fast, ~220 ms). Default: 0.3.
     */
    var slideSwiftness: Float = 0.3f
        set(v) {
            field = v.coerceIn(0f, 1f)
            pager.animationDurationMs = swiftnessToDurationMs(field)
        }

    /**
     * Spring stiffness for the elastic dot-indicator animation. Higher = snappier.
     * Default: 220.
     */
    var springStiffness: Float = 220f
        set(v) {
            field = v
            dots.springStiffness = v
        }

    /**
     * Damping ratio for the dot-indicator spring.
     * < 1.0 produces underdamped (bouncy) behavior. Default: 0.62.
     */
    var springDamping: Float = 0.62f
        set(v) {
            field = v
            dots.springDamping = v
        }

    /**
     * Whether the dot indicator bar is visible. Default: true.
     */
    var showIndicator: Boolean = true
        set(v) {
            field = v
            dots.visibility = if (v) View.VISIBLE else View.GONE
        }

    /**
     * Whether the directional edge-fade effect is active. Default: false.
     *
     * Delegates directly to the underlying [FelicityPager]. When `true`, a gradient
     * mask is applied over the pager content in the direction set by [fadeDirection],
     * starting at the normalized position set by [fadeStartFraction].
     */
    var fadeEnabled: Boolean = false
        set(v) {
            field = v
            pager.fadeEnabled = v
        }

    /**
     * Normalized position in [0..1] along the [fadeDirection] axis at which the fade begins.
     *
     * - `0.0` — the gradient covers the entire pager from the opaque edge to the transparent edge.
     * - `0.5` — the first half of the pager (from the opaque edge) is fully visible; the second
     *           half fades from full alpha to 0 % (default).
     * - `1.0` — no visible fade; the entire pager remains fully opaque.
     *
     * Delegates directly to the underlying [FelicityPager].
     */
    var fadeStartFraction: Float = 0.5f
        set(v) {
            field = v.coerceIn(0f, 1f)
            pager.fadeStartFraction = field
        }

    /**
     * The direction the edge-fade gradient travels. Default: [FadeDirection.TOP_TO_BOTTOM].
     *
     * Delegates directly to the underlying [FelicityPager].
     */
    var fadeDirection: FadeDirection = FadeDirection.TOP_TO_BOTTOM
        set(v) {
            field = v
            pager.fadeDirection = v
        }

    /**
     * Which axis pages scroll along. Switching this at runtime repositions the dot indicator
     * so it always appears parallel to the scroll axis — bottom-center for horizontal paging,
     * right-center for vertical paging. Delegates to the underlying [FelicityPager].
     *
     * Default: [SlideDirection.HORIZONTAL].
     */
    var slideDirection: SlideDirection = SlideDirection.HORIZONTAL
        set(v) {
            field = v
            pager.slideDirection = v
            repositionDots(v)
        }

    /**
     * Whether pages fill the entire pager ([PagerMode.NORMAL]) or appear as centered square
     * cards with neighbors peeking in from the sides ([PagerMode.CAROUSEL]). Delegates to
     * the underlying [FelicityPager].
     *
     * Default: [PagerMode.NORMAL].
     */
    var pagerMode: PagerMode = PagerMode.NORMAL
        set(v) {
            field = v
            pager.pagerMode = v
        }

    /**
     * Pixel gap between adjacent cards in carousel mode. Delegates to [FelicityPager].
     * Default: 16 dp (applied automatically by the pager if never set).
     */
    var carouselPageSpacingPx: Float
        get() = pager.carouselPageSpacingPx
        set(v) {
            pager.carouselPageSpacingPx = v
        }

    /**
     * How many pixels of each neighboring card peek in from the sides in carousel mode.
     * Delegates to [FelicityPager]. Default: 48 dp.
     */
    var carouselPeekPx: Float
        get() = pager.carouselPeekPx
        set(v) {
            pager.carouselPeekPx = v
        }

    /**
     * Fixed pixel size of the square carousel card. Set to 0 for auto-sizing.
     * Delegates to [FelicityPager]. Default: 0 (auto).
     */
    var carouselCardSizePx: Int
        get() = pager.carouselCardSizePx
        set(v) {
            pager.carouselCardSizePx = v
        }

    /**
     * Whether the neighbor cards are visible in carousel mode. When false, only the center
     * card is shown. Delegates to [FelicityPager]. Default: true.
     */
    var carouselShowSidePages: Boolean
        get() = pager.carouselShowSidePages
        set(v) {
            pager.carouselShowSidePages = v
        }

    /**
     * How many dp to shrink side cards relative to the center card in carousel mode.
     * 0 disables the built-in scaling. Delegates to [FelicityPager]. Default: 0.
     */
    var carouselSideScaleDp: Float
        get() = pager.carouselSideScaleDp
        set(v) {
            pager.carouselSideScaleDp = v
        }

    private var isRunning = false

    // ── Initialisation ───────────────────────────────────────────────────────────

    init {
        // Read XML attributes if present
        if (attrs != null) {
            context.withStyledAttributes(attrs, R.styleable.FelicitySlider, defStyleAttr, 0) {
                slideIntervalMs = getInt(R.styleable.FelicitySlider_slideIntervalMs, 3000).toLong()
                slideSwiftness = getFloat(R.styleable.FelicitySlider_slideSwiftness, 0.6f)
                springStiffness = getFloat(R.styleable.FelicitySlider_springStiffness, 420f)
                springDamping = getFloat(R.styleable.FelicitySlider_springDamping, 0.62f)
                showIndicator = getBoolean(R.styleable.FelicitySlider_showIndicator, true)
                fadeEnabled = getBoolean(R.styleable.FelicitySlider_fadeEnabled, false)
                fadeStartFraction = getFloat(R.styleable.FelicitySlider_fadeStartFraction, 0.5f)
                fadeDirection = FadeDirection.fromInt(
                        getInt(R.styleable.FelicitySlider_fadeDirection, 0)
                )
                slideDirection = SlideDirection.fromInt(
                        getInt(R.styleable.FelicitySlider_slideDirection, 0)
                )
                pagerMode = PagerMode.fromInt(
                        getInt(R.styleable.FelicitySlider_pagerMode, 0)
                )
                if (hasValue(R.styleable.FelicitySlider_carouselPageSpacing)) {
                    carouselPageSpacingPx = getDimension(R.styleable.FelicitySlider_carouselPageSpacing, 0f)
                }
                if (hasValue(R.styleable.FelicitySlider_carouselPeekMargin)) {
                    carouselPeekPx = getDimension(R.styleable.FelicitySlider_carouselPeekMargin, 0f)
                }
                carouselCardSizePx = getDimensionPixelSize(R.styleable.FelicitySlider_carouselCardSize, 0)
                carouselShowSidePages = getBoolean(R.styleable.FelicitySlider_carouselShowSidePages, true)
                if (hasValue(R.styleable.FelicitySlider_carouselSideScaleDp)) {
                    val rawPx = getDimension(R.styleable.FelicitySlider_carouselSideScaleDp, 0f)
                    carouselSideScaleDp = rawPx / resources.displayMetrics.density
                }
            }
        }

        // Apply initial values
        pager.animationDurationMs = swiftnessToDurationMs(slideSwiftness)
        dots.springStiffness = springStiffness
        dots.springDamping = springDamping

        // Build layout
        addView(pager, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val dotsPadding = dpToPx(12f).toInt()
        val dotsParams = buildDotsParams(slideDirection, dotsPadding)
        addView(dots, dotsParams)
        dots.visibility = if (showIndicator) View.VISIBLE else View.GONE

        // Wire pager → dots
        pager.addOnPageChangeListener(object : FelicityPager.OnPageChangeListener {
            override fun onPageSelected(position: Int) {
                dots.setCurrentPage(position)
            }

            override fun onPageScrollStateChanged(state: Int) {
                // When the user starts dragging, also reset the auto-slide timer
                if (state == FelicityPager.SCROLL_STATE_DRAGGING && isRunning) {
                    restartAutoSlide()
                }
            }
        })
    }

    // ── Public API ───────────────────────────────────────────────────────────────

    /**
     * Sets the [FelicityPager.PageAdapter] on the underlying pager and updates the dot count.
     */
    fun setAdapter(adapter: FelicityPager.PageAdapter?) {
        pager.setAdapter(adapter)
        dots.setCount(adapter?.getCount() ?: 0)
    }

    /**
     * Notifies the slider that the data set has changed.
     * Also call [setItemCount] if the total page count changed.
     */
    fun notifyDataSetChanged() {
        pager.notifyDataSetChanged()
    }

    /**
     * Updates the dot count explicitly. Call this after the adapter's data changes.
     */
    fun setItemCount(count: Int) {
        dots.setCount(count)
    }

    /** Starts auto-slide if [slideIntervalMs] > 0. */
    fun start() {
        isRunning = true
        if (slideIntervalMs > 0) {
            pager.startAutoSlide(slideIntervalMs, loop = true)
        }
    }

    /** Pauses auto-sliding without resetting the current page. */
    fun stop() {
        isRunning = false
        pager.stopAutoSlide()
    }

    /** Returns the current page index. */
    fun getCurrentItem(): Int = pager.getCurrentItem()

    /** Navigates to [item] (optionally animated). */
    fun setCurrentItem(item: Int, smoothScroll: Boolean = true) {
        pager.setCurrentItem(item, smoothScroll)
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isRunning) start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private fun restartAutoSlide() {
        pager.stopAutoSlide()
        if (slideIntervalMs > 0) pager.startAutoSlide(slideIntervalMs, loop = true)
    }

    /**
     * Maps a swiftness value [0, 1] to an animation duration in milliseconds.
     * swiftness = 1.0 → ~220 ms, swiftness = 0.0 → ~900 ms (linear interpolation).
     */
    private fun swiftnessToDurationMs(swiftness: Float): Long {
        val ms = 900f - swiftness * (900f - 220f)
        return ms.toLong().coerceIn(100L, 1200L)
    }

    /**
     * Builds the [LayoutParams] that pin the dot indicator to the correct edge for
     * the given [direction]. For horizontal scrolling the dots sit at the bottom center.
     * For vertical scrolling they move to the end (right) edge, centered vertically, and
     * are rotated 90 degrees so the dot row runs parallel to the scroll axis.
     */
    private fun buildDotsParams(direction: SlideDirection, padding: Int): LayoutParams {
        return LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (direction == SlideDirection.VERTICAL) {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                marginEnd = padding
            } else {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = padding
            }
        }
    }

    /**
     * Moves the dot indicator to the right edge (with 90-degree rotation) when [direction]
     * is vertical, or back to the bottom center when it is horizontal. Also resets the
     * rotation so a dot row that was previously tilted returns to its natural orientation.
     */
    private fun repositionDots(direction: SlideDirection) {
        val padding = dpToPx(12f).toInt()
        dots.layoutParams = buildDotsParams(direction, padding)
        dots.rotation = if (direction == SlideDirection.VERTICAL) 90f else 0f
    }
}

