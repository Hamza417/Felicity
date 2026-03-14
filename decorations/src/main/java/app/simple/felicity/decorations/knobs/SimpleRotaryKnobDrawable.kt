package app.simple.felicity.decorations.knobs

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
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme

/**
 * A programmatic [RotaryKnobDrawable] that draws a circular knob with a small position
 * indicator dot at the top.
 *
 * **Idle state**: ring and indicator dot use [idleColor] (gray / muted).
 * **Pressed state**: they animate to [accentColor].
 *
 * The arc track and min / max tick marks are intentionally NOT drawn here —
 * they are drawn by [RotaryKnobView] directly on its own canvas so they stay
 * stationary while the knob rotates.
 *
 * Theme colors are managed internally: the drawable registers with
 * [ThemeManager] during [onAttachedToKnobView] and unregisters during
 * [onDetachedFromKnobView], so [RotaryKnobView] never needs to forward theme events.
 *
 * @param strokeWidthFraction      Ring stroke width as a fraction of the knob radius (0..1).
 * @param indicatorRadiusFraction  Radius of the indicator dot as a fraction of the knob radius.
 * @param intrinsicSizePx          Reported intrinsic size in pixels so wrap_content works.
 *
 * @author Hamza417
 */
class SimpleRotaryKnobDrawable(
        var strokeWidthFraction: Float = DEFAULT_STROKE_WIDTH_FRACTION,
        var indicatorRadiusFraction: Float = DEFAULT_INDICATOR_RADIUS_FRACTION,
        @Px private var intrinsicSizePx: Int = DEFAULT_INTRINSIC_SIZE_PX
) : RotaryKnobDrawable(), ThemeChangedListener {

    @ColorInt
    private var accentColor: Int = DEFAULT_ACCENT_COLOR

    @ColorInt
    private var idleColor: Int = DEFAULT_IDLE_COLOR

    @ColorInt
    private var bodyColor: Int = DEFAULT_BODY_COLOR

    // ── Paints ──────────────────────────────────────────────────────────────────

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** Ring outline. Glow is achieved via [Paint.setShadowLayer] — requires software layer. */
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Indicator dot. Glow is achieved via [Paint.setShadowLayer] — requires software layer. */
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // ── State ────────────────────────────────────────────────────────────────────

    @ColorInt
    @get:JvmName("getAnimatedStateColor")
    var currentStateColor: Int = idleColor
        private set

    private var colorAnimator: ValueAnimator? = null

    // ── RotaryKnobDrawable ───────────────────────────────────────────────────────

    override fun getCurrentStateColor(): Int = currentStateColor

    /** Returns `true` — [ringPaint] and [indicatorPaint] rely on [Paint.setShadowLayer] for glow. */
    override fun requiresSoftwareLayer(): Boolean = true

    override fun onPressedStateChanged(pressed: Boolean, animationDuration: Int) {
        val targetColor = if (pressed) accentColor else idleColor
        colorAnimator?.cancel()
        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), currentStateColor, targetColor).apply {
            duration = animationDuration.toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                currentStateColor = animator.animatedValue as Int
                invalidateSelf()
            }
            start()
        }
    }

    override fun onAttachedToKnobView() {
        ThemeManager.addListener(this)
        val theme = ThemeManager.theme
        if (theme.viewGroupTheme != null) {
            applyTheme(theme)
        }
        applyAccent(ThemeManager.accent)
    }

    override fun onDetachedFromKnobView() {
        ThemeManager.removeListener(this)
    }

    // ── ThemeChangedListener ──────────────────────────────────────────────────────

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        applyTheme(theme)
    }

    override fun onAccentChanged(accent: Accent) {
        applyAccent(accent)
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
        val indicatorRadius = knobRadius * indicatorRadiusFraction
        val indicatorCy = cy - bodyRadius * INDICATOR_DISTANCE_FRACTION
        val glowRadius = knobRadius * GLOW_RADIUS_FRACTION
        val ringRect = RectF(cx - bodyRadius, cy - bodyRadius, cx + bodyRadius, cy + bodyRadius)

        // Body fill — no glow
        bodyPaint.color = bodyColor
        canvas.drawCircle(cx, cy, bodyRadius, bodyPaint)

        // Ring with luminance glow (centered shadow = radial bloom)
        ringPaint.setShadowLayer(glowRadius, 0f, 0f, currentStateColor)
        ringPaint.color = currentStateColor
        ringPaint.strokeWidth = strokeWidth
        canvas.drawOval(ringRect, ringPaint)

        // Indicator dot with luminance glow
        indicatorPaint.setShadowLayer(glowRadius, 0f, 0f, currentStateColor)
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

    /** Immediately snaps to the idle color without animation (useful when attaching to a new view). */
    fun resetToIdle() {
        colorAnimator?.cancel()
        currentStateColor = idleColor
        invalidateSelf()
    }

    /**
     * Update the reported intrinsic size in pixels. Call this once you know the
     * density-aware pixel size (e.g., from a dimension resource), then set the drawable on the view.
     */
    fun setIntrinsicSize(@Px sizePx: Int) {
        intrinsicSizePx = sizePx
        invalidateSelf()
    }

    private fun applyTheme(theme: Theme) {
        idleColor = theme.viewGroupTheme.dividerColor
        bodyColor = theme.viewGroupTheme.backgroundColor
        if (colorAnimator == null || colorAnimator?.isRunning == false) {
            currentStateColor = idleColor
        }
        invalidateSelf()
    }

    private fun applyAccent(accent: Accent) {
        accentColor = accent.primaryAccentColor
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

        /** How far the indicator dot sits from the center, as a fraction of body radius. */
        const val INDICATOR_DISTANCE_FRACTION = 0.81f

        const val DEFAULT_INTRINSIC_SIZE_PX = 500

        /**
         * Blur radius of the [Paint.setShadowLayer] glow, as a fraction of the knob radius.
         * A centered shadow (dx=0, dy=0) produces a pure radial luminance bloom around the
         * ring and indicator dot.
         */
        private const val GLOW_RADIUS_FRACTION = 0.15f
    }
}
