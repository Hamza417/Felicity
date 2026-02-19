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
import androidx.core.graphics.withTranslation
import androidx.core.os.BundleCompat
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.themes.Theme
import com.google.android.material.math.MathUtils.lerp
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A lightweight animated Play/Pause button that morphs between:
 *
 * - **Pause** → Two rounded vertical bars
 * - **Play**  → A rounded equilateral triangle
 *
 * ### Animation Model
 * The **left pause bar morphs into the play triangle**, while the **right bar fades out**.
 *
 * Progress mapping:
 * - `0f` → Pause
 * - `1f` → Play
 *
 * The view maintains visual centering during morph to prevent drift.
 *
 * ### Corner Handling
 * Uses [CornerPathEffect] for smooth rounded geometry. A true triangle is introduced
 * slightly before the morph completes (`progress >= 0.9f`) to avoid degenerate vertices
 * and ensure consistent rounding from the start of the visible transition.
 *
 * ### Theme Awareness
 * Implements [ThemeChangedListener] and automatically updates icon tint on theme change.
 */
@Suppress("UnnecessaryVariable")
class FlipPlayPauseView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), ThemeChangedListener {

    /**
     * Tint color of the icon.
     * Updating this invalidates the view.
     */
    var iconColor: Int = Color.WHITE
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }

    /**
     * Duration of morph animation in milliseconds.
     */
    var animDuration: Long = 300L

    /**
     * Radius used by [CornerPathEffect] to smooth all icon corners.
     */
    private val cornerRadius = 10f

    /**
     * Logical playback state.
     * `false` → Pause, `true` → Play
     */
    private var isPlaying = false

    /**
     * Morph progress between Pause and Play.
     */
    private var progress = 0f

    /**
     * Paint used for rendering both shapes.
     */
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = iconColor
        pathEffect = CornerPathEffect(cornerRadius)
    }

    /** Morphing left shape (Pause bar → Triangle) */
    private val leftPath = Path()

    /** Right pause bar (fades out during morph) */
    private val rightPath = Path()

    /** Animator driving morph progress */
    private var animator: ValueAnimator? = null

    init {
        isClickable = true
        setOnClickListener { toggle() }

        if (!isInEditMode) {
            iconColor = ThemeManager.theme.iconTheme.regularIconColor
        }
    }

    /**
     * Toggles between Play and Pause with animation.
     */
    fun toggle() {
        setPlaying(!isPlaying, true)
    }

    /**
     * Sets the current playback state.
     *
     * @param playing Target state
     * @param animate Whether to animate transition
     */
    fun setPlaying(playing: Boolean, animate: Boolean = true) {
        if (isPlaying == playing) return

        isPlaying = playing
        val target = if (playing) PROGRESS_PLAY else PROGRESS_PAUSE

        animator?.cancel()

        if (animate) {
            animator = ValueAnimator.ofFloat(progress, target).apply {
                duration = animDuration
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            progress = target
            invalidate()
        }
    }

    fun paused(animate: Boolean = true) {
        setPlaying(false, animate)
    }

    fun playing(animate: Boolean = true) {
        setPlaying(true, animate)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // ----- Compute drawable square -----
        val wAvail = width - paddingLeft - paddingRight
        val hAvail = height - paddingTop - paddingBottom
        val size = min(wAvail, hAvail).toFloat()

        val h = size * 0.5f

        // Pause bar geometry
        val barWidth = h / 2.5f
        val gap = barWidth / 1.5f

        // Equilateral triangle height
        val triHeight = (sqrt(3.0) / 2.0 * h).toFloat()

        leftPath.rewind()
        rightPath.rewind()

        // ----- Right pause bar -----
        val rightBarX = barWidth + gap
        rightPath.moveTo(rightBarX, 0f)
        rightPath.lineTo(rightBarX + barWidth, 0f)
        rightPath.lineTo(rightBarX + barWidth, h)
        rightPath.lineTo(rightBarX, h)
        rightPath.close()

        // ----- Left morphing shape -----
        if (progress >= 0.9f) {
            /**
             * Switch to true triangle slightly early to avoid degenerate
             * collapsing edge and ensure CornerPathEffect rounds properly.
             */
            leftPath.moveTo(0f, 0f)
            leftPath.lineTo(triHeight, h / 2f)
            leftPath.lineTo(0f, h)
            leftPath.close()
        } else {
            val tipX = lerp(barWidth, triHeight, progress)
            val topY = lerp(0f, h / 2f, progress)
            val bottomY = lerp(h, h / 2f, progress)

            leftPath.moveTo(0f, 0f)
            leftPath.lineTo(tipX, topY)
            leftPath.lineTo(tipX, bottomY)
            leftPath.lineTo(0f, h)
            leftPath.close()
        }

        // ----- Center icon -----
        canvas.withTranslation(width / 2f, height / 2f) {
            val totalPauseWidth = barWidth * 2 + gap
            val totalPlayWidth = triHeight

            val offsetPause = -totalPauseWidth / 2f
            val offsetPlay = -totalPlayWidth / 2f + (barWidth * 0.1f)

            val offsetX = lerp(offsetPause, offsetPlay, progress)
            translate(offsetX, -h / 2f)

            // ----- Draw shapes -----
            paint.alpha = 255
            drawPath(leftPath, paint)

            if (progress < 1f) {
                paint.alpha = (255 * (1f - progress)).toInt()
                drawPath(rightPath, paint)
            }

        }
        paint.alpha = 255
    }

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable(KEY_SUPER_STATE, super.onSaveInstanceState())
        bundle.putBoolean(KEY_IS_PLAYING, isPlaying)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            isPlaying = state.getBoolean(KEY_IS_PLAYING)
            progress = if (isPlaying) PROGRESS_PLAY else PROGRESS_PAUSE
            super.onRestoreInstanceState(
                    BundleCompat.getParcelable(state, KEY_SUPER_STATE, Parcelable::class.java)
            )
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        iconColor = theme.iconTheme.regularIconColor
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ThemeManager.addListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ThemeManager.removeListener(this)
    }

    companion object {
        private const val TAG = "FlipPlayPauseView"
        private const val KEY_SUPER_STATE = "superState"
        private const val KEY_IS_PLAYING = "isPlaying"
        private const val PROGRESS_PLAY = 0f
        private const val PROGRESS_PAUSE = 1f
    }
}