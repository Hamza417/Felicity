package app.simple.felicity.decorations.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.models.Theme

/**
 * A compact view that draws a smooth thin wave line representing the shape of a 10-band
 * equalizer curve. Think of it as a tiny frequency-response graph — each of the 10 bands
 * pushes the line up (boost) or pulls it down (cut), and the points are connected with
 * cubic Bezier curves so the result looks like a real EQ curve rather than a bar chart.
 *
 * This view is designed to live inside each row of the preset list dialog, giving the
 * user an instant visual preview of what each preset sounds like before they apply it.
 * No numbers, no labels — just a glanceable shape.
 *
 * The line color is sourced from the current theme's accent color so it always matches
 * the rest of the UI. A subtle gradient from the primary to the secondary accent color
 * sweeps across the line from left to right, making it a tiny bit fancy.
 *
 * @author Hamza417
 */
class EqualizerWaveView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), ThemeChangedListener {

    /**
     * The 10 EQ band gains in dB. The range expected by this view is [-15..+15] dB,
     * which maps linearly to the vertical position of each point on the wave.
     * A gain of 0 dB lands exactly on the center horizontal line.
     */
    private var bandGains = FloatArray(BAND_COUNT)

    /**
     * The paint used to stroke the smooth wave path. Its shader is rebuilt whenever
     * the accent color changes or the view is resized.
     */
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH_DP * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /**
     * The path drawn each frame — reused to avoid allocating a new object every draw call.
     * Because garbage collection during drawing is not our friend.
     */
    private val wavePath = Path()

    /**
     * Pre-allocated point array used during drawing. Each element is a two-float pair
     * [x, y] so we avoid creating new arrays inside [onDraw] on every frame.
     * The lint tool appreciates this very much.
     */
    private val points = Array(BAND_COUNT) { FloatArray(2) }

    private var gradient: LinearGradient? = null

    /**
     * Updates the wave shape to reflect a new set of EQ band gains and triggers a redraw.
     * Call this whenever the displayed preset changes.
     *
     * @param gains Array of up to 10 gain values in dB. Extra values are ignored;
     *              missing values default to 0 dB (flat).
     */
    fun setGains(gains: FloatArray) {
        val len = minOf(gains.size, BAND_COUNT)
        for (i in 0 until len) {
            bandGains[i] = gains[i].coerceIn(DB_MIN, DB_MAX)
        }
        for (i in len until BAND_COUNT) {
            bandGains[i] = 0f
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildGradient(w)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val midY = h / 2f

        // Map each band gain to a Y coordinate. Positive gains go up, negative gains go down.
        // The full dB range [-15..+15] spans the full view height with a small padding.
        val pad = h * VERTICAL_PADDING_FRACTION
        val drawH = h - 2f * pad

        /**
         * Calculate the X position for each band point, spread evenly across the view width.
         * We add a half-slot offset so the first point isn't right at the left edge.
         */
        val slotW = w / BAND_COUNT
        for (i in 0 until BAND_COUNT) {
            points[i][0] = slotW * i + slotW / 2f
            val normalizedGain = bandGains[i] / DB_MAX    // -1..+1
            points[i][1] = midY - normalizedGain * (drawH / 2f)  // flip: positive gain = higher
        }

        // Build a smooth cubic Bezier path through all band points.
        wavePath.reset()
        wavePath.moveTo(points[0][0], points[0][1])

        for (i in 0 until points.size - 1) {
            val x1 = points[i][0]
            val y1 = points[i][1]
            val x2 = points[i + 1][0]
            val y2 = points[i + 1][1]

            // Control points sit halfway between consecutive data points, creating smooth
            // transitions that look like a real analog EQ response curve.
            val controlX = (x1 + x2) / 2f
            wavePath.cubicTo(controlX, y1, controlX, y2, x2, y2)
        }

        wavePaint.shader = gradient
        canvas.drawPath(wavePath, wavePaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            ThemeManager.addListener(this)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!isInEditMode) {
            ThemeManager.removeListener(this)
        }
    }

    override fun onAccentChanged(accent: Accent) {
        super.onAccentChanged(accent)
        rebuildGradient(width)
        invalidate()
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        invalidate()
    }

    private fun rebuildGradient(w: Int) {
        if (w == 0) return
        val (primary, secondary) = if (isInEditMode) {
            Pair(0xFF6200EE.toInt(), 0xFF00B0FF.toInt())
        } else {
            Pair(
                    ThemeManager.accent.primaryAccentColor,
                    ThemeManager.accent.secondaryAccentColor
            )
        }
        gradient = LinearGradient(
                0f, 0f, w.toFloat(), 0f,
                intArrayOf(primary, secondary),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
        )
        wavePaint.shader = gradient
    }

    companion object {
        /** Number of EQ bands this view expects — matches the 10-band graphic EQ in the app. */
        const val BAND_COUNT = 10

        /** Maximum and minimum dB values that the wave maps to the full view height. */
        private const val DB_MAX = 15f
        private const val DB_MIN = -15f

        /** Stroke width of the wave line in density-independent pixels. */
        private const val STROKE_WIDTH_DP = 2f

        /**
         * Fraction of the view height reserved as top and bottom padding so the wave never
         * touches the very edges even at maximum boost or cut.
         */
        private const val VERTICAL_PADDING_FRACTION = 0.1f
    }
}

