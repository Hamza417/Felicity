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
import android.graphics.Rect
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import androidx.core.os.BundleCompat
import app.simple.felicity.decoration.R
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.models.Theme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A favorite/unfavorite toggle button backed by [R.drawable.ic_favorite_filled] and
 * [R.drawable.ic_favorite_border], with two distinct direction-aware animations:
 *
 * ### Unfavoriting (favorite → not-favorite)  `isExplosionEnabled = true`
 * **Balloon burst** — the filled icon briefly swells (~12 % scale-up), then the icon
 * snaps away and 10–12 irregular torn-rubber polygon shards fly outward in all
 * directions, spinning and fading. When the debris clears the outline icon is revealed.
 *
 * ### Favoriting (not-favorite → favorite)  `isExplosionEnabled = true`
 * **Resurrection** — the filled icon appears immediately. Three escalating *lub-dub*
 * beats play over ~3.5 s — the first barely a flutter, the second medium, the third
 * full-strength — then the animation hands off to the infinite [startHeartbeat] loop.
 *
 * ### `isExplosionEnabled = false`
 * Both directions fall back to a gentle scale-in with a barely-perceptible overshoot.
 *
 * ### Colors
 * - Favorited   → [favoriteColor] (default: accent)
 * - Unfavorited → [normalColor]   (default: regular icon color)
 */
class FavoriteButton @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr), ThemeChangedListener {

    // ── State ─────────────────────────────────────────────────────────────────

    /** Current favorite state. Change via [setFavorite] or [toggle]. */
    var isFavorite: Boolean = false
        private set

    // ── Colors ────────────────────────────────────────────────────────────────

    /** Tint color when favorited. Defaults to the accent color. */
    var favoriteColor: Int = if (isInEditMode) 0xFFE91E63.toInt() else ThemeManager.accent.primaryAccentColor
        set(value) {
            field = value
            if (isFavorite) {
                currentColor = value; invalidate()
            }
        }

    /** Tint color when not favorited. Defaults to the regular icon color. */
    var normalColor: Int = if (isInEditMode) 0xFFAAAAAA.toInt() else ThemeManager.theme.iconTheme.regularIconColor
        set(value) {
            field = value
            if (!isFavorite) {
                currentColor = value; invalidate()
            }
        }

    /** Currently displayed / animating tint color. */
    private var currentColor: Int =
        if (isInEditMode) 0xFFAAAAAA.toInt() else ThemeManager.theme.iconTheme.regularIconColor

    // ── Drawables ─────────────────────────────────────────────────────────────

    private val filledDrawable = if (!isInEditMode)
        ContextCompat.getDrawable(context, R.drawable.ic_favorite_filled)?.mutate() else null

    private val borderDrawable = if (!isInEditMode)
        ContextCompat.getDrawable(context, R.drawable.ic_favorite_border)?.mutate() else null

    private var currentDrawable = borderDrawable

    // ── Options ───────────────────────────────────────────────────────────────

    /**
     * `true`  → direction-aware animations (burst on unfav, resurrection on fav).
     * `false` → gentle scale-in overshoot in both directions.
     */
    var isExplosionEnabled: Boolean = true

    /**
     * Heartbeat frequency while favorited (beats per second).
     * Recommended: 0.5 – 1.5  (default **0.6**).
     */
    var beatsPerSecond: Float = 0.6f
        set(value) {
            field = value.coerceAtLeast(0.1f)
            if (isFavorite) restartHeartbeat()
        }

    /**
     * Peak scale expansion per heartbeat (0 = none, 1 = max).
     * Recommended: 0.05 – 0.20  (default **0.10**).
     */
    var beatIntensity: Float = 0.10f
        set(value) {
            field = value.coerceIn(0f, 1f)
            if (isFavorite) restartHeartbeat()
        }

    // ── Geometry ──────────────────────────────────────────────────────────────

    private var iconSize = 0f
    private val drawableBounds = Rect()

    // ── Animated values ───────────────────────────────────────────────────────

    private var iconScale = 1f

    /**
     * Scale of the [borderDrawable] while it is simultaneously revealed behind
     * the balloon-burst shards (0 = hidden, 1 = full size). Only non-zero during
     * an active [burstAnimation]; reset to 0 on completion or cancellation.
     */
    private var borderRevealScale = 0f

    /**
     * Tracks how far the shard burst has progressed (0 = origin, 1 = destination).
     * Used in [onDraw] to compute each shard's current rotation angle without
     * accumulating floating-point error per frame.
     */
    private var shardProgress = 0f

    // ── Shard model ───────────────────────────────────────────────────────────

    /**
     * One torn-rubber fragment used in the balloon-burst explosion.
     *
     * @param x / y          Current screen position (updated each frame).
     * @param originX/Y      Starting position (near the icon center perimeter).
     * @param destX/Y        Final resting position (well outside the icon).
     * @param startRotation  Initial rotation angle in degrees.
     * @param totalRotation  Total degrees rotated by the time the shard reaches [destX/Y].
     * @param path           Irregular polygon centered at the local origin.
     * @param color          ARGB shard color (carries the accent color of the burst icon).
     * @param alpha          0–1 opacity, faded to 0 as the animation progresses.
     */
    private data class Shard(
            var x: Float,
            var y: Float,
            val originX: Float,
            val originY: Float,
            val destX: Float,
            val destY: Float,
            val startRotation: Float,
            val totalRotation: Float,
            val path: Path,
            val color: Int,
            var alpha: Float = 0f, // starts invisible; revealed only after the burst moment
    )

    private val shards = mutableListOf<Shard>()
    private val shardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // ── Animators ─────────────────────────────────────────────────────────────

    private var toggleAnimator: ValueAnimator? = null
    private var burstAnimator: ValueAnimator? = null
    private var heartbeatAnimator: ValueAnimator? = null
    private var colorAnimator: ValueAnimator? = null

    // ── Callback ──────────────────────────────────────────────────────────────

    /** Invoked whenever the favorite state changes. */
    var onFavoriteChanged: ((Boolean) -> Unit)? = null

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { toggle() }
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        iconSize = min(w - paddingLeft - paddingRight, h - paddingTop - paddingBottom).toFloat() * 0.72f
        val half = (iconSize / 2).toInt()
        drawableBounds.set(-half, -half, half, half)
        filledDrawable?.bounds = drawableBounds
        borderDrawable?.bounds = drawableBounds
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Toggles the favorite state with animation. */
    fun toggle() = setFavorite(!isFavorite, animate = true)

    /**
     * Explicitly sets the favorite state.
     *
     * @param favorite Desired state.
     * @param animate  Whether to animate the transition (default `true`).
     */
    fun setFavorite(favorite: Boolean, animate: Boolean = true) {
        if (isFavorite == favorite) return
        val fromColor = currentColor
        isFavorite = favorite
        val targetColor = if (favorite) favoriteColor else normalColor

        stopHeartbeat()
        cancelTransitionAnimators()

        if (animate) {
            animateColorTo(fromColor, targetColor)
            if (isExplosionEnabled) {
                if (favorite) {
                    // ── Favoriting: resurrection ──────────────────────────────
                    // Switch to the filled icon straight away; the animation
                    // drives the scale so the heart "comes back to life" gradually.
                    currentDrawable = filledDrawable
                    startHeartbeatWithResurrection()
                } else {
                    // ── Unfavoriting: balloon burst ───────────────────────────
                    // currentDrawable is still filledDrawable (we're leaving fav);
                    // it will be replaced by borderDrawable at the end of the burst.
                    burstAnimation(fromColor)
                }
            } else {
                currentDrawable = if (favorite) filledDrawable else borderDrawable
                animateSubtlePop()
                if (favorite) startHeartbeat()
            }
        } else {
            currentColor = targetColor
            currentDrawable = if (favorite) filledDrawable else borderDrawable
            iconScale = 1f
            invalidate()
            if (favorite) startHeartbeat()
        }

        onFavoriteChanged?.invoke(isFavorite)
    }

    // ── Color ─────────────────────────────────────────────────────────────────

    private fun animateColorTo(from: Int, to: Int) {
        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            duration = 380L
            addUpdateListener { currentColor = it.animatedValue as Int; invalidate() }
            start()
        }
    }

    // ── Balloon burst (unfavoriting) ──────────────────────────────────────────

    /**
     * Three-phase balloon burst:
     *
     * 1. **Pre-pop swell** (t = 0 → 0.14): icon scales from 1.0 → 1.12. Shards are
     *    spawned but invisible (`alpha = 0`), ready to fire.
     * 2. **Burst** (t = 0.14+): icon scale snaps to 0, shards explode outward with
     *    random rotation and deceleration, fading as they travel.
     * 3. **End**: shards cleared, [borderDrawable] shown at full scale.
     *
     * @param fromColor The accent/favorite color — shards carry this tint.
     */
    private fun burstAnimation(fromColor: Int) {
        shards.clear()
        shardProgress = 0f

        val cx = width / 2f
        val cy = height / 2f
        val count = 11

        repeat(count) { i ->
            val baseAngle = (2.0 * Math.PI * i / count).toFloat()
            val jitter = (Random.nextFloat() - 0.5f) * 0.55f
            val angle = baseAngle + jitter
            // Shards start near the perimeter of the icon, not at dead-center,
            // to reinforce the illusion that the icon itself is fragmenting.
            val startR = iconSize * (0.08f + Random.nextFloat() * 0.22f)
            val destR = iconSize * (0.68f + Random.nextFloat() * 0.52f)
            val shardSize = iconSize * (0.07f + Random.nextFloat() * 0.09f)

            shards += Shard(
                    x = cx + cos(angle) * startR,
                    y = cy + sin(angle) * startR,
                    originX = cx + cos(angle) * startR,
                    originY = cy + sin(angle) * startR,
                    destX = cx + cos(angle) * destR,
                    destY = cy + sin(angle) * destR,
                    startRotation = Random.nextFloat() * 360f,
                    totalRotation = (Random.nextFloat() - 0.5f) * 640f, // ±320 °
                    path = buildShardPath(shardSize),
                    color = fromColor,
                    alpha = 0f,
            )
        }

        iconScale = 1f
        burstAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 620L
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { va ->
                val t = va.animatedValue as Float
                if (t < 0.14f) {
                    // Pre-pop swell — icon grows slightly under pressure
                    iconScale = lerp(1f, 1.12f, t / 0.14f)
                    shardProgress = 0f
                    borderRevealScale = 0f
                } else {
                    // Burst — filled icon vanishes, border scales in simultaneously behind shards
                    val bt = (t - 0.14f) / 0.86f
                    shardProgress = bt
                    iconScale = 0f

                    // Border scales in over the first 65 % of the burst using a smooth-step
                    // curve so it feels like it surfaces from behind the explosion.
                    val revealT = (bt / 0.65f).coerceIn(0f, 1f)
                    borderRevealScale = smoothStep(revealT)

                    shards.forEach { s ->
                        s.x = lerp(s.originX, s.destX, bt)
                        s.y = lerp(s.originY, s.destY, bt)
                        // Fade starts full at burst moment and reaches 0 slightly
                        // before the shards hit their destinations.
                        s.alpha = (1f - bt * 1.18f).coerceIn(0f, 1f)
                    }
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    shards.clear()
                    shardProgress = 0f
                    borderRevealScale = 0f
                    currentDrawable = borderDrawable
                    iconScale = 1f
                    invalidate()
                }
            })
            start()
        }
    }

    /**
     * Builds an irregular polygon [Path] centered at the local origin `(0, 0)`.
     *
     * A random number of vertices (3 or 4) are placed around the origin with
     * small angular and radial jitter to produce an uneven, torn-edge silhouette.
     */
    private fun buildShardPath(size: Float): Path {
        val path = Path()
        val sides = 3 + Random.nextInt(2)
        val firstAngle = Random.nextFloat() * (2f * Math.PI.toFloat())
        val angleStep = (2f * Math.PI.toFloat()) / sides

        path.moveTo(
                cos(firstAngle) * size * (0.40f + Random.nextFloat() * 0.60f),
                sin(firstAngle) * size * (0.40f + Random.nextFloat() * 0.60f),
        )
        for (k in 1 until sides) {
            val a = firstAngle + angleStep * k + (Random.nextFloat() - 0.5f) * angleStep * 0.70f
            val r = size * (0.35f + Random.nextFloat() * 0.65f)
            path.lineTo(cos(a) * r, sin(a) * r)
        }
        path.close()
        return path
    }

    // ── Subtle pop (explosion disabled) ──────────────────────────────────────

    /**
     * Used when [isExplosionEnabled] is `false`.
     * Scales the icon in from 82 % with a barely-there overshoot (~2 %).
     */
    private fun animateSubtlePop() {
        iconScale = 0.82f
        toggleAnimator = ValueAnimator.ofFloat(0.82f, 1f).apply {
            duration = 300L
            interpolator = OvershootInterpolator(0.6f)
            addUpdateListener { iconScale = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    iconScale = 1f; invalidate()
                }
            })
            start()
        }
    }

    // ── Resurrection heartbeat (favoriting) ───────────────────────────────────

    /**
     * Plays a one-shot warm-up sequence before handing off to the infinite
     * [startHeartbeat] loop. The three escalating beats simulate a heart
     * "waking up" after being dormant:
     *
     * ```
     * fraction → scale             description
     * ──────────────────────────────────────────────────────────────
     *  0.00  →  1.0                silence
     *  0.09  →  1 + I × 0.25      beat 1 — barely a flutter
     *  0.20  →  1.0                silence
     *  0.34  →  1 + I × 0.55      beat 2 — lub, medium
     *  0.42  →  1 − I × 0.04      dip
     *  0.49  →  1 + I × 0.38      beat 2 — dub, softer
     *  0.59  →  1.0                silence
     *  0.71  →  1 + I             beat 3 — lub, full strength
     *  0.77  →  1 − I × 0.08      dip
     *  0.84  →  1 + I × 0.65      beat 3 — dub
     *  1.00  →  1.0                final rest → hand off to normal loop
     * ```
     * Total warm-up duration: 3 500 ms.
     */
    private fun startHeartbeatWithResurrection() {
        heartbeatAnimator?.cancel()
        val i = beatIntensity

        val pvh = PropertyValuesHolder.ofKeyframe(
                "s",
                Keyframe.ofFloat(0.00f, 1f),
                // Beat 1 — barely alive
                Keyframe.ofFloat(0.09f, 1f + i * 0.25f),
                Keyframe.ofFloat(0.20f, 1f),
                // Beat 2 — medium, first dub appearing
                Keyframe.ofFloat(0.34f, 1f + i * 0.55f),
                Keyframe.ofFloat(0.42f, 1f - i * 0.04f),
                Keyframe.ofFloat(0.49f, 1f + i * 0.38f),
                Keyframe.ofFloat(0.59f, 1f),
                // Beat 3 — full lub-dub, ready to loop
                Keyframe.ofFloat(0.71f, 1f + i),
                Keyframe.ofFloat(0.77f, 1f - i * 0.08f),
                Keyframe.ofFloat(0.84f, 1f + i * 0.65f),
                Keyframe.ofFloat(1.00f, 1f),
        )

        heartbeatAnimator = ValueAnimator.ofPropertyValuesHolder(pvh).apply {
            duration = 3_500L
            addUpdateListener { iconScale = it.getAnimatedValue("s") as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (isFavorite) startHeartbeat()
                }
            })
            start()
        }
    }

    // ── Normal heartbeat loop ─────────────────────────────────────────────────

    /**
     * Infinite *lub-dub* heartbeat running while [isFavorite] is `true`.
     *
     * ```
     *  0.00 → 1.0           rest
     *  0.09 → 1.0 + I       lub  (primary)
     *  0.17 → 1.0 − I×0.08  inter-beat dip
     *  0.26 → 1.0 + I×0.65  dub  (secondary)
     *  0.38 → 1.0           settle
     *  1.00 → 1.0           hold
     * ```
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
            addUpdateListener { iconScale = it.getAnimatedValue("s") as Float; invalidate() }
            start()
        }
    }

    private fun stopHeartbeat() {
        heartbeatAnimator?.cancel()
        heartbeatAnimator = null
    }

    private fun restartHeartbeat() {
        stopHeartbeat()
        if (isFavorite) startHeartbeat()
    }

    // ── Cancel helpers ────────────────────────────────────────────────────────

    private fun cancelTransitionAnimators() {
        toggleAnimator?.cancel(); toggleAnimator = null
        burstAnimator?.cancel(); burstAnimator = null
        colorAnimator?.cancel(); colorAnimator = null
        shards.clear()
        shardProgress = 0f
        borderRevealScale = 0f
        iconScale = 1f
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f

        // 1 — Border icon revealed simultaneously behind the burst shards.
        //     Only drawn when borderRevealScale > 0 (i.e. during an active burst).
        if (borderRevealScale > 0f) {
            borderDrawable?.let { bd ->
                bd.setTint(normalColor)
                canvas.withScale(borderRevealScale, borderRevealScale, cx, cy) {
                    translate(cx, cy)
                    bd.draw(this)
                }
            }
        }

        // 2 — Main icon (drawn above the border reveal; during burst this has iconScale = 0
        //     so nothing is visible, letting the border show through unobstructed).
        val d = currentDrawable ?: return
        d.setTint(currentColor)
        canvas.withScale(iconScale, iconScale, cx, cy) {
            translate(cx, cy)
            d.draw(this)
        }

        // 3 — Shards (drawn on top of everything; only present during the burst animation)
        for (shard in shards) {
            if (shard.alpha <= 0f) continue
            val alphaInt = (shard.alpha * 255f).toInt().coerceIn(0, 255)
            // Encode animated alpha into the shard's RGB color.
            shardPaint.color = shard.color
            shardPaint.alpha = alphaInt
            // Rotation angle is derived from shardProgress so it is always
            // consistent with the shard's current position — no per-frame accumulation.
            val rotation = shard.startRotation + shard.totalRotation * shardProgress
            canvas.withTranslation(shard.x, shard.y) {
                rotate(rotation)
                drawPath(shard.path, shardPaint)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /**
     * Classic smooth-step curve: starts and ends with zero derivative for a
     * natural ease-in / ease-out feel. [t] must be in [0, 1].
     *
     * @author Hamza417
     */
    private fun smoothStep(t: Float): Float = t * t * (3f - 2f * t)


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
        cancelTransitionAnimators()
        stopHeartbeat()
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val KEY_SUPER_STATE = "superState"
        private const val KEY_IS_FAVORITE = "isFavorite"
    }
}