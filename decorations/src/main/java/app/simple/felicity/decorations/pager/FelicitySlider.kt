package app.simple.felicity.decorations.pager

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.withStyledAttributes
import app.simple.felicity.decoration.R

/**
 * The direction in which the [FelicitySlider] edge-fade gradient travels.
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

/**
 * A self-contained image-slider compound widget built on top of [FelicityPager].
 *
 * ## Features
 * - Auto-slides through pages with a configurable delay.
 * - Dot indicators at the bottom center; the highlighted dot chases the current page
 *   using an underdamped spring for an elastic, droppy feel.
 * - A physics "swiftness" parameter controls the slide animation speed.
 * - User swipe support inherited from [FelicityPager].
 * - Optional directional edge-fade that grades the content from full alpha down to 0 %.
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
 *     app:fadeDirection="top_to_bottom" />
 * ```
 *
 * ## Usage (code)
 * ```kotlin
 * slider.setAdapter(myPageAdapter)
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
     * When `true`, a [LinearGradient] mask is applied during [dispatchDraw] that grades
     * the rendered content from full alpha down to 0 % in the direction set by [fadeDirection],
     * starting at the normalized position set by [fadeStartFraction].
     */
    var fadeEnabled: Boolean = false
        set(v) {
            field = v
            updateFadeShader()
            invalidate()
        }

    /**
     * Normalized position in [0..1] along the [fadeDirection] axis at which the fade begins.
     *
     * - `0.0` — the gradient covers the entire view from the opaque edge to the transparent edge.
     * - `0.5` — the first half of the view (from the opaque edge) is fully visible; the second
     *           half fades from full alpha to 0 % (default).
     * - `1.0` — no visible fade; the entire view remains fully opaque.
     *
     * Values outside [0..1] are coerced to the nearest boundary.
     */
    var fadeStartFraction: Float = 0.5f
        set(v) {
            field = v.coerceIn(0f, 1f)
            updateFadeShader()
            invalidate()
        }

    /**
     * The direction the edge-fade gradient travels. Default: [FadeDirection.TOP_TO_BOTTOM].
     *
     * The "transparent" end is always the edge named by the chosen [FadeDirection] value.
     */
    var fadeDirection: FadeDirection = FadeDirection.TOP_TO_BOTTOM
        set(v) {
            field = v
            updateFadeShader()
            invalidate()
        }

    /**
     * Paint used exclusively for the edge-fade DST_IN mask in [dispatchDraw].
     * Its [Paint.shader] is rebuilt whenever relevant properties or the view size change.
     */
    private val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
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
            }
        }

        // Apply initial values
        pager.animationDurationMs = swiftnessToDurationMs(slideSwiftness)
        dots.springStiffness = springStiffness
        dots.springDamping = springDamping

        // Build layout
        addView(pager, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val dotsPadding = dpToPx(12f).toInt()
        val dotsParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dotsPadding
        }
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateFadeShader()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (!fadeEnabled || fadePaint.shader == null) {
            super.dispatchDraw(canvas)
            return
        }
        val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.dispatchDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)
        canvas.restoreToCount(saveCount)
    }

    /**
     * Rebuilds the [LinearGradient] shader on [fadePaint] based on the current [fadeDirection],
     * [fadeStartFraction], and view dimensions.
     *
     * The gradient runs from opaque black (used as the DST_IN alpha source) to transparent,
     * starting at the fractional offset defined by [fadeStartFraction] along the fade axis.
     * Areas before the start offset are clamped to fully opaque; areas beyond the far edge
     * are clamped to fully transparent.
     *
     * No-ops when the view has not yet been measured (width or height == 0).
     */
    private fun updateFadeShader() {
        if (width == 0 || height == 0) return

        val x0: Float
        val y0: Float
        val x1: Float
        val y1: Float

        when (fadeDirection) {
            FadeDirection.TOP_TO_BOTTOM -> {
                // Top stays opaque; fade begins at fadeStartFraction down and ends at the bottom.
                x0 = 0f; y0 = height * fadeStartFraction
                x1 = 0f; y1 = height.toFloat()
            }
            FadeDirection.BOTTOM_TO_TOP -> {
                // Bottom stays opaque; fade begins at (1 - fadeStartFraction) and ends at the top.
                x0 = 0f; y0 = height * (1f - fadeStartFraction)
                x1 = 0f; y1 = 0f
            }
            FadeDirection.LEFT_TO_RIGHT -> {
                // Left stays opaque; fade begins at fadeStartFraction across and ends at the right.
                x0 = width * fadeStartFraction; y0 = 0f
                x1 = width.toFloat(); y1 = 0f
            }
            FadeDirection.RIGHT_TO_LEFT -> {
                // Right stays opaque; fade begins at (1 - fadeStartFraction) across and ends at the left.
                x0 = width * (1f - fadeStartFraction); y0 = 0f
                x1 = 0f; y1 = 0f
            }
        }

        fadePaint.shader = LinearGradient(
                x0, y0, x1, y1,
                Color.BLACK, Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        )
    }

    private fun dpToPx(dp: Float): Float =
        dp * resources.displayMetrics.density
}

