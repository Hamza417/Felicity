package app.simple.felicity.decorations.equalizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withSave
import app.simple.felicity.decorations.typeface.TypeFace
import app.simple.felicity.decorations.typeface.TypefaceStyle
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.shared.utils.ColorUtils.toHalfAlpha
import app.simple.felicity.shared.utils.ColorUtils.toQuarterAlpha
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.models.Theme
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow

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

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 8f, resources.displayMetrics)
        textAlign = Paint.Align.RIGHT
    }

    private val freqPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 8f, resources.displayMetrics)
        textAlign = Paint.Align.CENTER
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f * resources.displayMetrics.density
    }

    private var isPeq = false
    private var peqBands = emptyList<Triple<Float, Float, Float>>()

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val peqBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
    }

    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val singleBandPath = Path()
    private val fillPath = Path()
    private val borderRect = RectF()
    private val clipPath = Path()

    // Pre-allocated arrays to avoid onDraw allocations
    private val peqGains: FloatArray by lazy { FloatArray(PEQ_CURVE_RESOLUTION) }

    init {
        if (!isInEditMode) {
            val typeFace = TypeFace.getTypeFace(
                    AppearancePreferences.getAppFont(), TypefaceStyle.REGULAR.style, context)
            textPaint.typeface = typeFace
            freqPaint.typeface = typeFace
        }

        updateColors()
    }

    private fun updateColors() {
        if (!isInEditMode) {
            textPaint.color = ThemeManager.theme.textViewTheme.tertiaryTextColor
            freqPaint.color = ThemeManager.theme.textViewTheme.tertiaryTextColor
            gridPaint.color = ThemeManager.theme.viewGroupTheme.dividerColor.toHalfAlpha()
            borderPaint.color = ThemeManager.accent.primaryAccentColor
            dotPaint.color = ThemeManager.theme.textViewTheme.primaryTextColor

            peqBandPaint.color = ThemeManager.accent.primaryAccentColor.toQuarterAlpha()
            handleFillPaint.color = ThemeManager.accent.primaryAccentColor.toQuarterAlpha()
        } else {
            textPaint.color = 0x88000000.toInt()
            freqPaint.color = 0x88000000.toInt()
            gridPaint.color = 0x40000000.toInt()
            borderPaint.color = 0x40000000.toInt()
            dotPaint.color = 0xFF000000.toInt()

            peqBandPaint.color = 0x4D000000
            handleFillPaint.color = 0x4D000000
        }
    }

    /**
     * Updates the wave shape to reflect a new set of EQ band gains and triggers a redraw.
     * Call this whenever the displayed preset changes.
     *
     * @param gains Array of up to 10 gain values in dB. Extra values are ignored;
     *              missing values default to 0 dB (flat).
     */
    fun setGains(gains: FloatArray) {
        isPeq = false
        val len = minOf(gains.size, BAND_COUNT)
        for (i in 0 until len) {
            bandGains[i] = gains[i].coerceIn(DB_MIN, DB_MAX)
        }
        for (i in len until BAND_COUNT) {
            bandGains[i] = 0f
        }
        invalidate()
    }

    /**
     * Updates the wave shape to reflect a parametric EQ response.
     *
     * @param bands A list of PEQ bands, where each is a Triple of (gainDb, qFactor, frequencyHz).
     */
    fun setPeqBands(bands: List<Triple<Float, Float, Float>>) {
        isPeq = true
        peqBands = bands
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildGradient(w)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        val textPadding = 8f * resources.displayMetrics.density
        // Use a wide enough sample string for measurement
        val textWidth = textPaint.measureText("+00.0")
        val graphW = viewWidth - (textWidth + textPadding)
        val graphStartX = textWidth + textPadding

        val freqTextHeight = freqPaint.descent() - freqPaint.ascent()
        val bottomPadding = freqTextHeight + textPadding / 2f
        val graphH = viewHeight - bottomPadding

        val pad = graphH * VERTICAL_PADDING_FRACTION
        val drawH = graphH - 2f * pad
        val midY = graphH / 2f

        var maxGain = 0f
        var minGain = 0f

        val stepX = graphW / (PEQ_CURVE_RESOLUTION - 1)
        val slotW = graphW / BAND_COUNT

        if (isPeq) {
            for (i in 0 until PEQ_CURVE_RESOLUTION) {
                val x = graphStartX + i * stepX
                val frequency = BASE_FREQ_HZ * 2f.pow((x - graphStartX) / slotW - 0.5f)
                peqGains[i] = getPeqResponseAt(frequency, peqBands)
            }

            for (i in 0 until PEQ_CURVE_RESOLUTION) {
                val gain = peqGains[i]
                if (gain > maxGain) maxGain = gain
                if (gain < minGain) minGain = gain
            }
        } else {
            for (gain in bandGains) {
                if (gain > maxGain) maxGain = gain
                if (gain < minGain) minGain = gain
            }
        }

        // Ensure there's some range to avoid division by zero and overly flat look when all zeros
        val dynamicDbMax = maxOf(abs(maxGain), abs(minGain), MIN_DYNAMIC_RANGE_DB)

        val radius = if (isInEditMode) EDIT_MODE_CORNER_RADIUS else AppearancePreferences.getCornerRadius()
        val inset = borderPaint.strokeWidth / 2f
        borderRect.set(graphStartX + inset, inset, viewWidth - inset, graphH - inset)

        canvas.withSave {
            clipPath.reset()
            clipPath.addRoundRect(borderRect, radius, radius, Path.Direction.CW)
            clipPath(clipPath)

            // Draw Grid inside clip
            for (i in 0 until HORIZONTAL_GRID_LINES) {
                val y = graphH * i / (HORIZONTAL_GRID_LINES - 1)
                drawLine(graphStartX, y, viewWidth, y, gridPaint)
            }

            for (i in 0 until BAND_COUNT) {
                val x = graphStartX + slotW * i + slotW / 2f
                drawLine(x, 0f, x, graphH, gridPaint)
            }

            // Build a smooth cubic Bezier path through all band points.
            wavePath.reset()

            if (isPeq) {
                // Draw individual band curves first (faintly)
                for ((first, second, third) in peqBands) {
                    singleBandPath.reset()

                    for (i in 0 until PEQ_CURVE_RESOLUTION) {
                        val x = graphStartX + i * stepX
                        val frequency = BASE_FREQ_HZ * 2f.pow((x - graphStartX) / slotW - 0.5f)
                        val bandGain = getSingleBandResponseAt(frequency, first, second, third)
                        val normalizedGain = bandGain / dynamicDbMax
                        val y = midY - normalizedGain * (drawH / 2f)
                        if (i == 0) singleBandPath.moveTo(x, y)
                        else singleBandPath.lineTo(x, y)
                    }

                    drawPath(singleBandPath, peqBandPaint)
                }

                for (i in 0 until PEQ_CURVE_RESOLUTION) {
                    val x = graphStartX + i * stepX
                    val normalizedGain = peqGains[i] / dynamicDbMax
                    val y = midY - normalizedGain * (drawH / 2f)
                    if (i == 0) wavePath.moveTo(x, y)
                    else wavePath.lineTo(x, y)
                }
            } else {
                /**
                 * Calculate the X position for each band point, spread evenly across the view width.
                 * We add a half-slot offset so the first point isn't right at the left edge.
                 */
                for (i in 0 until BAND_COUNT) {
                    points[i][0] = graphStartX + slotW * i + slotW / 2f
                    val normalizedGain = bandGains[i] / dynamicDbMax    // -1..+1
                    points[i][1] = midY - normalizedGain * (drawH / 2f)  // flip: positive gain = higher
                }

                wavePath.moveTo(graphStartX, points[0][1])
                wavePath.lineTo(points[0][0], points[0][1])

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
                wavePath.lineTo(viewWidth, points.last()[1])
            }

            fillPath.set(wavePath)
            fillPath.lineTo(viewWidth, graphH)
            fillPath.lineTo(graphStartX, graphH)
            fillPath.close()

            drawPath(fillPath, fillPaint)

            wavePaint.shader = gradient
            drawPath(wavePath, wavePaint)

            // Draw dots
            if (isPeq) {
                val handleOuterRadius = DOT_RADIUS * resources.displayMetrics.density

                for ((gainDb, _, centerFrequency) in peqBands) {
                    // Find the x position for this band's center frequency
                    // centerFrequency = BASE_FREQ_HZ * 2f.pow((x - graphStartX) / slotW - 0.5f)
                    val log2fRatio = log2(centerFrequency / BASE_FREQ_HZ)
                    val x = graphStartX + slotW * (log2fRatio + 0.5f)
                    if (x in graphStartX..viewWidth) {
                        val normalizedGain = gainDb / dynamicDbMax
                        val y = midY - normalizedGain * (drawH / 2f)
                        drawCircle(x, y, handleOuterRadius, handleFillPaint)
                    }
                }
            } else {
                val dotRadius = DOT_RADIUS * resources.displayMetrics.density
                for (i in 0 until BAND_COUNT) {
                    drawCircle(points[i][0], points[i][1], dotRadius, dotPaint)
                }
            }
        }

        // Draw Values outside the clip (so they aren't cut off)
        val gridMax = dynamicDbMax * 1.5f
        for (i in 0 until HORIZONTAL_GRID_LINES) {
            val y = graphH * i / (HORIZONTAL_GRID_LINES - 1)
            val value = gridMax - (gridMax * 2 * i / (HORIZONTAL_GRID_LINES - 1))
            val textY = y - ((textPaint.descent() + textPaint.ascent()) / 2f)
            val valueStr = if (value > 0f) "+%.1f".format(value) else "%.1f".format(value)
            canvas.drawText(valueStr, graphStartX - textPadding, textY, textPaint)
        }

        // Draw frequency labels at the bottom, outside the borderRect
        val freqY = viewHeight - freqPaint.descent()
        for (i in 0 until BAND_COUNT) {
            val x = graphStartX + slotW * i + slotW / 2f
            canvas.drawText(FREQUENCY_LABELS[i], x, freqY, freqPaint)
        }

        // Draw the border
        canvas.drawRoundRect(borderRect, radius, radius, borderPaint)
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
        updateColors()
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

        val textPadding = 8f * resources.displayMetrics.density
        val textWidth = textPaint.measureText("+00.0") + textPadding
        val graphStartX = textWidth

        gradient = LinearGradient(
                graphStartX, 0f, w.toFloat(), 0f,
                intArrayOf(primary, secondary),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
        )
        wavePaint.shader = gradient

        val primaryFaint = ColorUtils.setAlphaComponent(primary, 0x1A) // 10%
        val secondaryFaint = ColorUtils.setAlphaComponent(secondary, 0x1A) // 10%

        fillPaint.shader = LinearGradient(
                graphStartX, 0f, w.toFloat(), 0f,
                intArrayOf(primaryFaint, secondaryFaint),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
        )
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
         * The width of the frequency dots on the wave line.
         */
        private const val DOT_RADIUS = 2.0f // 2dp

        /**
         * Fraction of the view height reserved as top and bottom padding so the wave never
         * touches the very edges even at maximum boost or cut.
         */
        private const val VERTICAL_PADDING_FRACTION = 0.1f

        /** Number of frequency samples used to calculate and render the PEQ response curve. */
        private const val PEQ_CURVE_RESOLUTION = 300

        /** Minimum visible gain bounds for drawing so flat EQ graphs don't collapse. */
        private const val MIN_DYNAMIC_RANGE_DB = 3f

        /** Fallback corner radius used when the view is in layout preview (edit mode). */
        private const val EDIT_MODE_CORNER_RADIUS = 20f

        /** Base frequency in Hz for the lowest graphic EQ band. */
        private const val BASE_FREQ_HZ = 31.25f

        /** Number of horizontal grid lines to draw inside the wave bounding box. */
        private const val HORIZONTAL_GRID_LINES = 7

        /** Hardcoded labels for the frequency x-axis text. */
        private val FREQUENCY_LABELS = arrayOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

        /** Opacity (0-255) for the faint wave gradient fill. */
        private const val WAVE_FILL_ALPHA = 0x1A

        /** Opacity (0-255) for the faint individual band responses in PEQ mode. */
        private const val PEQ_BAND_STROKE_ALPHA = 0x4D

        /**
         * Calculates the approximate magnitude response in dB of a single parametric EQ band at a given frequency.
         * Uses the analog prototype response of peaking filters.
         */
        private fun getSingleBandResponseAt(frequency: Float, gainDb: Float, qFactor: Float, centerFrequency: Float): Float {
            if (gainDb == 0f || centerFrequency <= 0f) return 0f
            val amplitude = 10f.pow(gainDb / 40f)
            val fRatio = frequency / centerFrequency
            val fRatioSq = fRatio * fRatio
            val oneMinusFRatioSq = 1f - fRatioSq
            val oneMinusFRatioSqSq = oneMinusFRatioSq * oneMinusFRatioSq

            val term1 = amplitude * fRatio / qFactor
            val term2 = (fRatio / qFactor) / amplitude

            val num = oneMinusFRatioSqSq + term1 * term1
            val den = oneMinusFRatioSqSq + term2 * term2

            return 10f * log10(num / den)
        }

        /**
         * Calculates the approximate magnitude response in dB of a parametric EQ at a given frequency.
         */
        private fun getPeqResponseAt(frequency: Float, bands: List<Triple<Float, Float, Float>>): Float {
            var totalGainDb = 0f
            for ((gainDb, qFactor, centerFrequency) in bands) {
                totalGainDb += getSingleBandResponseAt(frequency, gainDb, qFactor, centerFrequency)
            }
            return totalGainDb
        }
    }
}

