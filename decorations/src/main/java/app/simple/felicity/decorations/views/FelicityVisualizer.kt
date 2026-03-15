package app.simple.felicity.decorations.views

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import androidx.core.content.withStyledAttributes
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.views.FelicityVisualizer.Companion.BAND_COUNT
import app.simple.felicity.decorations.views.FelicityVisualizer.Companion.CORNER_RADIUS_FACTOR
import app.simple.felicity.decorations.views.FelicityVisualizer.Companion.MAX_PARTICLES
import app.simple.felicity.decorations.views.FelicityVisualizer.Companion.PARTICLE_FADE_RATE
import app.simple.felicity.manager.SharedPreferences.registerListener
import app.simple.felicity.manager.SharedPreferences.unregisterListener
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A custom audio spectrum visualizer that renders [BAND_COUNT] vertical bars animated
 * in real time from bass (left) to treble (right).
 *
 * Incoming RMS bands are normalised with a fast-attack/slow-release AGC, passed through
 * a square-root perceptual curve, and delivered to their target bars after a configurable
 * latency-compensation delay so the display stays in sync with audible output.
 *
 * Peak-hold caps accelerate downward under simulated gravity once the hold period expires.
 * Colors default to the current theme accent and update automatically on accent changes.
 *
 * @author Hamza417
 */
class FelicityVisualizer @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), ThemeChangedListener {

    // ── Band state ────────────────────────────────────────────────────────────

    /** Current smoothed magnitude for each band (the value actually rendered). */
    private val currentBands = FloatArray(BAND_COUNT)

    /** Target magnitude for each band, drained from the delay queue. */
    private val targetBands = FloatArray(BAND_COUNT)

    /** Highest magnitude reached; the peak cap tracks this value. */
    private val peakBands = FloatArray(BAND_COUNT)

    /** Downward velocity of each peak cap in normalized units per frame (gravity accumulates here). */
    private val peakVelocities = FloatArray(BAND_COUNT)

    /** Remaining hold frames before gravity starts pulling the cap down. */
    private val peakHoldCounters = IntArray(BAND_COUNT)

    // ── AGC ───────────────────────────────────────────────────────────────────

    /**
     * Smoothed peak across all bands used as the AGC reference.
     * Fast attack, slow release — the full view height is always used meaningfully
     * without any band ever exceeding it.
     */
    private var smoothedMax = MIN_SMOOTHED_MAX

    // ── Latency delay queue ───────────────────────────────────────────────────

    /**
     * Holds normalized band snapshots with a `SystemClock.elapsedRealtime` deadline.
     * A snapshot is only applied to [targetBands] once the deadline has passed,
     * compensating for the hardware audio output buffer latency so the bars
     * move in sync with what the user actually hears.
     */
    private class TimedBands(val deadline: Long, val bands: FloatArray)

    /**
     * A single floating particle belonging to the ash/snow emitter.
     *
     * All fields are mutable so the particle can be updated in-place each frame
     * without additional heap allocation.
     */
    private class Particle {
        var x: Float = 0f
        var y: Float = 0f

        /** Horizontal drift velocity in pixels per frame. */
        var vx: Float = 0f

        /** Vertical velocity in pixels per frame — negative value means upward. */
        var vy: Float = 0f

        /** Current opacity in the [0..1] range. Multiplied by [PARTICLE_FADE_RATE] each frame. */
        var alpha: Float = 1f

        /** Radius of the particle circle in pixels. */
        var radius: Float = 2f

        /** Per-particle Brownian turbulence strength coefficient. */
        var turbulence: Float = 0.2f
    }

    private val delayQueue = ArrayDeque<TimedBands>()

    /**
     * Milliseconds added to each band snapshot's timestamp before it is applied.
     * Increase this if bars still jump ahead of the beat on your device.
     */
    var latencyMs: Long = DEFAULT_LATENCY_MS

    // ── Drawing ───────────────────────────────────────────────────────────────

    /** Paint for the frequency bars; shader is rebuilt whenever width or colors change. */
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** Paint for the peak-hold cap pill above each bar. */
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        alpha = 220
    }

    /** Reusable rect — avoids per-frame allocation in [onDraw]. */
    private val drawRect = RectF()

    private var gradient: LinearGradient? = null
    private var cachedWidth = 0

    /**
     * Current gradient color stops. Reassigned by [setColors] or when the theme accent changes.
     * Triggers a gradient rebuild on the next [onDraw].
     */
    private var barColors: IntArray = buildAccentColors()

    /**
     * Whether an animation frame is already queued via [postInvalidateOnAnimation].
     * Prevents duplicate scheduling when [setBands] is called faster than the display refreshes.
     */
    private var animating = false

    /**
     * Corner radius expressed as a fraction of one bar's width [0..0.5].
     * Derived from [AppearancePreferences.getCornerRadius] scaled by [CORNER_RADIUS_FACTOR]
     * and updated live via [prefsListener].
     */
    private var cornerRadiusFraction = computeCornerFraction()

    /**
     * Listens for live changes to [AppearancePreferences.APP_CORNER_RADIUS] so the bar
     * corners update immediately when the user moves the corner-radius slider in Settings.
     */
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppearancePreferences.APP_CORNER_RADIUS) {
            cornerRadiusFraction = computeCornerFraction()
            invalidate()
        }
    }

    // ── Particle system ───────────────────────────────────────────────────────

    /**
     * Controls whether the ash-particle emitter is active.
     *
     * All live particles are cleared immediately when set to `false`.
     * Defaults to `false` — enable during testing or when desired.
     */
    var particlesEnabled: Boolean = false
        set(value) {
            field = value
            if (!value) particles.clear()
        }

    /** Live list of active particles, updated and rendered inside each [onDraw] call. */
    private val particles = ArrayList<Particle>(MAX_PARTICLES)

    /** Per-band countdown preventing rapid-fire particle floods from a single band. */
    private val particleCooldowns = IntArray(BAND_COUNT)

    /** Paint shared by all particles. [Paint.alpha] is overwritten per particle in [onDraw]. */
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    // ── Bar path (top-only rounded corners) ──────────────────────────────────

    /** Reusable [Path] for rendering bars with rounded top corners and a flat base. */
    private val barPath = Path()

    /**
     * Eight-element radii array for [Path.addRoundRect].
     *
     * Indices 0-3 carry the top-left and top-right radii; indices 4-7 are always 0
     * so the base corners of every bar remain flat regardless of the corner preference.
     */
    private val barCornerRadii = FloatArray(8)

    // ── Render height override ────────────────────────────────────────────────

    /**
     * Maximum bar-render height in pixels.
     *
     * When greater than zero this value replaces the view's own [height] in all bar
     * scaling calculations, letting the visualizer live inside a `match_parent`
     * container while the animated region stays within a controlled boundary.
     *
     * Set to 0 (the default) to use the full view height.
     * Can also be configured via the `vizMaxRenderHeight` XML attribute.
     */
    var maxRenderHeightPx: Int = 0
        set(value) {
            field = value.coerceAtLeast(0)
            invalidate()
        }

    init {
        // The visualizer is a transparent overlay — it must never draw an opaque background
        // and must not intercept touch events so the underlying pager remains scrollable.
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
        // Hardware layer enables proper alpha compositing when rendered over album art.
        setLayerType(LAYER_TYPE_HARDWARE, null)
        if (!isInEditMode && attrs != null) {
            context.withStyledAttributes(attrs, R.styleable.FelicityVisualizer, defStyleAttr, 0) {
                maxRenderHeightPx = getDimensionPixelSize(R.styleable.FelicityVisualizer_vizMaxRenderHeight, 0)
                particlesEnabled = getBoolean(R.styleable.FelicityVisualizer_vizParticlesEnabled, false)
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Delivers a new spectrum snapshot to the visualizer.
     *
     * The bands are normalized with AGC + square-root compression and stored in the
     * delay queue. They will be applied to [targetBands] once [latencyMs] has elapsed,
     * keeping the display in sync with audible playback.
     *
     * @param bands Raw RMS magnitudes — any positive float scale is accepted.
     *              Length should equal [BAND_COUNT]; extra elements are silently ignored.
     */
    fun setBands(bands: FloatArray) {
        val len = minOf(bands.size, BAND_COUNT)

        // Fast-attack / slow-release AGC.
        var frameMax = MIN_SMOOTHED_MAX
        for (i in 0 until len) {
            if (bands[i] > frameMax) frameMax = bands[i]
        }
        smoothedMax = if (frameMax > smoothedMax) {
            smoothedMax + (frameMax - smoothedMax) * AGC_ATTACK
        } else {
            (smoothedMax + (frameMax - smoothedMax) * AGC_RELEASE).coerceAtLeast(MIN_SMOOTHED_MAX)
        }

        val invMax = 1f / smoothedMax
        val normalized = FloatArray(BAND_COUNT)
        for (i in 0 until len) {
            val linear = (bands[i] * invMax).coerceIn(0f, 1f)
            // Noise floor gate: bands that are just sensor/FFT leakage noise are zeroed
            // out so a silent bass region doesn't feather into adjacent zones.
            normalized[i] = if (linear < NOISE_FLOOR) 0f else sqrt(linear)
        }

        delayQueue.addLast(TimedBands(SystemClock.elapsedRealtime() + latencyMs, normalized))
        scheduleRedraw()
    }

    /**
     * Replaces the bar gradient color stops.
     *
     * The array must contain at least two ARGB colors; they are spread evenly across
     * the full width of the view (bass on the left, treble on the right).
     * Pass an empty array to revert to the current theme accent colors.
     *
     * @param colors ARGB color integers for the gradient stops.
     */
    fun setColors(colors: IntArray) {
        barColors = if (colors.size >= 2) colors else buildAccentColors()
        gradient = null  // force rebuild on next draw
        invalidate()
    }

    /**
     * Sets the color of all peak-hold cap pills.
     *
     * @param color ARGB color integer.
     */
    fun setCapColor(color: Int) {
        capPaint.color = color
        invalidate()
    }

    /** Animates all bars and peaks down to zero. */
    fun clear() {
        targetBands.fill(0f)
        scheduleRedraw()
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun scheduleRedraw() {
        if (!animating) {
            animating = true
            postInvalidateOnAnimation()
        }
    }

    /**
     * Drains all delay-queue entries whose deadline has passed, keeping only the most
     * recent one as the new [targetBands]. Returns true if targets were updated.
     */
    private fun drainQueueIntoTargets(): Boolean {
        val now = SystemClock.elapsedRealtime()
        var latest: FloatArray? = null
        while (delayQueue.isNotEmpty() && delayQueue.first().deadline <= now) {
            latest = delayQueue.removeFirst().bands
        }
        if (latest != null) {
            System.arraycopy(latest, 0, targetBands, 0, BAND_COUNT)
            return true
        }
        return false
    }

    private fun buildAccentColors(): IntArray {
        return if (isInEditMode) {
            intArrayOf(0xFFFF6200.toInt(), 0xFF00B0FF.toInt())
        } else {
            intArrayOf(
                    ThemeManager.accent.primaryAccentColor,
                    ThemeManager.accent.secondaryAccentColor
            )
        }
    }

    // ── Size / gradient ───────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildGradient(w)
    }

    private fun rebuildGradient(w: Int) {
        if (w == 0) return
        cachedWidth = w

        val colors = barColors
        val positions = FloatArray(colors.size) { i -> i.toFloat() / (colors.size - 1) }

        gradient = LinearGradient(
                0f, 0f, w.toFloat(), 0f,
                colors, positions,
                Shader.TileMode.CLAMP
        )
        barPaint.shader = gradient
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        if (cachedWidth != width) rebuildGradient(width)

        drainQueueIntoTargets()

        val slotWidth = width.toFloat() / BAND_COUNT
        val gapWidth = slotWidth * BAR_GAP_FRACTION
        val barWidth = slotWidth - gapWidth
        // Corner radius is a live fraction of bar width, capped at half-width (full pill).
        val cornerRadius = (cornerRadiusFraction * barWidth).coerceIn(0f, barWidth / 2f)
        // Respect the optional render-height override so bars are bounded independently
        // of the view's actual height in match_parent scenarios.
        val maxBarHeight = if (maxRenderHeightPx > 0) maxRenderHeightPx.toFloat() else height.toFloat()
        val capPillHeight = (barWidth * 0.3f).coerceAtLeast(3f)

        // Populate the top-only corner radii array once per frame.
        // Indices 0-3 carry the top-left and top-right radius.
        // Indices 4-7 are always 0 so the base of every bar stays flat.
        barCornerRadii[0] = cornerRadius; barCornerRadii[1] = cornerRadius  // top-left
        barCornerRadii[2] = cornerRadius; barCornerRadii[3] = cornerRadius  // top-right
        barCornerRadii[4] = 0f; barCornerRadii[5] = 0f            // bottom-right (flat)
        barCornerRadii[6] = 0f; barCornerRadii[7] = 0f            // bottom-left  (flat)

        var stillMoving = delayQueue.isNotEmpty()

        for (i in 0 until BAND_COUNT) {
            val target = targetBands[i]
            val current = currentBands[i]

            val speed = if (target > current) RISE_SPEED else FALL_SPEED
            val next = (current + (target - current) * speed).coerceIn(0f, 1f)
            currentBands[i] = next

            if (abs(next - current) > IDLE_THRESHOLD) stillMoving = true

            val barHeightPx = (next * maxBarHeight).coerceIn(MIN_BAR_PX, maxBarHeight)
            val left = i * slotWidth + gapWidth / 2f
            val right = left + barWidth

            // Draw the bar via a Path so only the top corners receive the corner radius.
            drawRect.set(left, maxBarHeight - barHeightPx, right, maxBarHeight)
            barPath.reset()
            barPath.addRoundRect(drawRect, barCornerRadii, Path.Direction.CW)
            canvas.drawPath(barPath, barPaint)

            // Advance the per-band particle cooldown every frame.
            if (particlesEnabled && particleCooldowns[i] > 0) {
                particleCooldowns[i]--
            }

            // Peak cap — pushed up by the bar, then falls under gravity.
            if (next >= peakBands[i]) {
                // Bar is pushing the cap up — reset velocity and restart hold timer.
                peakBands[i] = next
                peakVelocities[i] = 0f
                peakHoldCounters[i] = PEAK_HOLD_FRAMES
                // Emit a particle when the bar drives the peak to a new high.
                if (particlesEnabled && particleCooldowns[i] <= 0 && next > PARTICLE_MIN_HEIGHT) {
                    emitParticle(left + barWidth / 2f, maxBarHeight - next * maxBarHeight)
                    particleCooldowns[i] = PARTICLE_COOLDOWN_FRAMES
                }
            } else {
                if (peakHoldCounters[i] > 0) {
                    // Holding position — cap stays put while the bar falls away beneath it.
                    peakHoldCounters[i]--
                } else {
                    // Gravity phase — velocity increases each frame, cap accelerates downward.
                    peakVelocities[i] = (peakVelocities[i] + GRAVITY).coerceAtMost(MAX_PEAK_VELOCITY)
                    peakBands[i] = (peakBands[i] - peakVelocities[i]).coerceAtLeast(0f)
                }
                if (peakBands[i] > IDLE_THRESHOLD) stillMoving = true
                // Emit a particle when the bar crosses the two-thirds-of-peak threshold.
                val twoThirdsPeak = peakBands[i] * (2f / 3f)
                if (particlesEnabled && particleCooldowns[i] <= 0 &&
                        next >= twoThirdsPeak && next > PARTICLE_MIN_HEIGHT
                ) {
                    emitParticle(left + barWidth / 2f, maxBarHeight - next * maxBarHeight)
                    particleCooldowns[i] = PARTICLE_COOLDOWN_FRAMES
                }
            }

            if (peakBands[i] > 0.02f) {
                val peakY = maxBarHeight - peakBands[i] * maxBarHeight
                drawRect.set(
                        left,
                        (peakY - capPillHeight / 2f).coerceAtLeast(0f),
                        right,
                        (peakY + capPillHeight / 2f).coerceAtMost(maxBarHeight)
                )
                canvas.drawRoundRect(drawRect, cornerRadius, cornerRadius, capPaint)
            }
        }

        // Update physics and render all live ash particles.
        if (particlesEnabled && particles.isNotEmpty()) {
            val iter = particles.iterator()
            while (iter.hasNext()) {
                val p = iter.next()
                // Brownian horizontal jitter — each particle owns a unique turbulence
                // coefficient so they spread naturally, mimicking diffused air motion.
                p.vx += (Random.nextFloat() - 0.5f) * p.turbulence
                // Horizontal air-resistance drag keeps the lateral drift bounded.
                p.vx *= PARTICLE_DRAG
                // Very slight vertical deceleration keeps the ascent nearly constant,
                // like warm ash buoyed by rising heat.
                p.vy *= PARTICLE_VERTICAL_DECAY
                p.x += p.vx
                p.y += p.vy
                p.alpha *= PARTICLE_FADE_RATE
                if (p.alpha < 0.01f || p.y < -p.radius) {
                    iter.remove()
                    continue
                }
                particlePaint.alpha = (p.alpha * 255f).toInt()
                canvas.drawCircle(p.x, p.y, p.radius, particlePaint)
                stillMoving = true
            }
        }

        if (stillMoving) {
            postInvalidateOnAnimation()
        } else {
            animating = false
        }
    }

    /**
     * Spawns a new [Particle] at the given canvas coordinates.
     *
     * The particle receives randomized velocity, size, initial alpha, and turbulence
     * coefficient to produce the varied diffused-ash appearance. If [MAX_PARTICLES]
     * is already reached the oldest particle is evicted before the new one is inserted.
     *
     * @param x Horizontal center of the emission point in canvas pixels.
     * @param y Vertical coordinate of the emission point in canvas pixels.
     */
    private fun emitParticle(x: Float, y: Float) {
        if (particles.size >= MAX_PARTICLES) {
            particles.removeAt(0)
        }
        val p = Particle()
        p.x = x + (Random.nextFloat() - 0.5f) * 6f
        p.y = y
        p.vx = (Random.nextFloat() - 0.5f) * 0.8f
        p.vy = -(Random.nextFloat() * PARTICLE_SPEED_RANGE + PARTICLE_SPEED_MIN)
        p.alpha = Random.nextFloat() * 0.4f + 0.6f
        p.radius = Random.nextFloat() * 2.0f + 1.5f
        p.turbulence = Random.nextFloat() * 0.25f + 0.1f
        particles.add(p)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            ThemeManager.addListener(this)
            registerListener(prefsListener)
            // Sync in case the preference changed while detached.
            cornerRadiusFraction = computeCornerFraction()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ThemeManager.removeListener(this)
        unregisterListener(prefsListener)
        delayQueue.clear()
        particles.clear()
    }

    // ── ThemeChangedListener ──────────────────────────────────────────────────

    override fun onAccentChanged(accent: Accent) {
        // Only update if the caller has not already set a custom palette via setColors().
        if (barColors.size == 2 &&
                (barColors[0] == ThemeManager.accent.primaryAccentColor ||
                        barColors[1] == ThemeManager.accent.secondaryAccentColor)
        ) {
            barColors = buildAccentColors()
            gradient = null
            invalidate()
        }
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        // The bars use accent colors, not theme background colors — no action needed.
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Computes the corner radius as a fraction of bar width from the current app preference.
     *
     * [AppearancePreferences.getCornerRadius] returns a value in [1..80]; dividing by
     * [AppearancePreferences.MAX_CORNER_RADIUS] normalises it to (0..1], which is then
     * multiplied by [CORNER_RADIUS_FACTOR] so the corners look proportional to the narrow bars.
     */
    private fun computeCornerFraction(): Float {
        if (isInEditMode) return 0.35f
        return (AppearancePreferences.getCornerRadius() / AppearancePreferences.MAX_CORNER_RADIUS) * CORNER_RADIUS_FACTOR
    }

    companion object {
        /** Number of frequency bands rendered — must match the engine's VisualizerAudioProcessor band count. */
        const val BAND_COUNT = 40

        /** Fraction of each band slot occupied by the gap between adjacent bars. */
        private const val BAR_GAP_FRACTION = 0.22f

        /** Lerp factor per frame when a bar is rising toward a louder target. */
        private const val RISE_SPEED = 0.25f

        /** Lerp factor per frame when a bar is falling toward a quieter target. */
        private const val FALL_SPEED = 0.08f

        /**
         * Gravity added to a cap's downward velocity each frame while it is falling.
         * Normalized units (0..1 per frame²), so the cap accelerates like a physical object.
         */
        private const val GRAVITY = 0.0018f

        /** Terminal velocity cap for the falling peak pill to prevent it from teleporting. */
        private const val MAX_PEAK_VELOCITY = 0.04f

        /** Frames the peak cap holds its highest position before gravity starts. */
        private const val PEAK_HOLD_FRAMES = 20

        /** Change threshold below which a bar is considered idle and animation may stop. */
        private const val IDLE_THRESHOLD = 0.0008f

        /** Minimum bar height in pixels so silent bars remain perceptible. */
        private const val MIN_BAR_PX = 4f

        /** AGC attack: how quickly [smoothedMax] rises to a louder signal. */
        private const val AGC_ATTACK = 0.4f

        /** AGC release: how slowly [smoothedMax] decays during quiet passages. */
        private const val AGC_RELEASE = 0.004f

        /** Floor for [smoothedMax] to prevent division-by-zero during silence. */
        private const val MIN_SMOOTHED_MAX = 0.0001f

        /**
         * Normalized magnitude below which a band is treated as silence (noise floor gate).
         * Kept intentionally low so quiet treble content remains visible; only true
         * FFT-leakage noise (sub-1.5 % of peak) is suppressed.
         */
        private const val NOISE_FLOOR = 0.015f

        /**
         * Default latency compensation in milliseconds.
         * Audio data passes through the ExoPlayer write buffer and hardware DAC buffers
         * before reaching the speakers, so the visualizer needs this delay to stay in sync.
         * Tune via [latencyMs] if your device's output latency differs.
         */
        const val DEFAULT_LATENCY_MS = 150L

        /**
         * Scale factor applied to the normalised app corner radius when mapping it to bar width.
         * Values above 1.0 produce pill-shaped bars at maximum app corner radius.
         */
        private const val CORNER_RADIUS_FACTOR = 1.5f

        /** Minimum bar height (target) for emitting a particle. */
        private const val PARTICLE_MIN_HEIGHT = 0.15f

        /** Particle emission cooldown in frames to prevent flooding. */
        private const val PARTICLE_COOLDOWN_FRAMES = 12

        /** Maximum number of simultaneous live particles to cap memory usage. */
        private const val MAX_PARTICLES = 120

        /** Range of possible initial upward speeds in pixels per frame. */
        private const val PARTICLE_SPEED_MIN = 0.15f
        private const val PARTICLE_SPEED_RANGE = 0.35f

        /**
         * Alpha multiplier applied each frame.
         * At 60 fps a particle reaches near-zero opacity after ~130 frames (~2.2 s).
         */
        private const val PARTICLE_FADE_RATE = 0.975f

        /** Per-frame horizontal velocity multiplier — simulates air resistance. */
        private const val PARTICLE_DRAG = 0.93f

        /**
         * Per-frame vertical speed multiplier.
         * Very close to 1.0 keeps the ascent almost constant, like warm ash buoyed by heat.
         */
        private const val PARTICLE_VERTICAL_DECAY = 0.9985f
    }
}
