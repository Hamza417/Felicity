package app.simple.felicity.decorations.pager

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.models.Theme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A custom [View] that draws a row of indicator dots and animates the "active" dot highlight
 * with **drop physics** — like a condensation droplet on a ceiling that detaches, falls, and
 * splats:
 *
 *  - While **in motion** the blob squishes wide and flat (like a drop mid-fall stretching).
 *  - On **arrival** it momentarily snaps tall and narrow (surface-impact deformation) then
 *    springs back to a perfect circle.
 *
 * The horizontal blob position is governed by a **damped spring** so the dot bounces past
 * its target and settles elastically.
 *
 * Call [setCount] to change the number of dots, [setCurrentPage] to animate the highlight.
 *
 * Theme colors are tracked automatically via [ThemeChangedListener].
 */
class DotsIndicatorView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr), ThemeChangedListener {

    // ── Dot geometry ────────────────────────────────────────────────────────────

    /** Base radius used for inactive dots and the resting active blob, in pixels. */
    var dotRadius = dpToPx(2f)
        set(v) {
            field = v; requestLayout(); invalidate()
        }

    /** Center-to-center spacing between dots in pixels. */
    var dotSpacing = dpToPx(13f)
        set(v) {
            field = v; requestLayout(); invalidate()
        }

    // ── Spring physics (position) ────────────────────────────────────────────────
    /**
     * Spring stiffness for the horizontal blob position.
     * Higher = snappier snap. Reasonable range: 200–800 (1/s²).
     */
    var springStiffness = 120f

    /**
     * Damping ratio for the horizontal blob position spring.
     * < 1.0 = underdamped → the blob overshoots, creating the elastic bounce.
     */
    var springDamping = 0.35f

    // ── State ────────────────────────────────────────────────────────────────────
    private var count = 0
    private var targetPage = 0

    /** Current blob center-X as pixel offset from first dot. */
    private var blobX = 0f

    /** Current blob horizontal velocity (px/s). */
    private var blobVelocity = 0f

    // ── Drop deformation physics ─────────────────────────────────────────────────
    /**
     * The "squash" value in [-1, +1].
     *   0 = perfect circle
     *  +1 = fully flattened (wide, short) — mid-travel like a drop in free fall
     *  -1 = fully stretched tall (narrow, tall) — impact moment on landing
     */
    private var squash = 0f

    /** Velocity of the squash value (s⁻¹). */
    private var squashVelocity = 0f

    // ── Paint & geometry ─────────────────────────────────────────────────────────
    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ovalRect = RectF()

    // ── Choreographer ────────────────────────────────────────────────────────────
    private val choreographer: Choreographer by lazy { Choreographer.getInstance() }
    private var animPosted = false
    private var lastFrameMs = -1L

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        animPosted = false
        tick(frameTimeNanos / 1_000_000L)
    }

    init {
        applyThemeColors()
        ThemeManager.addListener(this)
    }

    // ── Public API ───────────────────────────────────────────────────────────────

    /** Updates the total dot count and resets the highlight to page 0. */
    fun setCount(newCount: Int) {
        count = newCount
        targetPage = 0
        blobX = 0f
        blobVelocity = 0f
        squash = 0f
        squashVelocity = 0f
        lastFrameMs = -1L
        requestLayout()
        invalidate()
    }

    /**
     * Animates the highlight blob toward [page] using spring physics with drop deformation.
     */
    fun setCurrentPage(page: Int) {
        if (page == targetPage && !animPosted) return
        targetPage = page.coerceIn(0, (count - 1).coerceAtLeast(0))
        lastFrameMs = -1L
        postFrame()
    }

    // ── Measurement & drawing ────────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Reserve enough height for the blob to stretch tall: 3× dotRadius
        val maxBlobR = dotRadius * 3f
        val desiredW = if (count <= 1) {
            (2 * maxBlobR).roundToInt()
        } else {
            ((count - 1) * dotSpacing + 2 * maxBlobR).roundToInt()
        }
        val desiredH = (2 * maxBlobR).roundToInt()
        setMeasuredDimension(
                resolveSize(desiredW, widthMeasureSpec),
                resolveSize(desiredH, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (count <= 0) return

        val cx0 = width / 2f - (count - 1) * dotSpacing / 2f
        val cy = height / 2f

        // Draw inactive dots
        @Suppress("EmptyRange")
        for (i in 0 until count) {
            canvas.drawCircle(cx0 + i * dotSpacing, cy, dotRadius, inactivePaint)
        }

        // Compute deformed blob dimensions.
        // squash > 0 → flatten (wide+short), squash < 0 → stretch tall (narrow+tall).
        // We use the area-preserving rule: rW * rH = r²  (constant area).
        val r = dotRadius * 1.3f              // resting blob is slightly larger than inactive
        val maxFlatFactor = 2.2f              // how wide it can get when squashed flat
        val maxTallFactor = 2.0f              // how tall it can get on impact stretch

        val rW: Float
        val rH: Float
        when {
            squash >= 0f -> {
                // Moving: blob flattens — wider, shorter (area-preserving)
                val factor = 1f + squash * (maxFlatFactor - 1f)
                rW = r * factor
                rH = r / factor
            }
            else -> {
                // Landing: blob stretches tall — narrower, taller (area-preserving)
                val factor = 1f + (-squash) * (maxTallFactor - 1f)
                rW = r / factor
                rH = r * factor
            }
        }

        val blobCx = cx0 + blobX
        ovalRect.set(blobCx - rW, cy - rH, blobCx + rW, cy + rH)
        canvas.drawOval(ovalRect, activePaint)
    }

    // ── Physics simulation ───────────────────────────────────────────────────────

    private fun targetBlobX() = targetPage * dotSpacing

    private fun tick(nowMs: Long) {
        if (lastFrameMs == -1L) lastFrameMs = nowMs
        val dtMs = (nowMs - lastFrameMs).coerceIn(0L, 48L)
        lastFrameMs = nowMs
        val dt = dtMs / 1000f   // seconds

        if (dt > 0f) {
            // ── Position spring ──────────────────────────────────────────────────
            val target = targetBlobX()
            val disp = blobX - target
            val c = 2f * springDamping * sqrt(springStiffness.toDouble()).toFloat()
            val accel = -springStiffness * disp - c * blobVelocity
            blobVelocity += accel * dt
            blobX += blobVelocity * dt

            // ── Squash physics ───────────────────────────────────────────────────
            // Target squash is driven by the *speed* of the blob:
            //   - fast horizontal travel → positive squash (flat drop in freefall)
            //   - blob decelerates hard on arrival (velocity crosses zero near target)
            //     → kick squash negative (impact stretch)
            val speed = abs(blobVelocity)
            val maxSpeed = dotSpacing * springStiffness * 0.004f   // normalize
            val speedNorm = (speed / maxSpeed.coerceAtLeast(1f)).coerceIn(0f, 1f)

            // Detect landing: blob is very close to target and speed is small but squash is still positive
            val nearTarget = abs(blobX - target) < dotRadius * 0.5f
            val squashTarget = when {
                nearTarget && squash > 0.05f -> -0.35f   // impact: snap tall briefly
                nearTarget -> 0f        // resting
                else -> speedNorm * 0.75f  // in-flight: flatten
            }

            // Squash spring — snappy, slightly overdamped so it doesn't oscillate too much
            val squashK = 900f
            val squashC = 2f * 0.85f * sqrt(squashK.toDouble()).toFloat()
            val squashAccel = -squashK * (squash - squashTarget) - squashC * squashVelocity
            squashVelocity += squashAccel * dt
            squash += squashVelocity * dt
        }

        invalidate()

        val posRest = abs(blobX - targetBlobX()) < 0.3f && abs(blobVelocity) < 0.8f
        val shapeRest = abs(squash) < 0.01f && abs(squashVelocity) < 0.05f

        if (!posRest || !shapeRest) {
            postFrame()
        } else {
            blobX = targetBlobX()
            blobVelocity = 0f
            squash = 0f
            squashVelocity = 0f
            invalidate()
        }
    }

    private fun postFrame() {
        if (!animPosted) {
            animPosted = true
            choreographer.postFrameCallback(frameCallback)
        }
    }

    // ── Theme ────────────────────────────────────────────────────────────────────

    override fun onAccentChanged(accent: Accent) {
        applyThemeColors()
        invalidate()
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        applyThemeColors()
        invalidate()
    }

    private fun applyThemeColors() {
        val color = Color.WHITE
        activePaint.color = color
        inactivePaint.color = (color and 0x00FFFFFF) or (0x55 shl 24)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ThemeManager.removeListener(this)
        if (animPosted) {
            choreographer.removeFrameCallback(frameCallback)
            animPosted = false
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Float) = dp * resources.displayMetrics.density
}
