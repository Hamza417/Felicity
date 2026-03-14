package app.simple.felicity.decorations.knobs.simple

import android.animation.ValueAnimator
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.annotation.Px
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A neumorphic glass-ball rotary knob drawable.
 *
 * Visual layers (drawn back to front):
 *  1. **Raised plate** — two offset, blurred circles (dark bottom-right + light top-left)
 *     leave a neumorphic halo around the opaque plate fill, simulating a raised surface.
 *  2. **Sphere body** — a filled circle painted with a [RadialGradient] whose origin is
 *     shifted toward the top-left to create a single-light-source 3-D depth illusion.
 *     The gradient sweeps from [accentColorFrom] (purple) at the bright pole to
 *     [accentColorTo] (pink) at the dark equator.
 *  3. **Crescent arc indicator** — a ∩-shaped path built by subtracting a downward-shifted
 *     inner circle from a larger outer circle ([Path.Op.DIFFERENCE]).  Filled with a
 *     horizontal [LinearGradient] matching the sphere colors so it reads as an engraved
 *     groove in the glass surface.  Rotates with the knob to show the current position.
 *  4. **Specular highlight** — a soft, tilted white-to-grey oval fixed at the 10-o'clock
 *     position (counter-rotated so the light source never moves).  Three gradient stops —
 *     solid white → near-white → semi-transparent grey — produce a convincing frosted-glass
 *     reflection.
 *  5. **Press-glow ring** — a blurred accent halo that fades in around the plate edge when
 *     the knob is touched.  Intensity is scaled by [glowIntensityMultiplier].
 *
 * Layers 1 and 4 are **world-space static**: counter-rotated inside [draw] with
 * [knobRotationDegrees] so the neumorphic shadows and the light reflection never spin.
 * Layers 2 and 3 ride the canvas rotation applied by [RotaryKnobView].
 *
 * The host view must have [android.view.View.LAYER_TYPE_SOFTWARE] set (already done by
 * [RotaryKnobView]) because [BlurMaskFilter] is required for the neumorphic shadows and
 * the press-glow ring.
 *
 * @author Hamza417
 *
 * @param accentColorFrom   Sphere + crescent gradient start (defaults to purple #A855F7).
 * @param accentColorTo     Sphere + crescent gradient end   (defaults to pink  #EC4899).
 * @param baseColor         Opaque fill for the neumorphic plate.
 * @param intrinsicSizePx   Reported intrinsic size so wrap_content works.
 */
class NeoKnobDrawable @JvmOverloads constructor(
        @ColorInt var accentColorFrom: Int = NEO_PURPLE,
        @ColorInt var accentColorTo: Int = NEO_PINK,
        @ColorInt var baseColor: Int = DEFAULT_BASE_COLOR,
        @Px private var intrinsicSizePx: Int = DEFAULT_INTRINSIC_SIZE_PX
) : RotaryKnobDrawable() {

    // ── Rotation feed (set by RotaryKnobView before every draw call) ─────────────

    /**
     * Canvas-space rotation (degrees) currently applied by [RotaryKnobView].
     * Used to counter-rotate the neumorphic plate and specular highlight so they remain
     * fixed in world space while the sphere and crescent spin with the knob.
     */
    var knobRotationDegrees: Float = 0f

    // ── Neumorphic shadow configuration ──────────────────────────────────────────

    /** Semi-transparent white for the top-left neumorphic highlight shadow. */
    var lightShadowColor: Int = DEFAULT_LIGHT_SHADOW

    /** Semi-transparent black for the bottom-right neumorphic depth shadow. */
    var darkShadowColor: Int = DEFAULT_DARK_SHADOW

    /** Shadow offset magnitude as a fraction of the drawable radius. */
    var shadowOffsetFraction: Float = 0.04f

    /** Shadow blur spread radius as a fraction of the drawable radius. */
    var shadowRadiusFraction: Float = 0.08f

    /**
     * Master multiplier for the press-glow ring.
     * 0 disables glow; values above 1 amplify beyond the default peak.
     */
    var glowIntensityMultiplier: Float = 1f

    // ── Paints ────────────────────────────────────────────────────────────────────

    private val darkShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val lightShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val spherePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** Filled paint for the crescent arc indicator — shader is applied each frame. */
    private val crescentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glowRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    // ── Shader + path cache ───────────────────────────────────────────────────────

    /**
     * Last knob radius for which shaders and paths were built.
     * Reset to [Float.NaN] whenever bounds or [pressScale] change.
     */
    private var cachedKnobRadius = Float.NaN

    private var sphereShader: Shader? = null
    private var specularShader: Shader? = null
    private var crescentShader: Shader? = null

    /**
     * Final crescent [Path] — the result of [outerCrescentPath] minus [innerCrescentPath].
     * Built in bounds-center absolute coordinates and drawn directly on the rotating canvas
     * so the arch rotates with the knob to indicate the current position.
     */
    private val crescentPath = Path()
    private val outerCrescentPath = Path()
    private val innerCrescentPath = Path()

    // ── Animation state ───────────────────────────────────────────────────────────

    /** Scale factor driven toward 0.94 on press and back to 1.0 on release. */
    private var pressScale: Float = 1f
    private var glowIntensity: Float = 0f
    private var pressAnimator: ValueAnimator? = null
    private var glowAnimator: ValueAnimator? = null

    // ── RotaryKnobDrawable ────────────────────────────────────────────────────────

    override fun onPressedStateChanged(pressed: Boolean, animationDuration: Int) {
        val targetScale = if (pressed) 0.94f else 1f
        val targetGlow = if (pressed) 1f else 0f

        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(pressScale, targetScale).apply {
            duration = animationDuration.toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                pressScale = it.animatedValue as Float
                cachedKnobRadius = Float.NaN
                invalidateSelf()
            }
            start()
        }

        glowAnimator?.cancel()
        glowAnimator = ValueAnimator.ofFloat(glowIntensity, targetGlow).apply {
            duration = animationDuration.toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                glowIntensity = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    // ── Drawable ──────────────────────────────────────────────────────────────────

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        cachedKnobRadius = Float.NaN
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return

        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val R = min(b.width(), b.height()) / 2f

        val plateRadius = R * PLATE_RADIUS_FRAC
        val knobRadius = R * KNOB_RADIUS_FRAC * pressScale
        val shadowOffset = R * shadowOffsetFraction
        val shadowBlur = R * shadowRadiusFraction

        ensureShadersAndPaths(cx, cy, knobRadius)

        // ── Neumorphic plate (world-space static — counter-rotate to fix light direction) ─
        canvas.save()
        canvas.rotate(-knobRotationDegrees, cx, cy)

        // Dark shadow: bottom-right — simulates surface depth.
        darkShadowPaint.color = darkShadowColor
        darkShadowPaint.maskFilter = BlurMaskFilter(shadowBlur, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(cx + shadowOffset, cy + shadowOffset, plateRadius, darkShadowPaint)
        darkShadowPaint.maskFilter = null

        // Light shadow: top-left — simulates elevated surface highlight.
        lightShadowPaint.color = lightShadowColor
        lightShadowPaint.maskFilter = BlurMaskFilter(shadowBlur, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(cx - shadowOffset, cy - shadowOffset, plateRadius, lightShadowPaint)
        lightShadowPaint.maskFilter = null

        // Base plate fill — covers both shadow centers, leaving their blurred rims as the halo.
        platePaint.color = baseColor
        canvas.drawCircle(cx, cy, plateRadius, platePaint)
        canvas.restore()

        // ── Sphere body (rotates with canvas — rolling glass ball effect) ─────────
        spherePaint.shader = sphereShader
        canvas.drawCircle(cx, cy, knobRadius, spherePaint)

        // ── Crescent arc indicator (rotates with canvas — shows knob position) ────
        // crescentPath is built in absolute canvas coords relative to the bounds center,
        // so it naturally orbits around (cx, cy) as RotaryKnobView rotates the canvas.
        crescentPaint.shader = crescentShader
        canvas.drawPath(crescentPath, crescentPaint)

        // ── Specular highlight (world-space static — fixed overhead light source) ─
        // Drawn on top of both sphere and crescent so it always looks like a surface glint.
        canvas.save()
        canvas.rotate(-knobRotationDegrees, cx, cy)

        val specAngle = Math.toRadians(SPECULAR_ANGLE_DEG)
        val specDist = knobRadius * 0.46f
        val specCx = cx + (cos(specAngle) * specDist).toFloat()
        val specCy = cy + (sin(specAngle) * specDist).toFloat()
        val specW = knobRadius * 0.40f
        val specH = knobRadius * 0.26f
        canvas.rotate(SPECULAR_TILT_DEG, specCx, specCy)
        specularPaint.shader = specularShader
        canvas.drawOval(RectF(specCx - specW, specCy - specH, specCx + specW, specCy + specH), specularPaint)

        canvas.restore()

        // ── Press-glow ring (ring shape — symmetric, no counter-rotation needed) ──
        val effectiveGlow = (glowIntensity * glowIntensityMultiplier).coerceIn(0f, 1f)
        if (effectiveGlow > 0f) {
            val glowR = plateRadius * 0.12f * effectiveGlow
            val glowAlpha = (200 * effectiveGlow).toInt().coerceIn(0, 255)
            glowRingPaint.color = (accentColorFrom and 0x00FFFFFF) or (glowAlpha shl 24)
            glowRingPaint.strokeWidth = glowR
            glowRingPaint.maskFilter = BlurMaskFilter(glowR.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(cx, cy, plateRadius + glowR * 0.5f, glowRingPaint)
            glowRingPaint.maskFilter = null
        }
    }

    override fun getIntrinsicWidth(): Int = intrinsicSizePx
    override fun getIntrinsicHeight(): Int = intrinsicSizePx

    override fun setAlpha(alpha: Int) {
        spherePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        spherePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    // ── Cache helpers ─────────────────────────────────────────────────────────────

    /**
     * Rebuilds [sphereShader], [specularShader], [crescentShader], and [crescentPath]
     * whenever [knobRadius] differs from [cachedKnobRadius].
     *
     * All coordinates are in bounds-center space (cx, cy = b.exactCenterX/Y) so they
     * remain valid for both the rotated sphere draw and the counter-rotated specular draw.
     */
    private fun ensureShadersAndPaths(cx: Float, cy: Float, knobRadius: Float) {
        if (knobRadius == cachedKnobRadius) return

        // Sphere: RadialGradient offset toward top-left for a single-point-light 3-D illusion.
        val gradCx = cx - knobRadius * 0.22f
        val gradCy = cy - knobRadius * 0.22f
        sphereShader = RadialGradient(
                gradCx, gradCy,
                knobRadius * 1.30f,
                intArrayOf(accentColorFrom, accentColorTo),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
        )

        // Specular: white → near-white → semi-transparent grey.
        // Three stops reproduce a frosted-glass reflection: a bright central glint fading
        // into a soft grey halo that reads as light diffusing across the curved surface.
        val specAngle = Math.toRadians(SPECULAR_ANGLE_DEG)
        val specDist = knobRadius * 0.46f
        val specCx = cx + (cos(specAngle) * specDist).toFloat()
        val specCy = cy + (sin(specAngle) * specDist).toFloat()
        val specW = knobRadius * 0.40f
        specularShader = RadialGradient(
                specCx, specCy,
                specW,
                intArrayOf(
                        0xFFFFFFFF.toInt(),  // solid white — the bright glint
                        0xFFE8E8E8.toInt(),  // near-white — diffuse transition
                        0x40909090           // semi-transparent grey — soft halo edge
                ),
                floatArrayOf(0f, 0.42f, 1f),
                Shader.TileMode.CLAMP
        )

        // Crescent arc indicator: ∩-arch shape built via Path.Op.DIFFERENCE.
        //
        // Geometry:
        //   outerCircle — full circle centered at the indicator position (top of sphere).
        //   innerCircle — slightly smaller circle shifted DOWNWARD toward the sphere center.
        //
        // Subtracting inner from outer removes the lower portion of the outer circle and
        // leaves a thick arch whose concave opening faces the sphere center.  The arch
        // thickness tapers to cusp points where the two circles' edges meet on each side.
        val indicatorCy = cy - knobRadius * INDICATOR_DISTANCE_FRAC
        val outerR = knobRadius * CRESCENT_OUTER_FRAC
        val innerR = outerR * CRESCENT_INNER_RATIO
        val innerShiftY = outerR * CRESCENT_SHIFT_RATIO

        outerCrescentPath.reset()
        outerCrescentPath.addCircle(cx, indicatorCy, outerR, Path.Direction.CW)

        innerCrescentPath.reset()
        innerCrescentPath.addCircle(cx, indicatorCy + innerShiftY, innerR, Path.Direction.CW)

        crescentPath.reset()
        crescentPath.op(outerCrescentPath, innerCrescentPath, Path.Op.DIFFERENCE)

        // Crescent gradient: horizontal sweep from accentColorFrom (left) to accentColorTo
        // (right) so the arch reads as part of the same glass material as the sphere body.
        crescentShader = LinearGradient(
                cx - outerR, indicatorCy,
                cx + outerR, indicatorCy,
                accentColorFrom, accentColorTo,
                Shader.TileMode.CLAMP
        )

        cachedKnobRadius = knobRadius
    }

    /**
     * Updates the reported intrinsic size in pixels.
     * Call after resolving a dimension resource so wrap_content reports the correct size.
     */
    fun setIntrinsicSize(@Px sizePx: Int) {
        intrinsicSizePx = sizePx
        invalidateSelf()
    }

    companion object {
        @ColorInt
        val NEO_PURPLE: Int = 0xFFA855F7.toInt()
        @ColorInt
        val NEO_PINK: Int = 0xFFEC4899.toInt()
        @ColorInt
        val DEFAULT_BASE_COLOR: Int = 0xFF1E1B2E.toInt()
        @ColorInt
        val DEFAULT_LIGHT_SHADOW: Int = 0x50FFFFFF
        @ColorInt
        val DEFAULT_DARK_SHADOW: Int = 0x70000000

        const val DEFAULT_INTRINSIC_SIZE_PX = 500

        /** Fraction of the outer drawable radius used for the neumorphic plate. */
        private const val PLATE_RADIUS_FRAC = 0.92f

        /** Fraction of the outer drawable radius for the glass sphere (before press-scale). */
        private const val KNOB_RADIUS_FRAC = 0.60f

        /**
         * Distance from the sphere center to the crescent indicator center
         * as a fraction of the sphere radius.  0.76 places the arch well inside
         * the sphere edge so the full crescent is always visible.
         */
        private const val INDICATOR_DISTANCE_FRAC = 0.76f

        /** Outer circle radius of the crescent as a fraction of the sphere radius. */
        private const val CRESCENT_OUTER_FRAC = 0.20f

        /**
         * Inner circle radius as a fraction of the outer circle radius.
         * Closer to 1.0 = thinner crescent rim; further from 1.0 = fatter arch.
         */
        private const val CRESCENT_INNER_RATIO = 0.88f

        /**
         * How far the inner circle's center is shifted downward (toward sphere center)
         * relative to the outer circle radius.  Controls how much of the outer circle
         * the inner one bites away — larger value = taller exposed arch.
         */
        private const val CRESCENT_SHIFT_RATIO = 0.38f

        /**
         * Canvas angle for the specular highlight center.
         * -150° maps to the 10-o'clock position (cos ≈ -0.866, sin ≈ -0.5).
         */
        private const val SPECULAR_ANGLE_DEG = -150.0

        /** Tilt applied to the specular oval to align its long axis with the 10-o'clock radial. */
        private const val SPECULAR_TILT_DEG = -30f
    }
}