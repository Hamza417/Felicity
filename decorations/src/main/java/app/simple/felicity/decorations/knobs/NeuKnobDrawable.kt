package app.simple.felicity.decorations.knobs

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.annotation.Px

class NeuKnobDrawable(
        @ColorInt private var accentColor: Int = DEFAULT_ACCENT_COLOR,
        @ColorInt private var idleColor: Int = DEFAULT_IDLE_COLOR,
        @ColorInt private var bodyColor: Int = DEFAULT_BODY_COLOR,
        var strokeWidthFraction: Float = DEFAULT_STROKE_WIDTH_FRACTION,
        var indicatorRadiusFraction: Float = DEFAULT_INDICATOR_RADIUS_FRACTION,
        @Px private var intrinsicSizePx: Int = DEFAULT_INTRINSIC_SIZE_PX
) : RotaryKnobDrawable() {

    // ── Neumorphic Shadow Config ────────────────────────────────────────────────
    private val shadowBlurFraction = 0.15f
    private val shadowOffsetFraction = 0.01f
    private val lightShadowColor = 0xFFFFFFFF.toInt()
    private val darkShadowColor = 0x33A3B1C6.toInt()

    // ── Paints ──────────────────────────────────────────────────────────────────

    private val lightShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt() // Needs a base color to cast a shadow
    }

    private val darkShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }

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

    var onColorChanged: (() -> Unit)? = null
    private var colorAnimator: ValueAnimator? = null

    // Cached bounds for gradients and shadows
    private var cachedCx = 0f
    private var cachedCy = 0f
    private var cachedBodyRadius = 0f

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

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        if (bounds.isEmpty) return

        cachedCx = bounds.exactCenterX()
        cachedCy = bounds.exactCenterY()

        val maxRadius = minOf(bounds.width(), bounds.height()) / 2f

        // Shrink the knob body slightly so the shadows aren't clipped by the bounds
        val shadowPadding = maxRadius * (shadowBlurFraction + shadowOffsetFraction)
        cachedBodyRadius = maxRadius - shadowPadding

        updateNeumorphicEffects()
    }

    private fun updateNeumorphicEffects() {
        if (cachedBodyRadius <= 0) return

        val blurRadius = cachedBodyRadius * shadowBlurFraction
        val offset = cachedBodyRadius * shadowOffsetFraction

        // Apply opposing drop shadows
        lightShadowPaint.setShadowLayer(blurRadius, -offset, -offset, lightShadowColor)
        darkShadowPaint.setShadowLayer(blurRadius * 1.2f, offset, offset, darkShadowColor)

        // Add a faint convex gradient to the body (Top-Left Light -> Bottom-Right Slightly Darker)
        val edgeColor = blendColors(bodyColor, 0xFFD9E2EC.toInt(), 0.5f) // Slightly darker than body
        bodyPaint.shader = LinearGradient(
                cachedCx - cachedBodyRadius, cachedCy - cachedBodyRadius, // Start Top-Left
                cachedCx + cachedBodyRadius, cachedCy + cachedBodyRadius, // End Bottom-Right
                intArrayOf(0xFFFFFFFF.toInt(), bodyColor, edgeColor),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP
        )
    }

    // ── Drawable ─────────────────────────────────────────────────────────────────

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty || cachedBodyRadius <= 0) return

        // 1. Draw Shadows (underneath)
        canvas.drawCircle(cachedCx, cachedCy, cachedBodyRadius, lightShadowPaint)
        canvas.drawCircle(cachedCx, cachedCy, cachedBodyRadius, darkShadowPaint)

        // 2. Body fill (now with a gradient!)
        canvas.drawCircle(cachedCx, cachedCy, cachedBodyRadius, bodyPaint)

        // 3. Ring outline
        val strokeWidth = cachedBodyRadius * strokeWidthFraction
        ringPaint.color = currentStateColor
        ringPaint.strokeWidth = strokeWidth

        // Draw ring slightly inside the body edge
        val ringRadius = cachedBodyRadius - (strokeWidth / 2f) - (cachedBodyRadius * 0.05f)
        canvas.drawOval(
                RectF(cachedCx - ringRadius, cachedCy - ringRadius, cachedCx + ringRadius, cachedCy + ringRadius),
                ringPaint
        )

        // 4. Indicator dot — near the top
        val indicatorRadius = cachedBodyRadius * indicatorRadiusFraction
        val indicatorCy = cachedCy - cachedBodyRadius * INDICATOR_DISTANCE_FRACTION
        indicatorPaint.color = currentStateColor

        // Optional: Add a tiny glow to the indicator dot when active
        if (currentStateColor != idleColor) {
            indicatorPaint.setShadowLayer(indicatorRadius * 0.5f, 0f, 0f, currentStateColor)
        } else {
            indicatorPaint.clearShadowLayer()
        }

        canvas.drawCircle(cachedCx, indicatorCy, indicatorRadius, indicatorPaint)
    }

    // ── Helper ───────────────────────────────────────────────────────────────────

    // Simple color blending to calculate a slightly darker shade for the bottom edge
    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val r = (Color.red(color1) * ratio + Color.red(color2) * inverseRatio).toInt()
        val g = (Color.green(color1) * ratio + Color.green(color2) * inverseRatio).toInt()
        val b = (Color.blue(color1) * ratio + Color.blue(color2) * inverseRatio).toInt()
        return Color.rgb(r, g, b)
    }

    // ... (Keep your getIntrinsicWidth, setAlpha, setColorFilter, etc. exactly as they were) ...
    override fun getIntrinsicWidth(): Int = intrinsicSizePx
    override fun getIntrinsicHeight(): Int = intrinsicSizePx
    override fun setAlpha(alpha: Int) {
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    fun resetToIdle() { /* ... existing logic ... */
    }

    fun setIdleColor(@ColorInt color: Int) { /* ... existing logic ... */
    }

    fun setAccentColor(@ColorInt color: Int) { /* ... existing logic ... */
    }

    fun setBodyColor(@ColorInt color: Int) { /* ... existing logic ... */
    }

    fun setIntrinsicSize(@Px sizePx: Int) { /* ... existing logic ... */
    }

    companion object {
        @ColorInt
        val DEFAULT_ACCENT_COLOR: Int = 0xFF2D85E6.toInt()

        @ColorInt
        val DEFAULT_IDLE_COLOR: Int = 0x7A464646

        @ColorInt
        val DEFAULT_BODY_COLOR: Int = 0xFFF0F3F8.toInt() // Changed to off-white so white highlight shows

        const val DEFAULT_STROKE_WIDTH_FRACTION = 0.015f
        const val DEFAULT_INDICATOR_RADIUS_FRACTION = 0.074f
        private const val INDICATOR_DISTANCE_FRACTION = 0.81f
        const val DEFAULT_INTRINSIC_SIZE_PX = 500
    }
}