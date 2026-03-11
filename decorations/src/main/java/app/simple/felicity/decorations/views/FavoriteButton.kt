package app.simple.felicity.decorations.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.Keyframe
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.os.BundleCompat
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A custom heart-shaped favorite button with rich state animations:
 *
 * - **Pop balloon explode** — particle burst on every state toggle when [isExplosionEnabled] = true
 * - **Heartbeat overshoot** — the heart shrinks to zero then bounces back when [isExplosionEnabled] = false
 * - **Continuous lub-dub pulse** — a natural double-beat heartbeat while the button stays in the
 *   favorite state (frequency = [beatsPerSecond], scale amplitude = [beatIntensity])
 * - Accent color when favorited; regular icon color otherwise
 * - Fully theme-aware via [ThemeChangedListener]
 *
 * ### Heartbeat tuning
 * | Property         | Description                                        | Recommended |
 * |------------------|----------------------------------------------------|-------------|
 * | [beatsPerSecond] | Heartbeat frequency                                | 1.0 – 2.0   |
 * | [beatIntensity]  | Scale-up expansion per beat (0.0 = none, 1.0 = max)| 0.10 – 0.30 |
 */
class FavoriteButton @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr), ThemeChangedListener {

    // ── State ─────────────────────────────────────────────────────────────────

    /**
     * Current favorite state. Use [setFavorite] or [toggle] to change it.
     */
    var isFavorite: Boolean = false
        private set

    // ── Colors ────────────────────────────────────────────────────────────────

    /**
     * Color used while the button is in the *favorite* state.
     * Defaults to the current accent color.
     */
    var favoriteColor: Int = if (isInEditMode) 0xFFE91E63.toInt() else ThemeManager.accent.primaryAccentColor
        set(value) {
            field = value
            if (isFavorite) {
                currentColor = value
                heartPaint.color = value
                invalidate()
            }
        }

    /**
     * Color used while the button is *not* in the favorite state.
     * Defaults to the regular icon color from the active theme.
     */
    var normalColor: Int = if (isInEditMode) 0xFFAAAAAA.toInt() else ThemeManager.theme.iconTheme.regularIconColor
        set(value) {
            field = value
            if (!isFavorite) {
                currentColor = value
                heartPaint.color = value
                invalidate()
            }
        }

    /** Currently displayed / interpolated color. */
    private var currentColor: Int = if (isInEditMode) 0xFFAAAAAA.toInt() else ThemeManager.theme.iconTheme.regularIconColor

    // ── Animation options ─────────────────────────────────────────────────────

    /**
     * When `true` a particle-burst explosion plays on each state toggle.
     * When `false` a heartbeat-style overshoot scale animation is used instead.
     */
    var isExplosionEnabled: Boolean = true

    /**
     * How many heartbeats per second while the button is in the favorite state.
     * Recommended: 1.0 – 2.0  (default **1.2**).
     */
    var beatsPerSecond: Float = 0.6F
        set(value) {
            field = value.coerceAtLeast(0.1f)
            if (isFavorite) restartHeartbeat()
        }

    /**
     * Peak scale expansion on each heartbeat.
     * `0.0` = no visible pulse · `0.3` = 30 % expansion.
     * Recommended: 0.10 – 0.30  (default **0.20**).
     */
    var beatIntensity: Float = 0.10f
        set(value) {
            field = value.coerceIn(0f, 1f)
            if (isFavorite) restartHeartbeat()
        }

    // ── Paints ────────────────────────────────────────────────────────────────

    private val heartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // ── Geometry ──────────────────────────────────────────────────────────────

    private val heartPath = Path()
    private var heartSize = 0f

    // ── Runtime scale ─────────────────────────────────────────────────────────

    /** Uniform scale factor applied to the heart during all animations. */
    private var heartScale = 1f

    // ── Animators ─────────────────────────────────────────────────────────────

    private var toggleAnimator: ValueAnimator? = null
    private var heartbeatAnimator: ValueAnimator? = null
    private var colorAnimator: ValueAnimator? = null
    private var particleAnimator: ValueAnimator? = null

    // ── Particles ─────────────────────────────────────────────────────────────

    private data class Particle(
            var x: Float,
            var y: Float,
            val originX: Float,
            val originY: Float,
            val destX: Float,
            val destY: Float,
            val radius: Float,
            val color: Int,
            var alpha: Float = 1f,
    )

    private val particles = mutableListOf<Particle>()

    // ── Listener ──────────────────────────────────────────────────────────────

    /** Invoked whenever the favorite state changes, passing the new state. */
    var onFavoriteChanged: ((Boolean) -> Unit)? = null

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { toggle() }
        heartPaint.color = currentColor
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        heartSize = min(
                w - paddingLeft - paddingRight,
                h - paddingTop - paddingBottom,
        ).toFloat() * 0.72f
        buildHeartPath(heartSize)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Toggles between favorite and not-favorite with animation. */
    fun toggle() = setFavorite(!isFavorite, animate = true)

    /**
     * Explicitly sets the favorite state.
     *
     * @param favorite  The desired state.
     * @param animate   Whether to animate the transition (default `true`).
     */
    fun setFavorite(favorite: Boolean, animate: Boolean = true) {
        if (isFavorite == favorite) return
        isFavorite = favorite

        val targetColor = if (favorite) favoriteColor else normalColor

        // Cancel any in-flight animation before starting new ones.
        stopHeartbeat()
        toggleAnimator?.cancel()
        particleAnimator?.cancel()
        particles.clear()
        colorAnimator?.cancel()
        heartScale = 1f

        if (animate) {
            animateColorTo(currentColor, targetColor)
            if (isExplosionEnabled) {
                spawnExplosion(targetColor)
            } else {
                animateToggle()
            }
        } else {
            currentColor = targetColor
            heartPaint.color = targetColor
            invalidate()
        }

        if (favorite) startHeartbeat()
        onFavoriteChanged?.invoke(isFavorite)
    }

    // ── Color animation ───────────────────────────────────────────────────────

    private fun animateColorTo(from: Int, to: Int) {
        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            duration = 360L
            addUpdateListener {
                currentColor = it.animatedValue as Int
                heartPaint.color = currentColor
                invalidate()
            }
            start()
        }
    }

    // ── Toggle animation (explosion-off path) ─────────────────────────────────

    /**
     * Shrinks the heart to zero then bounces it back to full size via an
     * overshoot interpolator — giving a satisfying "pop" without particles.
     */
    private fun animateToggle() {
        heartScale = 0f
        toggleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450L
            interpolator = OvershootInterpolator(2.8f)
            addUpdateListener {
                heartScale = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    heartScale = 1f
                    invalidate()
                }
            })
            start()
        }
    }

    // ── Explosion ─────────────────────────────────────────────────────────────

    /**
     * Spawns a radial burst of circular particles from the button centre.
     * Simultaneously the heart icon pops in from scale 0 with a short overshoot.
     *
     * @param color Target color (used to tint some particles).
     */
    private fun spawnExplosion(color: Int) {
        particles.clear()
        val cx = width / 2f
        val cy = height / 2f
        val maxDist = heartSize * 0.92f
        val count = 14

        repeat(count) { i ->
            val baseAngle = (2.0 * Math.PI * i / count).toFloat()
            val jitter = Random.nextFloat() * 0.45f - 0.225f
            val angle = baseAngle + jitter
            val dist = maxDist * (0.50f + Random.nextFloat() * 0.50f)
            val radius = heartSize * (0.035f + Random.nextFloat() * 0.055f)
            val pColor = if (Random.nextBoolean()) color else blendColors(color, currentColor, Random.nextFloat())
            particles += Particle(
                    x = cx, y = cy,
                    originX = cx, originY = cy,
                    destX = cx + cos(angle) * dist,
                    destY = cy + sin(angle) * dist,
                    radius = radius,
                    color = pColor,
            )
        }

        heartScale = 0f
        particleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 540L
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { va ->
                val t = va.animatedValue as Float

                // Move particles outward and fade them
                particles.forEach { p ->
                    p.x = lerp(p.originX, p.destX, t)
                    p.y = lerp(p.originY, p.destY, t)
                    p.alpha = (1f - t).coerceIn(0f, 1f)
                }

                // Heart: 0→0.38 scale from 0→1.18 (overshoot), 0.38→1.0 settle to 1.0
                heartScale = when {
                    t < 0.38f -> (t / 0.38f) * 1.18f
                    else -> lerp(1.18f, 1f, (t - 0.38f) / 0.62f)
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    particles.clear()
                    heartScale = 1f
                    invalidate()
                }
            })
            start()
        }
    }

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    /**
     * Starts the continuous lub-dub heartbeat animation that runs while
     * [isFavorite] is `true`.
     *
     * The keyframe profile (fraction → scale):
     * ```
     *  0.00 → 1.0          (rest)
     *  0.09 → 1.0 + I      (lub — first, larger beat)
     *  0.17 → 1.0 − I×0.08 (brief dip between the two beats)
     *  0.26 → 1.0 + I×0.65 (dub — second, slightly softer beat)
     *  0.38 → 1.0          (settle back to rest)
     *  1.00 → 1.0          (hold rest until next cycle)
     * ```
     * where I = [beatIntensity].
     */
    private fun startHeartbeat() {
        heartbeatAnimator?.cancel()
        val periodMs = (1000.0 / beatsPerSecond).toLong().coerceAtLeast(100L)
        val i = beatIntensity

        val pvh = PropertyValuesHolder.ofKeyframe(
                "s",
                Keyframe.ofFloat(0.00f, 1f),
                Keyframe.ofFloat(0.09f, 1f + i),
                Keyframe.ofFloat(0.17f, 1f - i * 0.08f),
                Keyframe.ofFloat(0.26f, 1f + i * 0.65f),
                Keyframe.ofFloat(0.38f, 1f),
                Keyframe.ofFloat(1.00f, 1f),
        )

        heartbeatAnimator = ValueAnimator.ofPropertyValuesHolder(pvh).apply {
            duration = periodMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                heartScale = it.getAnimatedValue("s") as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopHeartbeat() {
        heartbeatAnimator?.cancel()
        heartbeatAnimator = null
        heartScale = 1f
    }

    private fun restartHeartbeat() {
        stopHeartbeat()
        if (isFavorite) startHeartbeat()
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f

        // Particles drawn behind the heart
        for (p in particles) {
            particlePaint.color = p.color
            particlePaint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, p.radius, particlePaint)
        }

        // Heart — centered at (cx, cy) with animated scale pivot
        canvas.save()
        canvas.scale(heartScale, heartScale, cx, cy)
        canvas.translate(cx, cy)
        heartPaint.color = currentColor
        canvas.drawPath(heartPath, heartPaint)
        canvas.restore()
    }

    // ── Heart path ────────────────────────────────────────────────────────────

    /**
     * Builds a symmetric heart [Path] centered at the origin `(0, 0)`,
     * fitting tightly within a square of side [size].
     *
     * The path is constructed from four cubic Bézier segments:
     * bottom-tip → left-centre → top-dip → right-centre → bottom-tip.
     */
    private fun buildHeartPath(size: Float) {
        heartPath.reset()
        val w = size / 2f   // half-width
        val h = size / 2f   // half-height

        // Bottom tip
        heartPath.moveTo(0f, h * 0.90f)

        // Bottom-left → left-centre
        heartPath.cubicTo(
                -w * 0.09f, h * 0.60f,
                -w * 0.90f, h * 0.30f,
                -w * 0.90f, -h * 0.10f,
        )
        // Left-centre → top dip (through left lobe)
        heartPath.cubicTo(
                -w * 0.90f, -h * 0.65f,
                -w * 0.38f, -h * 0.90f,
                0f, -h * 0.50f,
        )
        // Top dip → right-centre (through right lobe)
        heartPath.cubicTo(
                w * 0.38f, -h * 0.90f,
                w * 0.90f, -h * 0.65f,
                w * 0.90f, -h * 0.10f,
        )
        // Right-centre → bottom tip
        heartPath.cubicTo(
                w * 0.90f, h * 0.30f,
                w * 0.09f, h * 0.60f,
                0f, h * 0.90f,
        )
        heartPath.close()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
        val a = lerp(((c1 shr 24) and 0xFF).toFloat(), ((c2 shr 24) and 0xFF).toFloat(), ratio).toInt()
        val r = lerp(((c1 shr 16) and 0xFF).toFloat(), ((c2 shr 16) and 0xFF).toFloat(), ratio).toInt()
        val g = lerp(((c1 shr 8) and 0xFF).toFloat(), ((c2 shr 8) and 0xFF).toFloat(), ratio).toInt()
        val b = lerp((c1 and 0xFF).toFloat(), (c2 and 0xFF).toFloat(), ratio).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    // ── State persistence ─────────────────────────────────────────────────────

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable(KEY_SUPER_STATE, super.onSaveInstanceState())
        bundle.putBoolean(KEY_IS_FAVORITE, isFavorite)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            val fav = state.getBoolean(KEY_IS_FAVORITE)
            super.onRestoreInstanceState(
                    BundleCompat.getParcelable(state, KEY_SUPER_STATE, Parcelable::class.java),
            )
            setFavorite(fav, animate = false)
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        normalColor = theme.iconTheme.regularIconColor
    }

    override fun onAccentChanged(accent: Accent) {
        favoriteColor = accent.primaryAccentColor
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) ThemeManager.addListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ThemeManager.removeListener(this)
        toggleAnimator?.cancel()
        heartbeatAnimator?.cancel()
        particleAnimator?.cancel()
        colorAnimator?.cancel()
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val KEY_SUPER_STATE = "superState"
        private const val KEY_IS_FAVORITE = "isFavorite"
    }
}