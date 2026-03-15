package app.simple.felicity.decorations.knobs

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import androidx.annotation.Px
import app.simple.felicity.preferences.AppearancePreferences
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
 * The glow effect (when [AppearancePreferences.isShadowEffectOn] is true) animates both
 * the blur radius and the color together, giving a capacitor-charging / discharging feel:
 * the luminance bloom grows from zero on press and decays back on release rather than
 * only cross-fading alpha.
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

    /**
     * Ring outline. When [AppearancePreferences.isShadowEffectOn] is true, a luminance
     * glow bloom is produced via [Paint.setShadowLayer] with an animated blur radius.
     * Requires software layer on the host view.
     */
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /**
     * Indicator dot. When [AppearancePreferences.isShadowEffectOn] is true, a luminance
     * glow bloom is produced via [Paint.setShadowLayer] with an animated blur radius.
     * Requires software layer on the host view.
     */
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // ── Animation state ──────────────────────────────────────────────────────────

    /**
     * Normalized press / release state in [0..1].
     *
     *  - 0 = fully idle: idle color, zero glow radius.
     *  - 1 = fully pressed: accent color, maximum glow radius.
     *
     * Drives [currentStateColor] (ring + arc sync) and the ring glow radius.
     */
    private var stateProgress: Float = 0f
    private var stateAnimator: ValueAnimator? = null

    /**
     * Normalized indicator-only glow pulse for programmatic position changes.
     * Animates 0 → 1 → 0 independently of [stateProgress] so the ring is unaffected.
     */
    private var indicatorGlowProgress: Float = 0f
    private var indicatorGlowAnimator: ValueAnimator? = null

    // ── State color ───────────────────────────────────────────────────────────────

    @ColorInt
    @get:JvmName("getAnimatedStateColor")
    var currentStateColor: Int = idleColor
        private set

    // ── RotaryKnobDrawable ───────────────────────────────────────────────────────

    override fun getCurrentStateColor(): Int = currentStateColor

    /**
     * Returns `true` when [AppearancePreferences.isShadowEffectOn] is enabled, because
     * [Paint.setShadowLayer] requires software rendering on the host view.
     * Returns `false` when the shadow effect preference is off, allowing hardware acceleration.
     */
    override fun requiresSoftwareLayer(): Boolean = try {
        AppearancePreferences.isShadowEffectOn()
    } catch (e: Exception) {
        // In case the preference is unavailable for some reason, default to software layer to be safe.
        true
    }

    override fun onPressedStateChanged(pressed: Boolean, animationDuration: Int) {
        val targetProgress = if (pressed) 1f else 0f
        stateAnimator?.cancel()
        stateAnimator = ValueAnimator.ofFloat(stateProgress, targetProgress).apply {
            duration = animationDuration.toLong()
            // Press: AccelerateDecelerateInterpolator gives a capacitor-charging feel —
            // the glow builds quickly then settles at peak.
            // Release: DecelerateInterpolator gives a capacitor-discharging feel —
            // the glow drains quickly at first then fades to nothing.
            interpolator = if (pressed) AccelerateDecelerateInterpolator() else DecelerateInterpolator()
            addUpdateListener { anim ->
                stateProgress = anim.animatedValue as Float
                currentStateColor = lerpColor(idleColor, accentColor, stateProgress)
                invalidateSelf()
            }
            start()
        }
    }

    override fun onProgrammaticPositionChanged() {
        // Pulse only the indicator: 0 → peak → 0.  Ring and arc remain at idle.
        indicatorGlowAnimator?.cancel()
        indicatorGlowProgress = 0f
        indicatorGlowAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = PROGRAMMATIC_GLOW_DURATION
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                indicatorGlowProgress = anim.animatedValue as Float
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
        stateAnimator?.cancel()
        indicatorGlowAnimator?.cancel()
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
        val maxGlowRadius = knobRadius * GLOW_RADIUS_FRACTION
        val ringRect = RectF(cx - bodyRadius, cy - bodyRadius, cx + bodyRadius, cy + bodyRadius)
        val glowEnabled = AppearancePreferences.isShadowEffectOn()

        // Body fill — no glow.
        bodyPaint.color = bodyColor
        canvas.drawCircle(cx, cy, bodyRadius, bodyPaint)

        // Ring: glow radius scales with stateProgress — grows like a capacitor charging,
        // shrinks like a capacitor discharging on release.
        val ringGlowRadius = if (glowEnabled) maxGlowRadius * stateProgress else 0f
        if (ringGlowRadius > 0f) {
            ringPaint.setShadowLayer(ringGlowRadius, 0f, 0f, currentStateColor)
        } else {
            ringPaint.clearShadowLayer()
        }
        ringPaint.color = currentStateColor
        ringPaint.strokeWidth = strokeWidth
        canvas.drawOval(ringRect, ringPaint)

        // Indicator: independent glow that also responds to programmatic position pulses.
        // Taking max() means a programmatic pulse is visible only when the knob is not pressed.
        val indicatorT = maxOf(stateProgress, indicatorGlowProgress)
        val indicatorColor = lerpColor(idleColor, accentColor, indicatorT)
        val indicatorGlowRadius = if (glowEnabled) maxGlowRadius * indicatorT else 0f
        if (indicatorGlowRadius > 0f) {
            indicatorPaint.setShadowLayer(indicatorGlowRadius, 0f, 0f, indicatorColor)
        } else {
            indicatorPaint.clearShadowLayer()
        }
        indicatorPaint.color = indicatorColor
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

    /** Immediately snaps to the idle state without animation (useful when attaching to a new view). */
    fun resetToIdle() {
        stateAnimator?.cancel()
        indicatorGlowAnimator?.cancel()
        stateProgress = 0f
        indicatorGlowProgress = 0f
        currentStateColor = idleColor
        invalidateSelf()
    }

    /**
     * Updates the reported intrinsic size in pixels. Call this once you know the
     * density-aware pixel size (e.g., from a dimension resource), then set the drawable on the view.
     */
    fun setIntrinsicSize(@Px sizePx: Int) {
        intrinsicSizePx = sizePx
        invalidateSelf()
    }

    /**
     * Linearly interpolates between [colorFrom] and [colorTo] by [t] (0 = pure from, 1 = pure to),
     * blending all four ARGB channels independently.
     */
    private fun lerpColor(@ColorInt colorFrom: Int, @ColorInt colorTo: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return Color.argb(
                (Color.alpha(colorFrom) + (Color.alpha(colorTo) - Color.alpha(colorFrom)) * f).toInt(),
                (Color.red(colorFrom) + (Color.red(colorTo) - Color.red(colorFrom)) * f).toInt(),
                (Color.green(colorFrom) + (Color.green(colorTo) - Color.green(colorFrom)) * f).toInt(),
                (Color.blue(colorFrom) + (Color.blue(colorTo) - Color.blue(colorFrom)) * f).toInt()
        )
    }

    private fun applyTheme(theme: Theme) {
        idleColor = theme.viewGroupTheme.dividerColor
        bodyColor = theme.viewGroupTheme.backgroundColor
        if (stateAnimator?.isRunning != true) {
            stateProgress = 0f
            currentStateColor = idleColor
        }
        invalidateSelf()
    }

    private fun applyAccent(accent: Accent) {
        accentColor = accent.primaryAccentColor
        // Refresh currentStateColor so the arc / tick marks stay in sync with the new accent.
        currentStateColor = lerpColor(idleColor, accentColor, stateProgress)
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
         * Maximum blur radius of [Paint.setShadowLayer] as a fraction of the knob radius.
         * The actual value applied equals this fraction multiplied by the current [stateProgress]
         * or indicator glow progress (both in [0..1]), so the luminance bloom grows from
         * nothing to full intensity — like a capacitor charging up to its rated voltage.
         */
        private const val GLOW_RADIUS_FRACTION = 0.05f
    }
}
