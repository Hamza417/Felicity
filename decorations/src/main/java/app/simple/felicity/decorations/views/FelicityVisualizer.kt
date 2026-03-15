package app.simple.felicity.decorations.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import app.simple.felicity.decorations.views.FelicityVisualizer.Companion.BAND_COUNT
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme
import kotlin.math.abs
import kotlin.math.sqrt

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
        val cornerRadius = barWidth * 0.35f
        val maxBarHeight = height.toFloat()
        val capPillHeight = (barWidth * 0.3f).coerceAtLeast(3f)

        var stillMoving = delayQueue.isNotEmpty()

        for (i in 0 until BAND_COUNT) {
            val target = targetBands[i]
            val current = currentBands[i]

            val speed = if (target > current) RISE_SPEED else FALL_SPEED
            val next = (current + (target - current) * speed).coerceIn(0f, 1f)
            currentBands[i] = next

            if (abs(next - current) > IDLE_THRESHOLD) stillMoving = true

            // Bar — strictly clamped so it can never escape the view bounds.
            val barHeightPx = (next * maxBarHeight).coerceIn(MIN_BAR_PX, maxBarHeight)
            val left = i * slotWidth + gapWidth / 2f
            val right = left + barWidth
            drawRect.set(left, maxBarHeight - barHeightPx, right, maxBarHeight)
            canvas.drawRoundRect(drawRect, cornerRadius, cornerRadius, barPaint)

            // Peak cap — pushed up by the bar, then falls under gravity.
            if (next >= peakBands[i]) {
                // Bar is pushing the cap up — reset velocity and restart hold timer.
                peakBands[i] = next
                peakVelocities[i] = 0f
                peakHoldCounters[i] = PEAK_HOLD_FRAMES
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

        if (stillMoving) {
            postInvalidateOnAnimation()
        } else {
            animating = false
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) ThemeManager.addListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ThemeManager.removeListener(this)
        delayQueue.clear()
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

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        /** Number of frequency bands rendered — must match the engine's VisualizerAudioProcessor band count. */
        const val BAND_COUNT = 30

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
         * This prevents FFT spectral leakage from making a silent bass zone appear active.
         */
        private const val NOISE_FLOOR = 0.05f

        /**
         * Default latency compensation in milliseconds.
         * Audio data passes through the ExoPlayer write buffer and hardware DAC buffers
         * before reaching the speakers, so the visualizer needs this delay to stay in sync.
         * Tune via [latencyMs] if your device's output latency differs.
         */
        const val DEFAULT_LATENCY_MS = 150L
    }
}
