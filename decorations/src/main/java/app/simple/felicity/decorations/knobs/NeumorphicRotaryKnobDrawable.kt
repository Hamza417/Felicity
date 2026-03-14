package app.simple.felicity.decorations.knobs

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
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
 * A neumorphic [RotaryKnobDrawable] that renders the knob circle with a soft-UI /
 * neumorphic visual style. Every layer carries faint directional gradients that simulate
 * a raised surface lit from the top-left corner.
 *
 * Layers drawn bottom-to-top:
 * 1. **Light highlight** — a [RadialGradient] whose center is displaced toward the top-left,
 *    creating a soft specular reflection on the raised surface.
 * 2. **Body fill** — a circle filled with a [LinearGradient] that runs from a slightly
 *    lighter tint (top-left) through the base color to a slightly darker tint (bottom-right),
 *    reinforcing the convex curvature illusion.
 * 3. **Ring** — a thin circular stroke whose color animates between idle and accent on
 *    press / release. When [AppearancePreferences.isShadowEffectOn] is true a centered
 *    [Paint.setShadowLayer] (dx=0, dy=0) with an animated blur radius produces a luminance
 *    bloom that grows and shrinks like a capacitor charging / discharging.
 * 4. **Indicator dot** — a small filled circle near the top of the knob drawn with a
 *    subtle inner [RadialGradient] glow. When the shadow effect preference is enabled,
 *    a [Paint.setShadowLayer] outer bloom is applied with an animated radius.
 *
 * Both [Paint.setShadowLayer] usages require [android.view.View.LAYER_TYPE_SOFTWARE] on
 * the host view; [requiresSoftwareLayer] returns `true` only when the shadow effect
 * preference is on, allowing hardware acceleration when the effect is disabled.
 *
 * Theme colors are managed internally: the drawable registers with [ThemeManager] during
 * [onAttachedToKnobView] and unregisters during [onDetachedFromKnobView], so
 * [RotaryKnobView] never needs to forward theme events.
 *
 * @param strokeWidthFraction      Ring stroke width as a fraction of the knob radius (0..1).
 * @param indicatorRadiusFraction  Radius of the indicator dot as a fraction of the knob radius.
 * @param highlightAlpha           Alpha (0..255) applied to the light highlight layer.
 * @param intrinsicSizePx          Reported intrinsic size in pixels so wrap_content works.
 *
 * @author Hamza417
 */
class NeumorphicRotaryKnobDrawable(
        var strokeWidthFraction: Float = DEFAULT_STROKE_WIDTH_FRACTION,
        var indicatorRadiusFraction: Float = DEFAULT_INDICATOR_RADIUS_FRACTION,
        var highlightAlpha: Int = DEFAULT_HIGHLIGHT_ALPHA,
        @Px private var intrinsicSizePx: Int = DEFAULT_INTRINSIC_SIZE_PX
) : RotaryKnobDrawable(), ThemeChangedListener {

    // ── Theme colors ──────────────────────────────────────────────────────────────

    @ColorInt
    private var accentColor: Int = SimpleRotaryKnobDrawable.DEFAULT_ACCENT_COLOR

    @ColorInt
    private var idleColor: Int = SimpleRotaryKnobDrawable.DEFAULT_IDLE_COLOR

    @ColorInt
    private var bodyColor: Int = SimpleRotaryKnobDrawable.DEFAULT_BODY_COLOR

    @ColorInt
    private var highlightColor: Int = DEFAULT_HIGHLIGHT_COLOR

    // ── Animation state ──────────────────────────────────────────────────────────

    /**
     * Normalized press / release state in [0..1].
     *  - 0 = fully idle: idle color, zero glow radius.
     *  - 1 = fully pressed: accent color, maximum glow radius.
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

    // ── State colors ──────────────────────────────────────────────────────────────

    /** Current ring / arc color interpolated from idle → accent by [stateProgress]. */
    @ColorInt
    private var currentStateColor: Int = idleColor

    /**
     * Current indicator dot color interpolated from idle → accent by the maximum of
     * [stateProgress] and [indicatorGlowProgress].
     */
    @ColorInt
    private var currentIndicatorColor: Int = idleColor

    // ── Paints ──────────────────────────────────────────────────────────────────

    /**
     * Paint used for the light-highlight displaced radial gradient. Shader is rebuilt in
     * [rebuildShaders] whenever the bounds change or colors update.
     */
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /**
     * Paint used for the body fill. Uses a [LinearGradient] that runs from the
     * top-left (slightly lighter) to the bottom-right (slightly darker).
     */
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /**
     * Ring stroke. When [AppearancePreferences.isShadowEffectOn] is true, a luminance
     * glow bloom is produced via [Paint.setShadowLayer] with an animated blur radius —
     * requires software layer on the host view.
     */
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /**
     * Indicator dot. Uses a [RadialGradient] inner glow and, when the shadow effect
     * preference is on, a [Paint.setShadowLayer] outer bloom with an animated radius.
     */
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // ── RotaryKnobDrawable ───────────────────────────────────────────────────────

    override fun getCurrentStateColor(): Int = currentStateColor

    /**
     * Returns `true` when [AppearancePreferences.isShadowEffectOn] is enabled — the
     * [Paint.setShadowLayer] glow requires software rendering on the host view.
     * Returns `false` when the preference is off, restoring hardware acceleration.
     */
    override fun requiresSoftwareLayer(): Boolean = AppearancePreferences.isShadowEffectOn()

    override fun onPressedStateChanged(pressed: Boolean, animationDuration: Int) {
        val targetProgress = if (pressed) 1f else 0f
        stateAnimator?.cancel()
        stateAnimator = ValueAnimator.ofFloat(stateProgress, targetProgress).apply {
            duration = animationDuration.toLong()
            // Press: AccelerateDecelerateInterpolator for capacitor-charging feel.
            // Release: DecelerateInterpolator for capacitor-discharging feel.
            interpolator = if (pressed) AccelerateDecelerateInterpolator() else DecelerateInterpolator()
            addUpdateListener { anim ->
                stateProgress = anim.animatedValue as Float
                currentStateColor = lerpColor(idleColor, accentColor, stateProgress)
                val indicatorT = maxOf(stateProgress, indicatorGlowProgress)
                val newIndicatorColor = lerpColor(idleColor, accentColor, indicatorT)
                if (newIndicatorColor != currentIndicatorColor) {
                    currentIndicatorColor = newIndicatorColor
                    rebuildIndicatorShader()
                }
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
                val indicatorT = maxOf(stateProgress, indicatorGlowProgress)
                val newIndicatorColor = lerpColor(idleColor, accentColor, indicatorT)
                if (newIndicatorColor != currentIndicatorColor) {
                    currentIndicatorColor = newIndicatorColor
                    rebuildIndicatorShader()
                }
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

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        rebuildShaders()
    }

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
        val indicatorCy = cy - bodyRadius * SimpleRotaryKnobDrawable.INDICATOR_DISTANCE_FRACTION
        val ringRect = RectF(cx - bodyRadius, cy - bodyRadius, cx + bodyRadius, cy + bodyRadius)
        val glowEnabled = AppearancePreferences.isShadowEffectOn()

        // The highlight gradient circle is drawn slightly larger than the body so its soft
        // edges bleed outward and are not clipped by the solid body fill.
        val highlightRadius = bodyRadius + knobRadius * GLOW_RADIUS_EXTRA_FRACTION

        // Layer 1: light highlight (displaced top-left).
        canvas.drawCircle(cx, cy, highlightRadius, highlightPaint)

        // Layer 2: body fill with subtle top-left → bottom-right gradient.
        canvas.drawCircle(cx, cy, bodyRadius, bodyPaint)

        // Layer 3: ring with animated glow — bloom radius grows / shrinks like a capacitor.
        val ringMaxGlow = strokeWidth * GLOW_RADIUS_FRACTION
        val ringGlowRadius = if (glowEnabled) ringMaxGlow * stateProgress else 0f
        if (ringGlowRadius > 0f) {
            ringPaint.setShadowLayer(ringGlowRadius, 0f, 0f, currentStateColor)
        } else {
            ringPaint.clearShadowLayer()
        }
        ringPaint.color = currentStateColor
        ringPaint.strokeWidth = strokeWidth
        canvas.drawOval(ringRect, ringPaint)

        // Layer 4: indicator dot — inner gradient glow (shader) + animated outer bloom.
        val indicatorT = maxOf(stateProgress, indicatorGlowProgress)
        val indicatorMaxGlow = indicatorRadius * GLOW_RADIUS_FRACTION
        val indicatorGlowRadius = if (glowEnabled) indicatorMaxGlow * indicatorT else 0f
        if (indicatorGlowRadius > 0f) {
            indicatorPaint.setShadowLayer(indicatorGlowRadius, 0f, 0f, currentIndicatorColor)
        } else {
            indicatorPaint.clearShadowLayer()
        }
        canvas.drawCircle(cx, indicatorCy, indicatorRadius, indicatorPaint)
    }

    override fun getIntrinsicWidth(): Int = intrinsicSizePx
    override fun getIntrinsicHeight(): Int = intrinsicSizePx

    override fun setAlpha(alpha: Int) {
        highlightPaint.alpha = alpha
        bodyPaint.alpha = alpha
        ringPaint.alpha = alpha
        indicatorPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        highlightPaint.colorFilter = colorFilter
        bodyPaint.colorFilter = colorFilter
        ringPaint.colorFilter = colorFilter
        indicatorPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    // ── Shader helpers ────────────────────────────────────────────────────────────

    /**
     * Rebuilds all [Shader] instances that depend on bounds or color values.
     * Must be called whenever [onBoundsChange] fires or after any color update so the
     * gradient geometry stays in sync with the current draw bounds.
     */
    private fun rebuildShaders() {
        val b = bounds
        if (b.isEmpty) return

        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val knobRadius = minOf(b.width(), b.height()) / 2f
        val strokeWidth = knobRadius * strokeWidthFraction
        val halfStroke = strokeWidth / 2f
        val bodyRadius = knobRadius - halfStroke
        val glowRadius = bodyRadius + knobRadius * GLOW_RADIUS_EXTRA_FRACTION
        val offset = knobRadius * HIGHLIGHT_OFFSET_FRACTION

        // Light highlight: displaced gradient — dense at top-left edge, transparent at center.
        val highlightRaw = colorWithAlpha(highlightColor, highlightAlpha)
        highlightPaint.shader = RadialGradient(
                cx - offset, cy - offset,
                glowRadius,
                intArrayOf(Color.TRANSPARENT, highlightRaw),
                floatArrayOf(0.55f, 1f),
                Shader.TileMode.CLAMP
        )

        // Body: subtle linear gradient from lighter (top-left) through base to darker (bottom-right).
        val bodyLight = blendColors(bodyColor, Color.WHITE, BODY_GRADIENT_TINT)
        val bodyDark = blendColors(bodyColor, Color.BLACK, BODY_GRADIENT_TINT)
        bodyPaint.shader = LinearGradient(
                b.left.toFloat(), b.top.toFloat(),
                b.right.toFloat(), b.bottom.toFloat(),
                intArrayOf(bodyLight, bodyColor, bodyDark),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
        )

        rebuildIndicatorShader()
    }

    /**
     * Rebuilds only the indicator dot shader so it can be refreshed independently on every
     * state-color animation frame without reconstructing all the heavier gradient shaders.
     * Uses [currentIndicatorColor] which may differ from [currentStateColor] when a
     * programmatic glow pulse is running.
     */
    private fun rebuildIndicatorShader() {
        val b = bounds
        if (b.isEmpty) return

        val cy = b.exactCenterY()
        val knobRadius = minOf(b.width(), b.height()) / 2f
        val strokeWidth = knobRadius * strokeWidthFraction
        val halfStroke = strokeWidth / 2f
        val bodyRadius = knobRadius - halfStroke
        val indicatorRadius = knobRadius * indicatorRadiusFraction
        val indicatorCy = cy - bodyRadius * SimpleRotaryKnobDrawable.INDICATOR_DISTANCE_FRACTION
        val cx = b.exactCenterX()

        // Indicator inner glow: radial gradient from a brighter center to the indicator color at the edge.
        val innerGlow = blendColors(currentIndicatorColor, Color.WHITE, INDICATOR_GLOW_BLEND)
        indicatorPaint.shader = RadialGradient(
                cx, indicatorCy,
                indicatorRadius,
                intArrayOf(innerGlow, currentIndicatorColor),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
        )
    }

    // ── Color utilities ───────────────────────────────────────────────────────────

    /**
     * Returns [color] with its alpha channel replaced by [alpha] (0..255), preserving
     * the original RGB components regardless of any alpha baked into the source color.
     */
    private fun colorWithAlpha(@ColorInt color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    /**
     * Linearly blends [colorA] toward [colorB] by [fraction] (0 = pure A, 1 = pure B)
     * in the RGB channels. Alpha from [colorA] is preserved.
     */
    private fun blendColors(@ColorInt colorA: Int, @ColorInt colorB: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val r = (Color.red(colorA) + (Color.red(colorB) - Color.red(colorA)) * f).toInt()
        val g = (Color.green(colorA) + (Color.green(colorB) - Color.green(colorA)) * f).toInt()
        val b = (Color.blue(colorA) + (Color.blue(colorB) - Color.blue(colorA)) * f).toInt()
        return Color.argb(Color.alpha(colorA), r, g, b)
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

    // ── Theme application ─────────────────────────────────────────────────────────

    private fun applyTheme(theme: Theme) {
        bodyColor = theme.viewGroupTheme.backgroundColor
        idleColor = theme.viewGroupTheme.dividerColor
        highlightColor = theme.viewGroupTheme.highlightColor
        if (stateAnimator?.isRunning != true) {
            stateProgress = 0f
            currentStateColor = idleColor
            currentIndicatorColor = idleColor
        }
        rebuildShaders()
        invalidateSelf()
    }

    private fun applyAccent(accent: Accent) {
        accentColor = accent.primaryAccentColor
        // Refresh colors to reflect new accent at the current animation fractions.
        currentStateColor = lerpColor(idleColor, accentColor, stateProgress)
        val indicatorT = maxOf(stateProgress, indicatorGlowProgress)
        currentIndicatorColor = lerpColor(idleColor, accentColor, indicatorT)
        rebuildIndicatorShader()
        invalidateSelf()
    }

    companion object {
        const val DEFAULT_STROKE_WIDTH_FRACTION = 0.015f
        const val DEFAULT_INDICATOR_RADIUS_FRACTION = 0.074f

        /** Alpha (0..255) applied to the light highlight overlay layer. */
        const val DEFAULT_HIGHLIGHT_ALPHA = 100

        const val DEFAULT_INTRINSIC_SIZE_PX = 500

        @ColorInt
        private val DEFAULT_HIGHLIGHT_COLOR: Int = 0xFFFFFFFF.toInt()

        /** Extra radius beyond bodyRadius for the highlight gradient circle, as a fraction of knobRadius. */
        private const val GLOW_RADIUS_EXTRA_FRACTION = 0.18f

        /** How far to displace the highlight gradient center toward the top-left, as a fraction of knobRadius. */
        private const val HIGHLIGHT_OFFSET_FRACTION = 0.20f

        /** How much white / black to mix into the body color at the gradient ends (0..1). */
        private const val BODY_GRADIENT_TINT = 0.12f

        /** How much white to blend into the indicator color for the inner gradient glow. */
        private const val INDICATOR_GLOW_BLEND = 0.45f

        /**
         * Multiplier applied to the ring stroke width (and indicator radius) to derive the
         * maximum [Paint.setShadowLayer] blur radius. The actual value applied is this
         * constant multiplied by the current state progress (0..1), so the luminance bloom
         * grows from nothing to full intensity — like a capacitor charging up to its rated voltage.
         */
        private const val GLOW_RADIUS_FRACTION = 3.5f
    }
}
