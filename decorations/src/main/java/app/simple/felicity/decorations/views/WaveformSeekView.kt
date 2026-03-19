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
import android.view.MotionEvent
import android.view.View
import app.simple.felicity.decorations.views.WaveformSeekView.Companion.BAND_COUNT
import app.simple.felicity.decorations.views.WaveformSeekView.Companion.OVERLAY_ALPHA
import app.simple.felicity.manager.SharedPreferences.registerListener
import app.simple.felicity.manager.SharedPreferences.unregisterListener
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A scrollable waveform seekbar that renders live equalizer bands as gradient-filled
 * vertical bars inside a rounded-corner box. A 20%-opacity accent-colored overlay
 * covers the already-played region from the left edge to the current playback position,
 * giving an at-a-glance progress indicator. The user can drag horizontally anywhere
 * within the view to seek.
 *
 * Incoming RMS band magnitudes are normalized with a fast-attack/slow-release AGC and
 * smoothed per frame with configurable rise/fall lerp coefficients. The gradient and
 * overlay color always reflect the current theme accent and update automatically on
 * accent changes. The container corner radius is sourced live from
 * [AppearancePreferences.getCornerRadius] and reacts to preference changes while the
 * view is attached.
 *
 * @author Hamza417
 */
class WaveformSeekView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), ThemeChangedListener {

    // ── Seek listener ─────────────────────────────────────────────────────────

    /**
     * Callback interface delivered when the user seeks by dragging within the view.
     */
    interface OnSeekListener {

        /**
         * Called each frame while the user is dragging, with the live fractional position.
         *
         * @param seekView The originating [WaveformSeekView].
         * @param fraction Current drag position in the range [0.0..1.0].
         */
        fun onSeekChanged(seekView: WaveformSeekView, fraction: Float)

        /**
         * Called once when the user first touches the view to begin a seek gesture.
         *
         * @param seekView The originating [WaveformSeekView].
         */
        fun onSeekStart(seekView: WaveformSeekView) {}

        /**
         * Called once when the user lifts their finger, supplying the final seek fraction.
         *
         * @param seekView The originating [WaveformSeekView].
         * @param fraction Final seek position in the range [0.0..1.0].
         */
        fun onSeekStop(seekView: WaveformSeekView, fraction: Float) {}
    }

    // ── Band state ────────────────────────────────────────────────────────────

    /** Smoothed magnitude for each band — the value actually rendered each frame. */
    private val currentBands = FloatArray(BAND_COUNT)

    /** Target magnitude toward which each band lerps each frame. */
    private val targetBands = FloatArray(BAND_COUNT)

    // ── Progress ──────────────────────────────────────────────────────────────

    /** Normalized playback position [0..1] applied by [setProgress]. */
    private var progressFraction = 0f

    /** Normalized position tracked during an active drag gesture. */
    private var dragFraction = 0f

    /** True while the user's finger is on the view. */
    private var isDragging = false

    // ── AGC ───────────────────────────────────────────────────────────────────

    /**
     * Smoothed peak across all bands, used as the AGC divisor.
     * Fast attack, slow release to keep bars usefully scaled without clipping.
     */
    private var smoothedMax = MIN_SMOOTHED_MAX

    // ── Paints ────────────────────────────────────────────────────────────────

    /** Gradient-filled paint for equalizer bars; shader is rebuilt on size or accent changes. */
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /**
     * Solid paint for the played-region overlay. Color is set to the primary accent each
     * time the gradient is rebuilt; alpha is clamped to [OVERLAY_ALPHA] (≈20% of 255).
     */
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = OVERLAY_ALPHA
    }

    /** Background fill for the container rectangle. Updated on theme changes. */
    private val containerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // ── Reusable geometry ─────────────────────────────────────────────────────

    private val containerRect = RectF()
    private val overlayRect = RectF()
    private val barRect = RectF()
    private val barPath = Path()
    private val clipPath = Path()

    /**
     * Top-only corner radii array ([Path.addRoundRect] expects eight values).
     * Indices 4–7 remain 0 so bar bases are always flat.
     */
    private val barCornerRadii = FloatArray(8)

    // ── Gradient ──────────────────────────────────────────────────────────────

    private var gradient: LinearGradient? = null
    private var cachedWidth = 0
    private var accentColors = buildAccentColors()

    // ── Corner radius ─────────────────────────────────────────────────────────

    /** Container corner radius in pixels, sourced live from [AppearancePreferences]. */
    private var cornerRadius = 0f

    // ── Animation guard ───────────────────────────────────────────────────────

    /**
     * Whether a redraw frame is already scheduled via [postInvalidateOnAnimation].
     * Prevents duplicate scheduling when [setBands] is called faster than the display refreshes.
     */
    private var animating = false

    // ── Listener ──────────────────────────────────────────────────────────────

    private var seekListener: OnSeekListener? = null

    // ── Preference change listener ────────────────────────────────────────────

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppearancePreferences.APP_CORNER_RADIUS) {
            cornerRadius = computeCornerRadius()
            invalidate()
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
        setLayerType(LAYER_TYPE_HARDWARE, null)
        cornerRadius = computeCornerRadius()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Registers a listener for seek gestures. Pass `null` to remove the current listener.
     *
     * @param listener The [OnSeekListener] to register, or `null` to clear it.
     */
    fun setOnSeekListener(listener: OnSeekListener?) {
        seekListener = listener
    }

    /**
     * Delivers a new spectrum snapshot. The bands are normalized with AGC and square-root
     * perceptual compression, then smoothed per frame toward the target.
     *
     * @param bands Raw RMS magnitudes in any positive float scale. Length should equal
     *              [BAND_COUNT]; extra elements are silently ignored.
     */
    fun setBands(bands: FloatArray) {
        val len = minOf(bands.size, BAND_COUNT)

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
        for (i in 0 until len) {
            val linear = (bands[i] * invMax).coerceIn(0f, 1f)
            targetBands[i] = if (linear < NOISE_FLOOR) 0f else sqrt(linear)
        }

        scheduleRedraw()
    }

    /**
     * Updates the playback position shown as the highlight overlay.
     *
     * The overlay covers the region from the left edge to `(position / duration) * width`.
     * Ignored while the user is actively dragging.
     *
     * @param position Current playback position in arbitrary time units (e.g. milliseconds).
     * @param duration Total track duration in the same units. Must be > 0.
     */
    fun setProgress(position: Long, duration: Long) {
        if (!isDragging && duration > 0L) {
            progressFraction = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            invalidate()
        }
    }

    /**
     * Replaces the gradient color stops used for the equalizer bars.
     *
     * @param colors ARGB color integers for the gradient (at least two elements required).
     *               Pass an empty array to revert to the current accent colors.
     */
    fun setColors(colors: IntArray) {
        accentColors = if (colors.size >= 2) colors else buildAccentColors()
        gradient = null
        invalidate()
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        if (w <= 0f) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                dragFraction = (event.x / w).coerceIn(0f, 1f)
                seekListener?.onSeekStart(this)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                dragFraction = (event.x / w).coerceIn(0f, 1f)
                seekListener?.onSeekChanged(this, dragFraction)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragFraction = (event.x / w).coerceIn(0f, 1f)
                progressFraction = dragFraction
                seekListener?.onSeekStop(this, dragFraction)
                isDragging = false
                invalidate()
                performClick()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    // ── Size / gradient ───────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildGradient(w)
    }

    private fun rebuildGradient(w: Int) {
        if (w == 0) return
        cachedWidth = w

        val positions = FloatArray(accentColors.size) { i ->
            i.toFloat() / (accentColors.size - 1)
        }

        val g = LinearGradient(
                0f, 0f, w.toFloat(), 0f,
                accentColors, positions,
                Shader.TileMode.CLAMP
        )
        gradient = g
        barPaint.shader = g

        overlayPaint.color = accentColors[0]
        overlayPaint.alpha = OVERLAY_ALPHA
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        if (cachedWidth != width) rebuildGradient(width)

        val w = width.toFloat()
        val h = height.toFloat()
        val r = cornerRadius.coerceAtMost(h / 2f).coerceAtMost(w / 2f)

        // Draw the rounded-corner container background.
        containerRect.set(0f, 0f, w, h)
        containerPaint.color = ThemeManager.theme.viewGroupTheme.highlightColor
        canvas.drawRoundRect(containerRect, r, r, containerPaint)

        // Clip subsequent drawing to the rounded rectangle.
        clipPath.reset()
        clipPath.addRoundRect(containerRect, r, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        // Advance each band one lerp step toward its target.
        var stillMoving = false
        for (i in 0 until BAND_COUNT) {
            val target = targetBands[i]
            val current = currentBands[i]
            val speed = if (target > current) RISE_SPEED else FALL_SPEED
            val next = (current + (target - current) * speed).coerceIn(0f, 1f)
            currentBands[i] = next
            if (abs(next - current) > IDLE_THRESHOLD) stillMoving = true
        }

        // Draw equalizer bars growing from the bottom up.
        val slotWidth = w / BAND_COUNT
        val gapWidth = slotWidth * BAR_GAP_FRACTION
        val barWidth = slotWidth - gapWidth
        val barCorner = (barWidth * BAR_CORNER_FRACTION).coerceIn(0f, barWidth / 2f)

        barCornerRadii[0] = barCorner; barCornerRadii[1] = barCorner
        barCornerRadii[2] = barCorner; barCornerRadii[3] = barCorner
        barCornerRadii[4] = 0f; barCornerRadii[5] = 0f
        barCornerRadii[6] = 0f; barCornerRadii[7] = 0f

        for (i in 0 until BAND_COUNT) {
            val barH = (currentBands[i] * h * MAX_BAR_HEIGHT_FRACTION).coerceAtLeast(MIN_BAR_PX)
            val left = i * slotWidth + gapWidth / 2f
            val right = left + barWidth
            barRect.set(left, h - barH, right, h)
            barPath.reset()
            barPath.addRoundRect(barRect, barCornerRadii, Path.Direction.CW)
            canvas.drawPath(barPath, barPaint)
        }

        // Draw the played-region highlight overlay.
        val displayProgress = if (isDragging) dragFraction else progressFraction
        if (displayProgress > 0f) {
            overlayRect.set(0f, 0f, w * displayProgress, h)
            canvas.drawRect(overlayRect, overlayPaint)
        }

        canvas.restore()

        if (stillMoving) {
            postInvalidateOnAnimation()
        } else {
            animating = false
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            ThemeManager.addListener(this)
            registerListener(prefsListener)
            cornerRadius = computeCornerRadius()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ThemeManager.removeListener(this)
        unregisterListener(prefsListener)
    }

    // ── ThemeChangedListener ──────────────────────────────────────────────────

    override fun onAccentChanged(accent: Accent) {
        super.onAccentChanged(accent)
        accentColors = buildAccentColors()
        rebuildGradient(width)
        invalidate()
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        // The background fill color comes from the theme's highlight color; trigger a redraw.
        invalidate()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun scheduleRedraw() {
        if (!animating) {
            animating = true
            postInvalidateOnAnimation()
        }
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

    /**
     * Maps [AppearancePreferences.getCornerRadius] (range 1..80) to a pixel corner radius
     * for the outer container rectangle.
     *
     * The preference value is already expressed in display-independent units comparable to dp;
     * it is multiplied by the display density to obtain physical pixels.
     */
    private fun computeCornerRadius(): Float {
        if (isInEditMode) return 24f
        return AppearancePreferences.getCornerRadius() * resources.displayMetrics.density * CORNER_RADIUS_DENSITY_SCALE
    }

    companion object {

        /** Number of frequency bands rendered — must match the engine's band count. */
        const val BAND_COUNT = 40

        /** Fraction of each band slot consumed by the gap between adjacent bars. */
        private const val BAR_GAP_FRACTION = 0.18f

        /** Fraction of bar width used as the rounded top-corner radius. */
        private const val BAR_CORNER_FRACTION = 0.35f

        /** Maximum fraction of the view height a fully-peaked bar may reach. */
        private const val MAX_BAR_HEIGHT_FRACTION = 0.88f

        /** Minimum bar height in pixels so silent bars remain perceptible. */
        private const val MIN_BAR_PX = 3f

        /** Lerp factor per frame when a bar is rising toward a louder target. */
        private const val RISE_SPEED = 0.25f

        /** Lerp factor per frame when a bar is falling toward a quieter target. */
        private const val FALL_SPEED = 0.06f

        /** Movement threshold below which a bar is considered idle and animation may stop. */
        private const val IDLE_THRESHOLD = 0.001f

        /** AGC fast-attack coefficient. */
        private const val AGC_ATTACK = 0.4f

        /** AGC slow-release coefficient. */
        private const val AGC_RELEASE = 0.004f

        /** Floor value for the AGC reference to prevent division-by-zero during silence. */
        private const val MIN_SMOOTHED_MAX = 0.0001f

        /**
         * Normalized magnitude below which a band is treated as silence.
         * Suppresses FFT-leakage noise without hiding quiet musical content.
         */
        private const val NOISE_FLOOR = 0.015f

        /**
         * Alpha value for the played-region highlight overlay.
         * 0.20 × 255 ≈ 51, giving a 20% opacity tint over the bars.
         */
        private const val OVERLAY_ALPHA = 51

        /**
         * Scale factor that converts the preference value (1..80) to a pixel corner radius
         * for the outer container. Multiplied by [android.util.DisplayMetrics.density].
         *
         * A scale of 0.4 means the maximum preference (80) yields 32dp of corner radius,
         * which results in a fully pill-shaped container at the most rounded setting.
         */
        private const val CORNER_RADIUS_DENSITY_SCALE = 0.4f
    }
}

