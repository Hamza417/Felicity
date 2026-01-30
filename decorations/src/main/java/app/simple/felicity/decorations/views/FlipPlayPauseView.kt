package app.simple.felicity.decorations.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.themes.Theme
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A lightweight custom view that draws a Play/Pause icon and morphs between them using a "flip" illusion.
 *
 * ## Visual model
 * - **Pause**: two vertical bars
 * - **Play**: a right-pointing triangle
 *
 * ## Animation strategy
 * - The **left bar** morphs into the triangle by collapsing its right edge into a tip.
 * - The **right bar** fades out while the morph progresses.
 *
 * Progress mapping:
 * - `progress = 0f` → Pause state
 * - `progress = 1f` → Play state
 *
 * The view listens to theme updates via [ThemeChangedListener] and updates [iconColor] automatically.
 */
class FlipPlayPauseView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), ThemeChangedListener {

    /**
     * Tint color used to draw the icon.
     *
     * Setting this updates [paint] and triggers a redraw.
     */
    var iconColor: Int = Color.WHITE
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }

    /**
     * Duration of the play/pause morph animation in milliseconds.
     */
    var animDuration: Long = 300L

    /**
     * Corner smoothing radius applied via [CornerPathEffect].
     *
     * This prevents harsh sharp corners and keeps the icon soft.
     */
    private val cornerRadius = 10f

    /**
     * Current logical state of the icon.
     * - `false` → Pause
     * - `true`  → Play
     */
    private var isPlaying = false

    /**
     * Animation progress:
     * - `0f` → Pause (two bars)
     * - `1f` → Play (triangle)
     */
    private var progress = 0f

    /**
     * Paint used to render both paths.
     *
     * Alpha will be modified temporarily to fade the right bar.
     */
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = iconColor
        pathEffect = CornerPathEffect(cornerRadius)
    }

    /**
     * The morphing left side shape.
     * - Rectangle in Pause
     * - Triangle in Play
     */
    private val leftPath = Path()

    /**
     * The right pause bar.
     * This does not morph; it only fades out during transition to Play.
     */
    private val rightPath = Path()

    /**
     * Animator used to drive [progress] smoothly between states.
     */
    private var animator: ValueAnimator? = null

    init {
        isClickable = true
        setOnClickListener { toggle() }

        if (!isInEditMode) {
            iconColor = ThemeManager.theme.iconTheme.regularIconColor
        }
    }

    /**
     * Toggles between Play and Pause with animation enabled by default.
     */
    fun toggle() {
        setPlaying(!isPlaying, animate = true)
    }

    /**
     * Sets the icon state.
     *
     * @param playing Desired state:
     *  - `true`  → Play (triangle)
     *  - `false` → Pause (two bars)
     * @param animate Whether to animate the transition.
     */
    fun setPlaying(playing: Boolean, animate: Boolean = true) {
        if (isPlaying == playing) return

        isPlaying = playing
        val targetProgress = if (isPlaying) 1f else 0f

        if (animate) {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(progress, targetProgress).apply {
                duration = animDuration
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            animator?.cancel()
            progress = targetProgress
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        /**
         * Size calculation:
         * We draw the icon inside the smallest available square, respecting padding.
         */
        val wAvailable = width - paddingLeft - paddingRight
        val hAvailable = height - paddingTop - paddingBottom
        val size = min(wAvailable, hAvailable).toFloat()

        /**
         * Base "bar height" reference used for both pause bars and play triangle.
         */
        val h = size * 0.5f

        /**
         * Pause shape specs:
         * Two vertical bars of equal width, separated by a gap.
         */
        val barWidth = h / 2.5f
        val gap = barWidth / 1.5f

        /**
         * Play shape spec:
         * An equilateral triangle whose "side" is similar to the bar height.
         *
         * Height of an equilateral triangle = (sqrt(3)/2) * side
         */
        val triHeight = (sqrt(3.0) / 2.0 * h).toFloat()

        /**
         * Reset paths before recomputing geometry for the current frame.
         */
        leftPath.rewind()
        rightPath.rewind()

        /**
         * Right pause bar:
         * Remains a rectangle, but fades out as we approach Play.
         */
        val rightBarX = barWidth + gap
        rightPath.moveTo(rightBarX, 0f)
        rightPath.lineTo(rightBarX + barWidth, 0f)
        rightPath.lineTo(rightBarX + barWidth, h)
        rightPath.lineTo(rightBarX, h)
        rightPath.close()

        /**
         * Left bar morph:
         *
         * Pause (rectangle):
         *   (0,0) -> (barWidth,0) -> (barWidth,h) -> (0,h)
         *
         * Play (triangle-ish):
         *   (0,0) -> (triHeight, h/2) -> (triHeight, h/2) -> (0,h)
         *
         * Morphing happens by moving the rectangle's right edge into a single tip at center.
         */
        val currentTipX = lerp(barWidth, triHeight, progress)
        val currentTopRightY = lerp(0f, h / 2f, progress)
        val currentBottomRightY = lerp(h, h / 2f, progress)

        leftPath.moveTo(0f, 0f)
        leftPath.lineTo(currentTipX, currentTopRightY)
        leftPath.lineTo(currentTipX, currentBottomRightY)
        leftPath.lineTo(0f, h)
        leftPath.close()

        /**
         * Centering:
         *
         * We translate canvas to the center of the view, then offset the icon horizontally so:
         * - Pause (2 bars + gap) appears centered
         * - Play triangle appears centered
         *
         * This avoids the icon "drifting" during morph.
         */
        canvas.save()
        canvas.translate(width / 2f, height / 2f)

        val totalWidthPause = (barWidth * 2) + gap
        val totalWidthPlay = triHeight

        val startOffsetPause = -totalWidthPause / 2f
        val startOffsetPlay = -totalWidthPlay / 2f + (barWidth * 0.1f)

        val currentOffsetX = lerp(startOffsetPause, startOffsetPlay, progress)

        canvas.translate(currentOffsetX, -h / 2f)

        /**
         * Draw order:
         * 1) Morphing left shape (always fully opaque)
         * 2) Right bar, fading out (alpha = 255 -> 0)
         */
        paint.alpha = 255
        canvas.drawPath(leftPath, paint)

        if (progress < 1f) {
            val fadeAlpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
            paint.alpha = fadeAlpha
            canvas.drawPath(rightPath, paint)
        }

        canvas.restore()

        /**
         * Restore alpha for any future drawing passes.
         */
        paint.alpha = 255
    }

    /**
     * Saves view state so the play/pause state survives:
     * - configuration changes
     * - list recycling
     * - view recreation
     */
    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable("superState", super.onSaveInstanceState())
        bundle.putBoolean("isPlaying", isPlaying)
        return bundle
    }

    /**
     * Restores view state saved by [onSaveInstanceState].
     */
    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            val playing = state.getBoolean("isPlaying")
            isPlaying = playing
            progress = if (playing) 1f else 0f
            super.onRestoreInstanceState(state.getParcelable("superState"))
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    /**
     * Theme callback:
     * Updates the icon tint when the app theme changes.
     */
    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        super.onThemeChanged(theme, animate)
        iconColor = theme.iconTheme.regularIconColor
    }

    /**
     * Register for theme updates when the view becomes visible/attached.
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ThemeManager.addListener(this)
    }

    /**
     * Unregister from theme updates to avoid leaks when detached.
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ThemeManager.removeListener(this)
    }

    /**
     * Linear interpolation helper.
     */
    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }
}
