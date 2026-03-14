package app.simple.felicity.decorations.knobs

import android.animation.ArgbEvaluator
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
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.annotation.Px
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
 *    press / release. A centered [Paint.setShadowLayer] (dx=0, dy=0) produces a pure radial
 *    luminance bloom around the stroke that also animates with state.
 * 4. **Indicator dot** — a small filled circle near the top of the knob drawn with a
 *    subtle inner [RadialGradient] glow and a [Paint.setShadowLayer] outer bloom,
 *    both animating between idle and accent.
 *
 * Both [Paint.setShadowLayer] usages require [LAYER_TYPE_SOFTWARE] on the host view;
 * [requiresSoftwareLayer] returns `true` to signal this to [RotaryKnobView].
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

    // ── State ────────────────────────────────────────────────────────────────────

    @ColorInt
    private var currentStateColor: Int = idleColor

    private var colorAnimator: ValueAnimator? = null

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

    /** Ring stroke. Luminance glow is achieved via [Paint.setShadowLayer] — requires software layer. */
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Indicator dot. Luminance glow is achieved via [Paint.setShadowLayer] — requires software layer. */
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // ── RotaryKnobDrawable ───────────────────────────────────────────────────────

    override fun getCurrentStateColor(): Int = currentStateColor

    override fun onPressedStateChanged(pressed: Boolean, animationDuration: Int) {
        val targetColor = if (pressed) accentColor else idleColor
        colorAnimator?.cancel()
        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), currentStateColor, targetColor).apply {
            duration = animationDuration.toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                currentStateColor = animator.animatedValue as Int
                rebuildIndicatorShader()
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

        // The highlight gradient circle is drawn slightly larger than the body so its soft
        // edges bleed outward and are not clipped by the solid body fill.
        val highlightRadius = bodyRadius + knobRadius * GLOW_RADIUS_EXTRA_FRACTION

        // Layer 1: light highlight (displaced top-left)
        canvas.drawCircle(cx, cy, highlightRadius, highlightPaint)

        // Layer 2: body fill with subtle top-left → bottom-right gradient
        canvas.drawCircle(cx, cy, bodyRadius, bodyPaint)

        // Layer 3: ring — setShadowLayer(dx=0, dy=0) produces a pure radial luminance bloom
        ringPaint.setShadowLayer(strokeWidth * GLOW_RADIUS_FRACTION, 0f, 0f, currentStateColor)
        ringPaint.color = currentStateColor
        ringPaint.strokeWidth = strokeWidth
        canvas.drawOval(ringRect, ringPaint)

        // Layer 4: indicator dot — inner gradient glow (shader) + outer setShadowLayer bloom
        indicatorPaint.setShadowLayer(indicatorRadius * GLOW_RADIUS_FRACTION, 0f, 0f, currentStateColor)
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

        // Indicator inner glow: radial gradient from a brighter center to the state color at the edge.
        val innerGlow = blendColors(currentStateColor, Color.WHITE, INDICATOR_GLOW_BLEND)
        indicatorPaint.shader = RadialGradient(
                cx, indicatorCy,
                indicatorRadius,
                intArrayOf(innerGlow, currentStateColor),
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

    // ── Theme application ─────────────────────────────────────────────────────────

    private fun applyTheme(theme: Theme) {
        bodyColor = theme.viewGroupTheme.backgroundColor
        idleColor = theme.viewGroupTheme.dividerColor
        highlightColor = theme.viewGroupTheme.highlightColor
        if (colorAnimator == null || colorAnimator?.isRunning == false) {
            currentStateColor = idleColor
        }
        rebuildShaders()
        invalidateSelf()
    }

    private fun applyAccent(accent: Accent) {
        accentColor = accent.primaryAccentColor
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

        /** How much white to blend into the state color for the indicator dot inner gradient glow. */
        private const val INDICATOR_GLOW_BLEND = 0.45f

        /**
         * Multiplier applied to the ring stroke width (and indicator radius) to derive the
         * [Paint.setShadowLayer] blur radius. A centered shadow (dx=0, dy=0) produces a pure
         * radial luminance bloom — the larger this value the wider and softer the glow.
         */
        private const val GLOW_RADIUS_FRACTION = 3.5f
    }
}
