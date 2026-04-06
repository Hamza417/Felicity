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
import android.util.AttributeSet
import android.view.View
import androidx.core.content.withStyledAttributes
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.views.FelicityVisualizer.Companion.BAND_COUNT
import app.simple.felicity.decorations.views.FelicityVisualizer.Companion.CORNER_RADIUS_FACTOR
import app.simple.felicity.decorations.views.FelicityVisualizer.Companion.WAVE_SECONDARY_SCALE
import app.simple.felicity.manager.SharedPreferences.registerListener
import app.simple.felicity.manager.SharedPreferences.unregisterListener
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.preferences.VisualizerPreferences
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A custom audio spectrum visualizer that supports two rendering modes —
 * vertical frequency bars ([VisualizerMode.BARS]) and a fluid water-wave fill
 * ([VisualizerMode.WAVE]) — combined with four growth directions
 * ([VisualizerDirection]).
 *
 * Audio data arrives via the lock-free twin-buffer system: [bufferA] and [bufferB] are
 * pre-allocated globally on this class, and [isBufferAFront] is an [AtomicBoolean] that
 * tracks which buffer the UI is currently reading. The audio thread writes FFT-derived
 * band magnitudes into the back buffer via JNI, atomically promotes it to front, and
 * calls [postInvalidate] — no coroutines, no SharedFlow, and no intermediate queues.
 *
 * In [onDraw], the front buffer is read once and the AGC (fast-attack / slow-release) is
 * applied inline to derive per-band targets. Those targets are then smoothed into
 * [currentBands] with configurable rise/fall speeds before being rendered.
 *
 * In bars mode, [BAND_COUNT] bars are animated in real time with peak-hold caps and an
 * optional ash-particle emitter. The bars grow from the anchor edge toward the opposite
 * edge according to [direction]. In wave mode, the same spectrum data drives a smooth
 * filled-wave curve anchored at the anchor edge.
 *
 * [maxRenderHeightFraction] (0.0–1.0) controls how much of the primary growth dimension
 * (height for vertical directions, width for horizontal) is available for bar or wave growth.
 *
 * Colors default to the current theme accent and update automatically on accent changes.
 * The active rendering mode, direction, and enabled state are read from
 * [PlayerPreferences] / [VisualizerPreferences] and react to live preference changes
 * while the view is attached.
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
     * Determines the edge from which bars and waves grow.
     *
     * The gradient always runs along the band-distribution axis so that the color sweep
     * remains perceptually consistent regardless of the chosen direction.
     */
    enum class VisualizerDirection {
        /** Bars and waves emerge from the bottom edge and grow upward (default). */
        BOTTOM_TO_TOP,

        /** Bars and waves emerge from the top edge and grow downward. */
        TOP_TO_BOTTOM,

        /** Bars and waves emerge from the left edge and grow rightward. */
        LEFT_TO_RIGHT,

        /** Bars and waves emerge from the right edge and grow leftward. */
        RIGHT_TO_LEFT
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

    /**
     * Active growth direction. Defaults to [VisualizerDirection.BOTTOM_TO_TOP].
     *
     * Changing this rebuilds the gradient shader so that it always runs along the
     * band-distribution axis (horizontal for vertical directions, vertical for
     * horizontal directions), then triggers an immediate redraw.
     */
    var direction: VisualizerDirection = VisualizerDirection.BOTTOM_TO_TOP
        set(value) {
            field = value
            gradient = null
            rebuildGradient(width, height)
            invalidate()
        }

    // ── Twin-buffer state (written by audio thread, read by UI thread) ────────

    /**
     * Buffer A of the lock-free twin-buffer pair.
     *
     * The audio thread writes directly into whichever of [bufferA] / [bufferB] is
     * currently the back buffer (determined by [isBufferAFront]). The UI thread reads
     * only from the front buffer inside [onDraw]. No synchronization primitives are
     * needed because the [AtomicBoolean] swap guarantees that writer and reader never
     * touch the same buffer simultaneously.
     */
    val bufferA = FloatArray(BAND_COUNT)

    /**
     * Buffer B of the lock-free twin-buffer pair.
     * See [bufferA] for the full protocol description.
     */
    val bufferB = FloatArray(BAND_COUNT)

    /**
     * Tracks which buffer the UI is currently reading.
     * When true, [bufferA] is the front (readable) buffer and [bufferB] is the back
     * (writable) buffer — and vice versa when false. Toggled atomically by the audio
     * thread after each successful write.
     */
    val isBufferAFront = AtomicBoolean(true)

    // ── Band state ────────────────────────────────────────────────────────────

    /** Current smoothed magnitude for each band (the value actually rendered). */
    private val currentBands = FloatArray(BAND_COUNT)

    /** Highest magnitude reached; the peak cap tracks this value. */
    private val peakBands = FloatArray(BAND_COUNT)

    /** Downward velocity of each peak cap in normalized units per frame (gravity accumulates here). */
    private val peakVelocities = FloatArray(BAND_COUNT)

    /** Remaining hold frames before gravity starts pulling the cap down. */
    private val peakHoldCounters = IntArray(BAND_COUNT)

    // ── AGC ───────────────────────────────────────────────────────────────────

    /**
     * Smoothed peak across all bands used as the AGC reference.
     * Fast attack, slow release — updated once per [onDraw] call from the front buffer.
     */
    private var smoothedMax = MIN_SMOOTHED_MAX

    // ── Drawing ───────────────────────────────────────────────────────────────

    /** Paint for the frequency bars and the primary wave fill; shader is rebuilt when size or colors change. */
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /**
     * Paint for the secondary (depth) wave layer in [VisualizerMode.WAVE].
     * Shares the same gradient shader as [barPaint] but renders at roughly 47% opacity
     * to simulate visual depth behind the primary wave.
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
     * Corner radius expressed as a fraction of one bar's cross-section width [0..0.5].
     * Derived from [AppearancePreferences.getCornerRadius] scaled by [CORNER_RADIUS_FACTOR]
     * and updated live via [prefsListener].
     */
    private var cornerRadiusFraction = computeCornerFraction()

    /**
     * Listens for live SharedPreferences changes.
     * Handles the app corner-radius slider, the visualizer mode switch, the direction
     * setting, and the visualizer enabled toggle so the view self-manages its rendering state.
     */
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            AppearancePreferences.APP_CORNER_RADIUS -> {
                cornerRadiusFraction = computeCornerFraction()
                invalidate()
            }
            VisualizerPreferences.VISUALIZER_TYPE -> {
                applyModePreferences()
            }
            PlayerPreferences.VISUALIZER_ENABLED -> {
                visibility = if (PlayerPreferences.isVisualizerEnabled()) VISIBLE else GONE
            }
            VisualizerPreferences.PARTICLES_ENABLED -> {
                particlesEnabled = VisualizerPreferences.areParticlesEnabled()
            }
        }
    }

    // ── Particle system ───────────────────────────────────────────────────────

    /**
     * Controls whether the ash-particle emitter is active.
     * All live particles are cleared immediately when set to `false`.
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

    /**
     * A single floating particle belonging to the ash/snow emitter.
     * All fields are mutable so the particle can be updated in-place each frame
     * without additional heap allocation.
     */
    private class Particle {
        var x: Float = 0f
        var y: Float = 0f
        var vx: Float = 0f
        var vy: Float = 0f
        var alpha: Float = 1f
        var radius: Float = 2f
        var turbulence: Float = 0.2f
    }

    // ── Bar path (growing-end rounded corners) ────────────────────────────────

    /** Reusable [Path] for rendering bars with rounded growing-end corners and a flat anchor base. */
    private val barPath = Path()

    /**
     * Eight-element radii array for [Path.addRoundRect].
     * The four indices corresponding to the growing end (top for [VisualizerDirection.BOTTOM_TO_TOP],
     * bottom for [VisualizerDirection.TOP_TO_BOTTOM], right for [VisualizerDirection.LEFT_TO_RIGHT],
     * left for [VisualizerDirection.RIGHT_TO_LEFT]) carry the active corner radius; the anchor-end
     * indices are always 0 so bar bases stay flat.
     */
    private val barCornerRadii = FloatArray(8)

    /** Reusable [Path] for rendering both the primary and secondary wave shapes. */
    private val wavePathBuffer = Path()

    // ── Render height fraction ────────────────────────────────────────────────

    /**
     * Maximum bar or wave render length expressed as a fraction of the view's primary
     * growth dimension (height for [VisualizerDirection.BOTTOM_TO_TOP] /
     * [VisualizerDirection.TOP_TO_BOTTOM], width for [VisualizerDirection.LEFT_TO_RIGHT] /
     * [VisualizerDirection.RIGHT_TO_LEFT]), in the range [0.0..1.0].
     *
     * Can also be configured via the `vizMaxRenderHeight` XML attribute.
     */
    var maxRenderHeightFraction: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
        setLayerType(LAYER_TYPE_HARDWARE, null)
        if (!isInEditMode && attrs != null) {
            context.withStyledAttributes(attrs, R.styleable.FelicityVisualizer, defStyleAttr, 0) {
                maxRenderHeightFraction = getFloat(R.styleable.FelicityVisualizer_vizMaxRenderHeight, 1f)
                particlesEnabled = getBoolean(R.styleable.FelicityVisualizer_vizParticlesEnabled, false)
                val modeOrdinal = getInt(R.styleable.FelicityVisualizer_vizMode, 0)
                mode = if (modeOrdinal == 1) VisualizerMode.WAVE else VisualizerMode.BARS
                direction = when (getInt(R.styleable.FelicityVisualizer_vizDirection, 0)) {
                    1 -> VisualizerDirection.TOP_TO_BOTTOM
                    2 -> VisualizerDirection.LEFT_TO_RIGHT
                    3 -> VisualizerDirection.RIGHT_TO_LEFT
                    else -> VisualizerDirection.BOTTOM_TO_TOP
                }
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Delivers a new spectrum snapshot via the legacy (non-direct) path.
     *
     * Writes [bands] directly into the current back buffer, atomically promotes it
     * to front, and calls [postInvalidate]. The AGC and normalization are applied
     * lazily in [onDraw] on the next frame, keeping this call allocation-free.
     *
     * Prefer the direct twin-buffer path ([bufferA], [bufferB], [isBufferAFront])
     * for zero-overhead audio-thread writes. This method is retained for any caller
     * that cannot participate in the JNI direct path.
     *
     * @param bands Raw FFT-derived band magnitudes in any positive float scale.
     *              Length should equal [BAND_COUNT]; extra elements are silently ignored.
     */
    fun setBands(bands: FloatArray) {
        val len = minOf(bands.size, BAND_COUNT)
        val back = if (isBufferAFront.get()) bufferB else bufferA
        System.arraycopy(bands, 0, back, 0, len)
        if (len < BAND_COUNT) back.fill(0f, len, BAND_COUNT)
        isBufferAFront.set(!isBufferAFront.get())
        postInvalidate()
    }

    /**
     * Replaces the bar gradient color stops.
     *
     * @param colors ARGB color integers for the gradient stops (at least two).
     *               Pass an empty array to revert to the current theme accent colors.
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

    /** Animates all bars and peaks down to zero by zeroing the back buffer and swapping. */
    fun clear() {
        val back = if (isBufferAFront.get()) bufferB else bufferA
        back.fill(0f)
        isBufferAFront.set(!isBufferAFront.get())
        postInvalidate()
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

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
        rebuildGradient(w, h)
    }

    /**
     * Rebuilds the linear gradient shader, orienting it along the band-distribution axis.
     *
     * For [VisualizerDirection.BOTTOM_TO_TOP] and [VisualizerDirection.TOP_TO_BOTTOM] the
     * bands run left-to-right, so the gradient is horizontal. For
     * [VisualizerDirection.LEFT_TO_RIGHT] and [VisualizerDirection.RIGHT_TO_LEFT] the bands
     * run top-to-bottom, so the gradient is vertical.
     *
     * @param w Current view width in pixels.
     * @param h Current view height in pixels.
     */
    private fun rebuildGradient(w: Int, h: Int = height) {
        if (w == 0 || h == 0) return
        cachedWidth = w

        val colors = barColors
        val positions = FloatArray(colors.size) { i -> i.toFloat() / (colors.size - 1) }

        val newGradient = when (direction) {
            VisualizerDirection.LEFT_TO_RIGHT, VisualizerDirection.RIGHT_TO_LEFT ->
                LinearGradient(0f, 0f, 0f, h.toFloat(), colors, positions, Shader.TileMode.CLAMP)
            else ->
                LinearGradient(0f, 0f, w.toFloat(), 0f, colors, positions, Shader.TileMode.CLAMP)
        }
        gradient = newGradient
        barPaint.shader = newGradient
        secondaryWavePaint.shader = newGradient
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        if (cachedWidth != width || gradient == null) rebuildGradient(width, height)

        // Read the current front buffer once to keep the snapshot consistent across the
        // whole frame even if the audio thread swaps buffers mid-draw.
        val frontBuffer = if (isBufferAFront.get()) bufferA else bufferB

        // Fast-attack / slow-release AGC derived from the front buffer.
        var frameMax = MIN_SMOOTHED_MAX
        for (i in 0 until BAND_COUNT) {
            if (frontBuffer[i] > frameMax) frameMax = frontBuffer[i]
        }
        smoothedMax = if (frameMax > smoothedMax) {
            smoothedMax + (frameMax - smoothedMax) * AGC_ATTACK
        } else {
            (smoothedMax + (frameMax - smoothedMax) * AGC_RELEASE).coerceAtLeast(MIN_SMOOTHED_MAX)
        }
        val invMax = 1f / smoothedMax

        // Smooth every band toward the AGC-normalized front-buffer target.
        var stillMoving = false
        for (i in 0 until BAND_COUNT) {
            val linear = (frontBuffer[i] * invMax).coerceIn(0f, 1f)
            // Noise floor gate: suppress FFT leakage below 1.5% of the normalized peak.
            val target = if (linear < NOISE_FLOOR) 0f else sqrt(linear)
            val current = currentBands[i]
            val speed = if (target > current) RISE_SPEED else FALL_SPEED
            val next = (current + (target - current) * speed).coerceIn(0f, 1f)
            currentBands[i] = next
            if (abs(next - current) > IDLE_THRESHOLD) stillMoving = true
        }

        stillMoving = when (mode) {
            VisualizerMode.BARS -> drawBars(canvas, stillMoving)
            VisualizerMode.WAVE -> drawWave(canvas, stillMoving)
        }

        if (stillMoving) {
            postInvalidateOnAnimation()
        }
    }

    /**
     * Renders the bar-spectrum visualization including peak-hold caps and the optional
     * ash-particle emitter. All geometry, corner-rounding, cap positions, particle
     * emission points, and particle physics adapt to the active [direction].
     *
     * Returns whether any element is still animating.
     *
     * @param canvas       Canvas to draw into.
     * @param initialMoving Whether any band was already considered animating before this call.
     */
    private fun drawBars(canvas: Canvas, initialMoving: Boolean): Boolean {
        val isHorizontal = direction == VisualizerDirection.LEFT_TO_RIGHT ||
                direction == VisualizerDirection.RIGHT_TO_LEFT

        // Dimension computations adapt to whether bands run along the horizontal or vertical axis.
        val slotDim: Float
        val maxBarLength: Float
        if (isHorizontal) {
            slotDim = height.toFloat() / BAND_COUNT
            maxBarLength = width * maxRenderHeightFraction.coerceIn(0f, 1f)
        } else {
            slotDim = width.toFloat() / BAND_COUNT
            maxBarLength = height * maxRenderHeightFraction.coerceIn(0f, 1f)
        }
        val gapDim = slotDim * BAR_GAP_FRACTION
        val barThickness = slotDim - gapDim
        val cornerRadius = (cornerRadiusFraction * barThickness).coerceIn(0f, barThickness / 2f)
        val capPillDim = (barThickness * 0.3f).coerceAtLeast(3f)
        val viewBottom = height.toFloat()
        val viewRight = width.toFloat()

        // Populate corner radii so only the growing end of each bar is rounded.
        // Indices: 0,1 = top-left; 2,3 = top-right; 4,5 = bottom-right; 6,7 = bottom-left.
        when (direction) {
            VisualizerDirection.BOTTOM_TO_TOP -> {
                barCornerRadii[0] = cornerRadius; barCornerRadii[1] = cornerRadius  // top-left
                barCornerRadii[2] = cornerRadius; barCornerRadii[3] = cornerRadius  // top-right
                barCornerRadii[4] = 0f; barCornerRadii[5] = 0f            // bottom-right
                barCornerRadii[6] = 0f; barCornerRadii[7] = 0f            // bottom-left
            }
            VisualizerDirection.TOP_TO_BOTTOM -> {
                barCornerRadii[0] = 0f; barCornerRadii[1] = 0f            // top-left
                barCornerRadii[2] = 0f; barCornerRadii[3] = 0f            // top-right
                barCornerRadii[4] = cornerRadius; barCornerRadii[5] = cornerRadius  // bottom-right
                barCornerRadii[6] = cornerRadius; barCornerRadii[7] = cornerRadius  // bottom-left
            }
            VisualizerDirection.LEFT_TO_RIGHT -> {
                barCornerRadii[0] = 0f; barCornerRadii[1] = 0f            // top-left
                barCornerRadii[2] = cornerRadius; barCornerRadii[3] = cornerRadius  // top-right
                barCornerRadii[4] = cornerRadius; barCornerRadii[5] = cornerRadius  // bottom-right
                barCornerRadii[6] = 0f; barCornerRadii[7] = 0f            // bottom-left
            }
            VisualizerDirection.RIGHT_TO_LEFT -> {
                barCornerRadii[0] = cornerRadius; barCornerRadii[1] = cornerRadius  // top-left
                barCornerRadii[2] = 0f; barCornerRadii[3] = 0f            // top-right
                barCornerRadii[4] = 0f; barCornerRadii[5] = 0f            // bottom-right
                barCornerRadii[6] = cornerRadius; barCornerRadii[7] = cornerRadius  // bottom-left
            }
        }

        var stillMoving = initialMoving

        for (i in 0 until BAND_COUNT) {
            val next = currentBands[i]
            val barLengthPx = (next * maxBarLength).coerceIn(MIN_BAR_PX, maxBarLength)
            val slotOffset = i * slotDim + gapDim / 2f
            val barCenter = slotOffset + barThickness / 2f

            // Set bar rect coordinates based on the growth direction.
            when (direction) {
                VisualizerDirection.BOTTOM_TO_TOP ->
                    drawRect.set(slotOffset, viewBottom - barLengthPx, slotOffset + barThickness, viewBottom)
                VisualizerDirection.TOP_TO_BOTTOM ->
                    drawRect.set(slotOffset, 0f, slotOffset + barThickness, barLengthPx)
                VisualizerDirection.LEFT_TO_RIGHT ->
                    drawRect.set(0f, slotOffset, barLengthPx, slotOffset + barThickness)
                VisualizerDirection.RIGHT_TO_LEFT ->
                    drawRect.set(viewRight - barLengthPx, slotOffset, viewRight, slotOffset + barThickness)
            }

            barPath.reset()
            barPath.addRoundRect(drawRect, barCornerRadii, Path.Direction.CW)
            canvas.drawPath(barPath, barPaint)

            if (particlesEnabled && particleCooldowns[i] > 0) particleCooldowns[i]--

            // Tip coordinate (the bar's growing end in the primary growth axis).
            val tipCoord = when (direction) {
                VisualizerDirection.BOTTOM_TO_TOP -> viewBottom - next * maxBarLength
                VisualizerDirection.TOP_TO_BOTTOM -> next * maxBarLength
                VisualizerDirection.LEFT_TO_RIGHT -> next * maxBarLength
                VisualizerDirection.RIGHT_TO_LEFT -> viewRight - next * maxBarLength
            }

            // Peak cap — pushed out by the bar tip, then retreats under gravity.
            if (next >= peakBands[i]) {
                peakBands[i] = next
                peakVelocities[i] = 0f
                peakHoldCounters[i] = PEAK_HOLD_FRAMES
                if (particlesEnabled && particleCooldowns[i] <= 0 && next > PARTICLE_MIN_HEIGHT) {
                    emitParticle(barCenter, tipCoord, isHorizontal)
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
                    emitParticle(barCenter, tipCoord, isHorizontal)
                    particleCooldowns[i] = PARTICLE_COOLDOWN_FRAMES
                }
            }

            // Draw the peak cap pill at the peak position along the growth axis.
            if (peakBands[i] > 0.02f) {
                val peakPos = when (direction) {
                    VisualizerDirection.BOTTOM_TO_TOP -> viewBottom - peakBands[i] * maxBarLength
                    VisualizerDirection.TOP_TO_BOTTOM -> peakBands[i] * maxBarLength
                    VisualizerDirection.LEFT_TO_RIGHT -> peakBands[i] * maxBarLength
                    VisualizerDirection.RIGHT_TO_LEFT -> viewRight - peakBands[i] * maxBarLength
                }
                if (isHorizontal) {
                    drawRect.set(
                            (peakPos - capPillDim / 2f).coerceAtLeast(0f),
                            slotOffset,
                            (peakPos + capPillDim / 2f).coerceAtMost(viewRight),
                            slotOffset + barThickness
                    )
                } else {
                    drawRect.set(
                            slotOffset,
                            (peakPos - capPillDim / 2f).coerceAtLeast(0f),
                            slotOffset + barThickness,
                            (peakPos + capPillDim / 2f).coerceAtMost(viewBottom)
                    )
                }
                canvas.drawRoundRect(drawRect, cornerRadius, cornerRadius, capPaint)
            }
        }

        // Update physics and render all live ash particles.
        if (particlesEnabled && particles.isNotEmpty()) {
            val iter = particles.iterator()
            while (iter.hasNext()) {
                val p = iter.next()
                // For horizontal directions the primary drift is in vx; vy carries turbulence.
                // For vertical directions the primary drift is in vy; vx carries turbulence.
                if (isHorizontal) {
                    p.vy += (Random.nextFloat() - 0.5f) * p.turbulence
                    p.vy *= PARTICLE_DRAG
                    p.vx *= PARTICLE_VERTICAL_DECAY
                } else {
                    p.vx += (Random.nextFloat() - 0.5f) * p.turbulence
                    p.vx *= PARTICLE_DRAG
                    p.vy *= PARTICLE_VERTICAL_DECAY
                }
                p.x += p.vx
                p.y += p.vy
                p.alpha *= PARTICLE_FADE_RATE

                val expired = p.alpha < 0.01f || when (direction) {
                    VisualizerDirection.BOTTOM_TO_TOP -> p.y < -p.radius
                    VisualizerDirection.TOP_TO_BOTTOM -> p.y > viewBottom + p.radius
                    VisualizerDirection.LEFT_TO_RIGHT -> p.x > width + p.radius
                    VisualizerDirection.RIGHT_TO_LEFT -> p.x < -p.radius
                }
                if (expired) {
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
     * Renders the fluid wave visualization using two stacked filled paths.
     *
     * A primary wave is drawn at full opacity and a secondary wave at
     * [WAVE_SECONDARY_SCALE] amplitude with reduced opacity, creating a layered
     * water-depth effect. Both waves are adapted to the active [direction].
     *
     * Returns whether any band is still animating.
     *
     * @param canvas       Canvas to draw into.
     * @param initialMoving Whether any band was already considered animating before this call.
     */
    private fun drawWave(canvas: Canvas, initialMoving: Boolean): Boolean {
        val viewBottom = height.toFloat()
        val viewRight = width.toFloat()

        when (direction) {
            VisualizerDirection.BOTTOM_TO_TOP -> {
                val maxH = height * maxRenderHeightFraction.coerceIn(0f, 1f)
                val slotW = viewRight / BAND_COUNT
                val bx = FloatArray(BAND_COUNT) { i -> (i + 0.5f) * slotW }
                val by = FloatArray(BAND_COUNT) { i -> viewBottom - currentBands[i] * maxH }
                val byS = FloatArray(BAND_COUNT) { i -> viewBottom - currentBands[i] * maxH * WAVE_SECONDARY_SCALE }
                buildWavePath(wavePathBuffer, bx, by, viewBottom)
                canvas.drawPath(wavePathBuffer, barPaint)
                buildWavePath(wavePathBuffer, bx, byS, viewBottom)
                canvas.drawPath(wavePathBuffer, secondaryWavePaint)
            }
            VisualizerDirection.TOP_TO_BOTTOM -> {
                val maxH = height * maxRenderHeightFraction.coerceIn(0f, 1f)
                val slotW = viewRight / BAND_COUNT
                val bx = FloatArray(BAND_COUNT) { i -> (i + 0.5f) * slotW }
                // Wave grows downward from the top; anchorY = 0f.
                val by = FloatArray(BAND_COUNT) { i -> currentBands[i] * maxH }
                val byS = FloatArray(BAND_COUNT) { i -> currentBands[i] * maxH * WAVE_SECONDARY_SCALE }
                buildWavePath(wavePathBuffer, bx, by, 0f)
                canvas.drawPath(wavePathBuffer, barPaint)
                buildWavePath(wavePathBuffer, bx, byS, 0f)
                canvas.drawPath(wavePathBuffer, secondaryWavePaint)
            }
            VisualizerDirection.LEFT_TO_RIGHT -> {
                val maxW = width * maxRenderHeightFraction.coerceIn(0f, 1f)
                val slotH = viewBottom / BAND_COUNT
                val by = FloatArray(BAND_COUNT) { i -> (i + 0.5f) * slotH }
                val bx = FloatArray(BAND_COUNT) { i -> currentBands[i] * maxW }
                val bxS = FloatArray(BAND_COUNT) { i -> currentBands[i] * maxW * WAVE_SECONDARY_SCALE }
                buildWavePathHorizontal(wavePathBuffer, bx, by, 0f, viewBottom)
                canvas.drawPath(wavePathBuffer, barPaint)
                buildWavePathHorizontal(wavePathBuffer, bxS, by, 0f, viewBottom)
                canvas.drawPath(wavePathBuffer, secondaryWavePaint)
            }
            VisualizerDirection.RIGHT_TO_LEFT -> {
                val maxW = width * maxRenderHeightFraction.coerceIn(0f, 1f)
                val slotH = viewBottom / BAND_COUNT
                val by = FloatArray(BAND_COUNT) { i -> (i + 0.5f) * slotH }
                val bx = FloatArray(BAND_COUNT) { i -> viewRight - currentBands[i] * maxW }
                val bxS = FloatArray(BAND_COUNT) { i -> viewRight - currentBands[i] * maxW * WAVE_SECONDARY_SCALE }
                buildWavePathHorizontal(wavePathBuffer, bx, by, viewRight, viewBottom)
                canvas.drawPath(wavePathBuffer, barPaint)
                buildWavePathHorizontal(wavePathBuffer, bxS, by, viewRight, viewBottom)
                canvas.drawPath(wavePathBuffer, secondaryWavePaint)
            }
        }

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
     * Populates [path] with a filled wave shape for vertical growth directions
     * ([VisualizerDirection.BOTTOM_TO_TOP] / [VisualizerDirection.TOP_TO_BOTTOM]).
     *
     * The top edge of the wave passes smoothly through the provided [by] y-coordinates
     * using midpoint quadratic Bézier interpolation.
     *
     * @param path    Reusable [Path] that is reset before use.
     * @param bx      X-center coordinate for each band (length = [BAND_COUNT]).
     * @param by      Y-coordinate of the wave edge for each band (length = [BAND_COUNT]).
     * @param anchorY Y-coordinate of the anchor edge:
     *                [height] for [VisualizerDirection.BOTTOM_TO_TOP],
     *                0 for [VisualizerDirection.TOP_TO_BOTTOM].
     */
    private fun buildWavePath(path: Path, bx: FloatArray, by: FloatArray, anchorY: Float) {
        path.reset()

        // Start at the anchor-edge left corner and move along the left edge to the first band.
        path.moveTo(0f, anchorY)
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
        path.lineTo(width.toFloat(), anchorY)
        path.close()
    }

    /**
     * Populates [path] with a filled wave shape for horizontal growth directions
     * ([VisualizerDirection.LEFT_TO_RIGHT] / [VisualizerDirection.RIGHT_TO_LEFT]).
     *
     * The leading edge of the wave passes smoothly through the provided [bx] x-coordinates
     * using midpoint quadratic Bézier interpolation, with bands distributed vertically.
     *
     * @param path       Reusable [Path] that is reset before use.
     * @param bx         X-coordinate of the wave edge for each band (length = [BAND_COUNT]).
     * @param by         Y-center coordinate for each band (length = [BAND_COUNT]).
     * @param anchorX    X-coordinate of the anchor edge: 0 for [VisualizerDirection.LEFT_TO_RIGHT],
     *                   view width for [VisualizerDirection.RIGHT_TO_LEFT].
     * @param viewBottom Y-coordinate of the view's physical bottom edge.
     */
    private fun buildWavePathHorizontal(
            path: Path,
            bx: FloatArray,
            by: FloatArray,
            anchorX: Float,
            viewBottom: Float
    ) {
        path.reset()

        // Start at the anchor-edge top corner and move along the top edge to the first band.
        path.moveTo(anchorX, 0f)
        path.lineTo(bx[0], 0f)

        for (i in 0 until BAND_COUNT - 1) {
            val midX = (bx[i] + bx[i + 1]) / 2f
            val midY = (by[i] + by[i + 1]) / 2f
            path.quadTo(bx[i], by[i], midX, midY)
        }

        // Connect the last band to the bottom edge, then close along the anchor edge.
        path.lineTo(bx[BAND_COUNT - 1], by[BAND_COUNT - 1])
        path.lineTo(bx[BAND_COUNT - 1], viewBottom)
        path.lineTo(anchorX, viewBottom)
        path.close()
    }

    /**
     * Spawns a new [Particle] at the bar tip.
     *
     * The particle's initial velocity is oriented in the growth direction of the active
     * [direction]: upward for [VisualizerDirection.BOTTOM_TO_TOP], downward for
     * [VisualizerDirection.TOP_TO_BOTTOM], rightward for [VisualizerDirection.LEFT_TO_RIGHT],
     * and leftward for [VisualizerDirection.RIGHT_TO_LEFT]. Turbulent drift is applied
     * perpendicular to the primary velocity each frame in [drawBars].
     *
     * @param crossCoord Position along the band-distribution axis (bar center).
     *                   This is an x-coordinate for vertical directions and a y-coordinate
     *                   for horizontal directions.
     * @param tipCoord   Position of the bar tip along the primary growth axis.
     *                   This is a y-coordinate for vertical directions and an x-coordinate
     *                   for horizontal directions.
     * @param isHorizontal `true` when the active direction is left-to-right or right-to-left.
     */
    private fun emitParticle(crossCoord: Float, tipCoord: Float, isHorizontal: Boolean) {
        if (particles.size >= MAX_PARTICLES) particles.removeAt(0)
        val p = Particle()
        val speed = Random.nextFloat() * PARTICLE_SPEED_RANGE + PARTICLE_SPEED_MIN
        p.alpha = Random.nextFloat() * 0.4f + 0.6f
        p.radius = Random.nextFloat() * 2.0f + 1.5f
        p.turbulence = Random.nextFloat() * 0.25f + 0.1f

        when (direction) {
            VisualizerDirection.BOTTOM_TO_TOP -> {
                p.x = crossCoord + (Random.nextFloat() - 0.5f) * 6f
                p.y = tipCoord
                p.vx = (Random.nextFloat() - 0.5f) * 0.8f
                p.vy = -speed
            }
            VisualizerDirection.TOP_TO_BOTTOM -> {
                p.x = crossCoord + (Random.nextFloat() - 0.5f) * 6f
                p.y = tipCoord
                p.vx = (Random.nextFloat() - 0.5f) * 0.8f
                p.vy = speed
            }
            VisualizerDirection.LEFT_TO_RIGHT -> {
                p.x = tipCoord
                p.y = crossCoord + (Random.nextFloat() - 0.5f) * 6f
                p.vx = speed
                p.vy = (Random.nextFloat() - 0.5f) * 0.8f
            }
            VisualizerDirection.RIGHT_TO_LEFT -> {
                p.x = tipCoord
                p.y = crossCoord + (Random.nextFloat() - 0.5f) * 6f
                p.vx = -speed
                p.vy = (Random.nextFloat() - 0.5f) * 0.8f
            }
        }
        particles.add(p)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            ThemeManager.addListener(this)
            registerListener(prefsListener)
            cornerRadiusFraction = computeCornerFraction()
            applyModePreferences()
            visibility = if (PlayerPreferences.isVisualizerEnabled()) VISIBLE else GONE
            particlesEnabled = VisualizerPreferences.areParticlesEnabled()
            capPaint.color = ThemeManager.accent.primaryAccentColor
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ThemeManager.removeListener(this)
        unregisterListener(prefsListener)
        particles.clear()
    }

    private fun applyModePreferences() {
        mode = if (VisualizerPreferences.getVisualizerType() == VisualizerPreferences.TYPE_WAVE) {
            VisualizerMode.WAVE
        } else {
            VisualizerMode.BARS
        }
    }

    // ── ThemeChangedListener ──────────────────────────────────────────────────

    override fun onAccentChanged(accent: Accent) {
        super.onAccentChanged(accent)
        barColors = buildAccentColors()
        rebuildGradient(width, height)
        capPaint.color = accent.primaryAccentColor
        invalidate()
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        // Bars use accent colors, not theme background colors — no action needed.
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Computes the corner radius as a fraction of bar cross-section width from the current app preference.
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
         * Gravity added to a cap's velocity each frame while it is retreating.
         * Normalized units (0..1 per frame²).
         */
        private const val GRAVITY = 0.0018f

        /** Terminal velocity cap for the retreating peak pill. */
        private const val MAX_PEAK_VELOCITY = 0.04f

        /** Frames the peak cap holds its highest position before gravity starts. */
        private const val PEAK_HOLD_FRAMES = 60

        /** Change threshold below which a bar is considered idle and animation may stop. */
        private const val IDLE_THRESHOLD = 0.0008f

        /** Minimum bar length in pixels so silent bars remain perceptible. */
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
         * Scale factor applied to the normalized app corner radius when mapping it to bar width.
         * Values above 1.0 produce pill-shaped bars at maximum app corner radius.
         */
        const val CORNER_RADIUS_FACTOR = 1.5f

        /** Minimum bar length (target) for emitting a particle. */
        private const val PARTICLE_MIN_HEIGHT = 0.15f

        /** Particle emission cooldown in frames to prevent flooding. */
        private const val PARTICLE_COOLDOWN_FRAMES = 72

        /** Maximum number of simultaneous live particles to cap memory usage. */
        const val MAX_PARTICLES = 120

        /** Range of possible initial primary-axis speeds in normalized units per frame. */
        private const val PARTICLE_SPEED_MIN = 0.15f
        private const val PARTICLE_SPEED_RANGE = 0.85f

        /**
         * Alpha multiplier applied each frame.
         * At 60 fps a particle reaches near-zero opacity after ~130 frames (~2.2 s).
         */
        const val PARTICLE_FADE_RATE = 0.975f

        /** Per-frame cross-axis velocity multiplier — simulates air resistance. */
        private const val PARTICLE_DRAG = 0.93f

        /**
         * Per-frame primary-axis speed multiplier.
         * Very close to 1.0 keeps the ascent almost constant, like warm ash buoyed by heat.
         */
        private const val PARTICLE_VERTICAL_DECAY = 0.9985f

        /**
         * Amplitude scale factor for the secondary (depth) wave layer in [VisualizerMode.WAVE].
         */
        const val WAVE_SECONDARY_SCALE = 0.6f
    }
}
