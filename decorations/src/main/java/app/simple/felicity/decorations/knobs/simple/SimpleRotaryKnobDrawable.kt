package app.simple.felicity.decorations.knobs.simple

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.annotation.Px

/**
 * A programmatic [RotaryKnobDrawable] that draws a circular knob with a small position
 * indicator dot at the top.
 *
 * **Idle state**: ring and indicator dot use [idleColor] (grey/muted).
 * **Pressed state**: they animate to [accentColor].
 *
 * The arc track and min/max tick marks are intentionally NOT drawn here —
 * they are drawn by [RotaryKnobView] directly on its own canvas so they stay
 * stationary while the knob rotates.
 *
 * @param accentColor              Color when touched.
 * @param idleColor                Color when not touched (grey/muted).
 * @param bodyColor                Fill color for the knob body circle.
 * @param strokeWidthFraction      Ring stroke width as a fraction of the knob radius (0..1).
 * @param indicatorRadiusFraction  Radius of the indicator dot as a fraction of the knob radius.
 * @param intrinsicSizePx          Reported intrinsic size in pixels so wrap_content works.
 */
class SimpleRotaryKnobDrawable(
        @ColorInt var accentColor: Int = DEFAULT_ACCENT_COLOR,
        @ColorInt var idleColor: Int = DEFAULT_IDLE_COLOR,
        @ColorInt var bodyColor: Int = DEFAULT_BODY_COLOR,
        var strokeWidthFraction: Float = DEFAULT_STROKE_WIDTH_FRACTION,
        var indicatorRadiusFraction: Float = DEFAULT_INDICATOR_RADIUS_FRACTION,
        @Px private var intrinsicSizePx: Int = DEFAULT_INTRINSIC_SIZE_PX
) : RotaryKnobDrawable() {

    // ── Paints ──────────────────────────────────────────────────────────────────

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // ── State ────────────────────────────────────────────────────────────────────

    @ColorInt
    var currentStateColor: Int = idleColor
        private set

    /**
     * Optional callback invoked on every animation frame when the state color changes.
     * [RotaryKnobView] uses this to keep its arc/tick drawing in sync without needing
     * to hijack the drawable's [android.graphics.drawable.Drawable.Callback].
     */
    var onColorChanged: (() -> Unit)? = null

    private var colorAnimator: ValueAnimator? = null

    // ── RotaryKnobDrawable ───────────────────────────────────────────────────────

    override fun onPressedStateChanged(pressed: Boolean, animationDuration: Int) {
        val targetColor = if (pressed) accentColor else idleColor
        colorAnimator?.cancel()
        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), currentStateColor, targetColor).apply {
            duration = animationDuration.toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                currentStateColor = animator.animatedValue as Int
                invalidateSelf()
                onColorChanged?.invoke()
            }
            start()
        }
    }

    // ── Drawable ─────────────────────────────────────────────────────────────────

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return

        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val knobRadius = minOf(b.width(), b.height()) / 2f
        val strokeWidth = knobRadius * strokeWidthFraction
        val halfStroke = strokeWidth / 2f
        val bodyRadius = knobRadius - halfStroke

        // Body fill
        bodyPaint.color = bodyColor
        canvas.drawCircle(cx, cy, bodyRadius, bodyPaint)

        // Ring outline
        ringPaint.color = currentStateColor
        ringPaint.strokeWidth = strokeWidth
        canvas.drawOval(
                RectF(cx - bodyRadius, cy - bodyRadius, cx + bodyRadius, cy + bodyRadius),
                ringPaint
        )

        // Indicator dot — near the top
        val indicatorRadius = knobRadius * indicatorRadiusFraction
        val indicatorCy = cy - bodyRadius * INDICATOR_DISTANCE_FRACTION
        indicatorPaint.color = currentStateColor
        canvas.drawCircle(cx, indicatorCy, indicatorRadius, indicatorPaint)
    }

    override fun getIntrinsicWidth(): Int = intrinsicSizePx
    override fun getIntrinsicHeight(): Int = intrinsicSizePx

    override fun setAlpha(alpha: Int) {
        bodyPaint.alpha = alpha
        ringPaint.alpha = alpha
        indicatorPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bodyPaint.colorFilter = colorFilter
        ringPaint.colorFilter = colorFilter
        indicatorPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /** Immediately snap to the idle color without animation (useful when attaching to a new view). */
    fun resetToIdle() {
        colorAnimator?.cancel()
        currentStateColor = idleColor
        invalidateSelf()
    }

    /**
     * Update the reported intrinsic size (in pixels). Call this once you know the
     * density-aware pixel size (e.g. from a dimension resource), then set the drawable on the view.
     */
    fun setIntrinsicSize(@Px sizePx: Int) {
        intrinsicSizePx = sizePx
        invalidateSelf()
    }

    companion object {
        @ColorInt
        val DEFAULT_ACCENT_COLOR: Int = 0xFF2D85E6.toInt()
        @ColorInt
        val DEFAULT_IDLE_COLOR: Int = 0x7A464646
        @ColorInt
        val DEFAULT_BODY_COLOR: Int = 0xFFFFFFFF.toInt()

        const val DEFAULT_STROKE_WIDTH_FRACTION = 0.015f
        const val DEFAULT_INDICATOR_RADIUS_FRACTION = 0.074f

        /** How far the indicator dot sits from centre, as a fraction of body radius. */
        private const val INDICATOR_DISTANCE_FRACTION = 0.81f

        const val DEFAULT_INTRINSIC_SIZE_PX = 500
    }
}
