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
import app.simple.felicity.decorations.views.FelicityVisualizer.Companion.WAVE_SECONDARY_SCALE
import app.simple.felicity.manager.SharedPreferences.registerListener
import app.simple.felicity.manager.SharedPreferences.unregisterListener
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A custom audio spectrum visualizer that supports two rendering modes:
 * vertical frequency bars ([VisualizerMode.BARS]) and a fluid water-wave fill
 * ([VisualizerMode.WAVE]).
 *
 * In bars mode, [BAND_COUNT] vertical bars are animated in real time from bass
 * (left) to treble (right) with peak-hold caps and an optional ash-particle emitter.
 * In wave mode, the same spectrum data drives a smooth filled-wave curve anchored
 * at the view's physical bottom using midpoint quadratic Bézier interpolation.
 *
 * [maxRenderHeightFraction] (0.0–1.0) controls how much of the view height is
 * available for bar or wave growth. Bars always grow upward from the physical
 * view bottom, so this is a pure height cap rather than a start-point offset.
 *
 * Incoming RMS bands are normalized with a fast-attack/slow-release AGC, passed
 * through a square-root perceptual curve, and delivered after a configurable
 * latency-compensation delay so the display stays in sync with audible output.
 *
 * Colors default to the current theme accent and update automatically on accent
 * changes. The active rendering mode and enabled state are read from
 * [PlayerPreferences] and react to live preference changes while the view is attached.
 *
 * @author Hamza417
 */
class FelicityVisualizer @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), ThemeChangedListener {

    // ── Rendering mode ────────────────────────────────────────────────────────

    /** Selects whether bars or a fluid wave is rendered. */
    enum class VisualizerMode {
        /** Vertical frequency bar spectrum with peak-hold caps and optional particles. */
        BARS,

        /** Filled smooth-wave shape derived from the frequency spectrum, resembling water waves. */
        WAVE
    }

    /**
     * Active rendering mode. Defaults to [VisualizerMode.BARS].
     * Changing this while attached triggers an immediate redraw.
     */
    var mode: VisualizerMode = VisualizerMode.BARS
        set(value) {
            field = value
            invalidate()
        }

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
     * Holds normalized band snapshots with a [SystemClock.elapsedRealtime] deadline.
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

    /** Paint for the frequency bars and the primary wave fill; shader is rebuilt when width or colors change. */
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /**
     * Paint for the secondary (depth) wave layer in [VisualizerMode.WAVE].
     * Shares the same horizontal gradient shader as [barPaint] but renders at
     * roughly 47% opacity to simulate visual depth behind the primary wave.
     */
    private val secondaryWavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 120
    }

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
     * Listens for live SharedPreferences changes.
     * Handles the app corner-radius slider, the visualizer mode switch, and the
     * visualizer enabled toggle so the view self-manages its own rendering state
     * without requiring external coordination from the host fragment.
     */
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            AppearancePreferences.APP_CORNER_RADIUS -> {
                cornerRadiusFraction = computeCornerFraction()
                invalidate()
            }
            PlayerPreferences.VISUALIZER_MODE -> {
                mode = if (PlayerPreferences.getVisualizerMode() == PlayerPreferences.VISUALIZER_MODE_WAVE) {
                    VisualizerMode.WAVE
                } else {
                    VisualizerMode.BARS
                }
            }
            PlayerPreferences.VISUALIZER_ENABLED -> {
                visibility = if (PlayerPreferences.isVisualizerEnabled()) VISIBLE else GONE
            }
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
     * Indices 0–3 carry the top-left and top-right radii; indices 4–7 are always 0
     * so the base corners of every bar remain flat regardless of the corner preference.
     */
    private val barCornerRadii = FloatArray(8)

    /** Reusable [Path] for rendering both the primary and secondary wave shapes. */
    private val wavePathBuffer = Path()

    // ── Render height fraction ────────────────────────────────────────────────

    /**
     * Maximum bar or wave render height expressed as a fraction of the view's
     * measured height, in the range [0.0..1.0].
     *
     * A value of `1.0` (the default) allows bars and waves to grow up to the full
     * view height. A value of `0.5` limits them to the lower 50% of the view.
     *
     * Bars always grow upward from the physical bottom of the view; this property
     * caps only how tall they can become, so it is a pure height-limit rather than
     * a start-point offset. The wave fill is similarly anchored at the view's bottom.
     *
     * Can also be configured via the `vizMaxRenderHeight` XML attribute.
     */
    var maxRenderHeightFraction: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
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
                maxRenderHeightFraction = getFloat(R.styleable.FelicityVisualizer_vizMaxRenderHeight, 1f)
                particlesEnabled = getBoolean(R.styleable.FelicityVisualizer_vizParticlesEnabled, false)
                val modeOrdinal = getInt(R.styleable.FelicityVisualizer_vizMode, 0)
                mode = if (modeOrdinal == 1) VisualizerMode.WAVE else VisualizerMode.BARS
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
        gradient = null
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

        val newGradient = LinearGradient(
                0f, 0f, w.toFloat(), 0f,
                colors, positions,
                Shader.TileMode.CLAMP
        )
        gradient = newGradient
        barPaint.shader = newGradient
        secondaryWavePaint.shader = newGradient
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        if (cachedWidth != width) rebuildGradient(width)

        drainQueueIntoTargets()

        // Maximum pixel height bars or waves may reach. They always grow upward from
        // viewBottom, so this is purely a height cap, not a y-coordinate offset.
        val maxBarHeight = height * maxRenderHeightFraction.coerceIn(0f, 1f)
        val viewBottom = height.toFloat()

        val slotWidth = width.toFloat() / BAND_COUNT
        val gapWidth = slotWidth * BAR_GAP_FRACTION
        val barWidth = slotWidth - gapWidth
        val cornerRadius = (cornerRadiusFraction * barWidth).coerceIn(0f, barWidth / 2f)
        val capPillHeight = (barWidth * 0.3f).coerceAtLeast(3f)

        // Smooth every band toward its current target regardless of rendering mode.
        var stillMoving = delayQueue.isNotEmpty()
        for (i in 0 until BAND_COUNT) {
            val target = targetBands[i]
            val current = currentBands[i]
            val speed = if (target > current) RISE_SPEED else FALL_SPEED
            val next = (current + (target - current) * speed).coerceIn(0f, 1f)
            currentBands[i] = next
            if (abs(next - current) > IDLE_THRESHOLD) stillMoving = true
        }

        stillMoving = when (mode) {
            VisualizerMode.BARS -> drawBars(
                    canvas, slotWidth, gapWidth, barWidth,
                    cornerRadius, capPillHeight, maxBarHeight, viewBottom, stillMoving
            )
            VisualizerMode.WAVE -> drawWave(canvas, maxBarHeight, viewBottom, stillMoving)
        }

        if (stillMoving) {
            postInvalidateOnAnimation()
        } else {
            animating = false
        }
    }

    /**
     * Renders the bar-spectrum visualization including peak-hold caps and the optional
     * ash-particle emitter. Bars are anchored at [viewBottom] and grow upward by at
     * most [maxBarHeight] pixels. Returns whether any element is still animating.
     */
    private fun drawBars(
            canvas: Canvas,
            slotWidth: Float,
            gapWidth: Float,
            barWidth: Float,
            cornerRadius: Float,
            capPillHeight: Float,
            maxBarHeight: Float,
            viewBottom: Float,
            initialMoving: Boolean
    ): Boolean {
        // Populate the top-only corner radii array once per frame.
        barCornerRadii[0] = cornerRadius; barCornerRadii[1] = cornerRadius
        barCornerRadii[2] = cornerRadius; barCornerRadii[3] = cornerRadius
        barCornerRadii[4] = 0f; barCornerRadii[5] = 0f
        barCornerRadii[6] = 0f; barCornerRadii[7] = 0f

        var stillMoving = initialMoving

        for (i in 0 until BAND_COUNT) {
            val next = currentBands[i]

            // Bar grows from viewBottom upward; maxBarHeight caps how tall it can be.
            val barHeightPx = (next * maxBarHeight).coerceIn(MIN_BAR_PX, maxBarHeight)
            val left = i * slotWidth + gapWidth / 2f
            val right = left + barWidth

            drawRect.set(left, viewBottom - barHeightPx, right, viewBottom)
            barPath.reset()
            barPath.addRoundRect(drawRect, barCornerRadii, Path.Direction.CW)
            canvas.drawPath(barPath, barPaint)

            if (particlesEnabled && particleCooldowns[i] > 0) {
                particleCooldowns[i]--
            }

            // Peak cap — pushed up by the bar, then falls under gravity.
            if (next >= peakBands[i]) {
                peakBands[i] = next
                peakVelocities[i] = 0f
                peakHoldCounters[i] = PEAK_HOLD_FRAMES
                if (particlesEnabled && particleCooldowns[i] <= 0 && next > PARTICLE_MIN_HEIGHT) {
                    emitParticle(left + barWidth / 2f, viewBottom - next * maxBarHeight)
                    particleCooldowns[i] = PARTICLE_COOLDOWN_FRAMES
                }
            } else {
                if (peakHoldCounters[i] > 0) {
                    peakHoldCounters[i]--
                } else {
                    peakVelocities[i] = (peakVelocities[i] + GRAVITY).coerceAtMost(MAX_PEAK_VELOCITY)
                    peakBands[i] = (peakBands[i] - peakVelocities[i]).coerceAtLeast(0f)
                }
                if (peakBands[i] > IDLE_THRESHOLD) stillMoving = true
                val twoThirdsPeak = peakBands[i] * (2f / 3f)
                if (particlesEnabled && particleCooldowns[i] <= 0 &&
                        next >= twoThirdsPeak && next > PARTICLE_MIN_HEIGHT
                ) {
                    emitParticle(left + barWidth / 2f, viewBottom - next * maxBarHeight)
                    particleCooldowns[i] = PARTICLE_COOLDOWN_FRAMES
                }
            }

            if (peakBands[i] > 0.02f) {
                val peakY = viewBottom - peakBands[i] * maxBarHeight
                drawRect.set(
                        left,
                        (peakY - capPillHeight / 2f).coerceAtLeast(0f),
                        right,
                        (peakY + capPillHeight / 2f).coerceAtMost(viewBottom)
                )
                canvas.drawRoundRect(drawRect, cornerRadius, cornerRadius, capPaint)
            }
        }

        // Update physics and render all live ash particles.
        if (particlesEnabled && particles.isNotEmpty()) {
            val iter = particles.iterator()
            while (iter.hasNext()) {
                val p = iter.next()
                p.vx += (Random.nextFloat() - 0.5f) * p.turbulence
                p.vx *= PARTICLE_DRAG
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

        return stillMoving
    }

    /**
     * Renders the fluid water-wave visualization using two stacked filled paths:
     * a primary wave at full opacity and a secondary wave at [WAVE_SECONDARY_SCALE]
     * amplitude with reduced opacity, creating a layered water-depth effect.
     *
     * The wave outline is built with midpoint quadratic Bézier interpolation so
     * the curve passes smoothly through every band's amplitude without requiring
     * a cubic spline solve. Both layers share the same horizontal accent gradient
     * as the bars mode for visual consistency. The wave fill is always anchored
     * at [viewBottom] and limited to [maxBarHeight] pixels upward.
     *
     * Returns whether any band is still animating.
     */
    private fun drawWave(
            canvas: Canvas,
            maxBarHeight: Float,
            viewBottom: Float,
            initialMoving: Boolean
    ): Boolean {
        val slotWidth = width.toFloat() / BAND_COUNT

        // Pre-compute x centers and y amplitude positions for the primary wave.
        val bx = FloatArray(BAND_COUNT) { i -> (i + 0.5f) * slotWidth }
        val by = FloatArray(BAND_COUNT) { i -> viewBottom - currentBands[i] * maxBarHeight }

        // Secondary wave sits at a smaller amplitude to create a foreground depth layer.
        val bySecondary = FloatArray(BAND_COUNT) { i ->
            viewBottom - currentBands[i] * maxBarHeight * WAVE_SECONDARY_SCALE
        }

        // Draw primary wave fill.
        buildWavePath(wavePathBuffer, bx, by, viewBottom)
        canvas.drawPath(wavePathBuffer, barPaint)

        // Draw secondary (depth) wave fill — shares gradient shader, lower alpha.
        buildWavePath(wavePathBuffer, bx, bySecondary, viewBottom)
        canvas.drawPath(wavePathBuffer, secondaryWavePaint)

        // Determine if any band is still animating.
        var stillMoving = initialMoving
        for (i in 0 until BAND_COUNT) {
            if (currentBands[i] > IDLE_THRESHOLD) {
                stillMoving = true
                break
            }
        }

        return stillMoving
    }

    /**
     * Populates [path] with a filled wave shape whose top edge passes smoothly
     * through the provided [by] y-coordinates at the corresponding [bx] x-centers.
     *
     * The left and right edge gaps (between the view boundary and the outermost
     * band centers) are filled with a vertical line at the respective edge band's
     * amplitude so the wave always covers the full view width flush to both sides.
     *
     * Each interior segment is drawn with a midpoint quadratic Bézier that uses the
     * current band as a control point and the midpoint between consecutive band
     * centers as the anchor — this gives C1-continuous smooth curves through all data
     * points without any matrix solve.
     *
     * @param path       Reusable [Path] that is reset before use.
     * @param bx         X-center coordinate for each band (length = [BAND_COUNT]).
     * @param by         Y-coordinate (top of wave) for each band (length = [BAND_COUNT]).
     * @param viewBottom Y-coordinate of the view's physical bottom edge.
     */
    private fun buildWavePath(path: Path, bx: FloatArray, by: FloatArray, viewBottom: Float) {
        path.reset()

        // Start at the bottom-left corner and rise to the first band's amplitude.
        path.moveTo(0f, viewBottom)
        path.lineTo(0f, by[0])

        // Smooth curve: each quadTo uses the current band center as a control point
        // and the midpoint between this and the next band center as the end point.
        // This guarantees C1 continuity (matching tangents) at every junction.
        for (i in 0 until BAND_COUNT - 1) {
            val midX = (bx[i] + bx[i + 1]) / 2f
            val midY = (by[i] + by[i + 1]) / 2f
            path.quadTo(bx[i], by[i], midX, midY)
        }

        // Connect the last band center to the right edge, then close the fill area.
        path.lineTo(bx[BAND_COUNT - 1], by[BAND_COUNT - 1])
        path.lineTo(width.toFloat(), by[BAND_COUNT - 1])
        path.lineTo(width.toFloat(), viewBottom)
        path.close()
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
            cornerRadiusFraction = computeCornerFraction()
            // Restore persisted mode and enabled state immediately.
            mode = if (PlayerPreferences.getVisualizerMode() == PlayerPreferences.VISUALIZER_MODE_WAVE) {
                VisualizerMode.WAVE
            } else {
                VisualizerMode.BARS
            }
            visibility = if (PlayerPreferences.isVisualizerEnabled()) VISIBLE else GONE
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
        super.onAccentChanged(accent)
        barColors = buildAccentColors()
        rebuildGradient(width)
        capPaint.color = accent.primaryAccentColor
        invalidate()
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        // The bars use accent colors, not theme background colors — no action needed.
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Computes the corner radius as a fraction of bar width from the current app preference.
     *
     * [AppearancePreferences.getCornerRadius] returns a value in [1..80]; dividing by
     * [AppearancePreferences.MAX_CORNER_RADIUS] normalizes it to (0..1), which is then
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
        private const val FALL_SPEED = 0.04f

        /**
         * Gravity added to a cap's downward velocity each frame while it is falling.
         * Normalized units (0..1 per frame²), so the cap accelerates like a physical object.
         */
        private const val GRAVITY = 0.0018f

        /** Terminal velocity cap for the falling peak pill to prevent it from teleporting. */
        private const val MAX_PEAK_VELOCITY = 0.04f

        /** Frames the peak cap holds its highest position before gravity starts. */
        private const val PEAK_HOLD_FRAMES = 60

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
         * FFT-leakage noise (sub-1.5% of peak) is suppressed.
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
         * Scale factor applied to the normalized app corner radius when mapping it to bar width.
         * Values above 1.0 produce pill-shaped bars at maximum app corner radius.
         */
        private const val CORNER_RADIUS_FACTOR = 1.5f

        /** Minimum bar height (target) for emitting a particle. */
        private const val PARTICLE_MIN_HEIGHT = 0.15f

        /** Particle emission cooldown in frames to prevent flooding. */
        private const val PARTICLE_COOLDOWN_FRAMES = 72

        /** Maximum number of simultaneous live particles to cap memory usage. */
        const val MAX_PARTICLES = 120

        /** Range of possible initial upward speeds in pixels per frame. */
        private const val PARTICLE_SPEED_MIN = 0.15f
        private const val PARTICLE_SPEED_RANGE = 0.85f

        /**
         * Alpha multiplier applied each frame.
         * At 60 fps a particle reaches near-zero opacity after ~130 frames (~2.2 s).
         */
        const val PARTICLE_FADE_RATE = 0.975f

        /** Per-frame horizontal velocity multiplier — simulates air resistance. */
        private const val PARTICLE_DRAG = 0.93f

        /**
         * Per-frame vertical speed multiplier.
         * Very close to 1.0 keeps the ascent almost constant, like warm ash buoyed by heat.
         */
        private const val PARTICLE_VERTICAL_DECAY = 0.9985f

        /**
         * Amplitude scale factor for the secondary (depth) wave layer in [VisualizerMode.WAVE].
         * At 0.6 the background wave reaches 60% of the primary wave's height, providing
         * a visually distinct depth layer that reinforces the water-wave appearance.
         */
        private const val WAVE_SECONDARY_SCALE = 0.6f
    }
}
