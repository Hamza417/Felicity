package app.simple.felicity.decorations.views

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
import app.simple.felicity.decorations.typeface.TypeFace
import app.simple.felicity.decorations.typeface.TypefaceStyle
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.shared.utils.ColorUtils.toHalfAlpha
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.models.Theme
import kotlin.math.log10
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

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
    }

    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val singleBandPath = Path()
    private val fillPath = Path()
    private val borderRect = RectF()
    private val clipPath = Path()

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

            peqBandPaint.color = androidx.core.graphics.ColorUtils.setAlphaComponent(ThemeManager.accent.primaryAccentColor, 0x4D) // 30% alpha
            handlePaint.color = ThemeManager.accent.primaryAccentColor
            handleFillPaint.color = ThemeManager.theme.viewGroupTheme.backgroundColor
        } else {
            textPaint.color = 0x88000000.toInt()
            freqPaint.color = 0x88000000.toInt()
            gridPaint.color = 0x40000000.toInt()
            borderPaint.color = 0x40000000.toInt()
            dotPaint.color = 0xFF000000.toInt()

            peqBandPaint.color = 0x4D000000
            handlePaint.color = 0xFF000000.toInt()
            handleFillPaint.color = 0xFFFFFFFF.toInt()
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

        val w = width.toFloat()
        val h = height.toFloat()

        val textPadding = 8f * resources.displayMetrics.density
        // Use a wide enough sample string for measurement
        val textWidth = textPaint.measureText("+00.0")
        val graphW = w - (textWidth + textPadding)
        val graphStartX = textWidth + textPadding

        val freqTextHeight = freqPaint.descent() - freqPaint.ascent()
        val bottomPadding = freqTextHeight + textPadding / 2f
        val graphH = h - bottomPadding

        val pad = graphH * VERTICAL_PADDING_FRACTION
        val drawH = graphH - 2f * pad
        val midY = graphH / 2f

        var maxGain = 0f
        var minGain = 0f

        // Increase samples for a smoother curve, especially on sharp PEQ bells
        val numSamples = 300
        val peqGains = FloatArray(numSamples)
        val stepX = graphW / (numSamples - 1)
        val slotW = graphW / BAND_COUNT

        if (isPeq) {
            for (i in 0 until numSamples) {
                val x = graphStartX + i * stepX
                val freq = 31.25f * 2f.pow((x - graphStartX) / slotW - 0.5f)
                peqGains[i] = getPeqResponseAt(freq, peqBands)
            }
            for (gain in peqGains) {
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
        val maxAbs = maxOf(kotlin.math.abs(maxGain), kotlin.math.abs(minGain), 3f)
        val dynamicDbMax = maxAbs
        val dynamicDbMin = -maxAbs
        val range = dynamicDbMax - dynamicDbMin

        val radius = if (isInEditMode) 20f else AppearancePreferences.getCornerRadius()
        val inset = borderPaint.strokeWidth / 2f
        borderRect.set(graphStartX + inset, inset, w - inset, graphH - inset)

        canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(borderRect, radius, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)

        // Draw Grid inside clip
        val gridLines = 7
        for (i in 0 until gridLines) {
            val y = graphH * i / (gridLines - 1)
            canvas.drawLine(graphStartX, y, w, y, gridPaint)
        }

        for (i in 0 until BAND_COUNT) {
            val x = graphStartX + slotW * i + slotW / 2f
            canvas.drawLine(x, 0f, x, graphH, gridPaint)
        }

        // Build a smooth cubic Bezier path through all band points.
        wavePath.reset()

        if (isPeq) {
            // Draw individual band curves first (faintly)
            for (band in peqBands) {
                singleBandPath.reset()
                for (i in 0 until numSamples) {
                    val x = graphStartX + i * stepX
                    val freq = 31.25f * 2f.pow((x - graphStartX) / slotW - 0.5f)
                    val bandGain = getSingleBandResponseAt(freq, band.first, band.second, band.third)
                    val normalizedGain = bandGain / dynamicDbMax
                    val y = midY - normalizedGain * (drawH / 2f)
                    if (i == 0) singleBandPath.moveTo(x, y)
                    else singleBandPath.lineTo(x, y)
                }
                canvas.drawPath(singleBandPath, peqBandPaint)
            }

            for (i in 0 until numSamples) {
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
            wavePath.lineTo(w, points.last()[1])
        }

        fillPath.set(wavePath)
        fillPath.lineTo(w, graphH)
        fillPath.lineTo(graphStartX, graphH)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)

        wavePaint.shader = gradient
        canvas.drawPath(wavePath, wavePaint)

        // Draw dots
        if (isPeq) {
            val handleOuterRadius = 2.5f * resources.displayMetrics.density
            val handleInnerRadius = 1.5f * resources.displayMetrics.density

            for ((gainDb, _, f0) in peqBands) {
                // Find the x position for this band's center frequency
                // f0 = 31.25f * 2f.pow((x - graphStartX) / slotW - 0.5f)
                val log2fRatio = kotlin.math.log2(f0 / 31.25f)
                val x = graphStartX + slotW * (log2fRatio + 0.5f)
                if (x in graphStartX..w) {
                    val normalizedGain = gainDb / dynamicDbMax
                    val y = midY - normalizedGain * (drawH / 2f)
                    canvas.drawCircle(x, y, handleOuterRadius, handlePaint)
                    canvas.drawCircle(x, y, handleInnerRadius, handleFillPaint)
                }
            }
        } else {
            val dotRadius = 1.5f * resources.displayMetrics.density
            for (i in 0 until BAND_COUNT) {
                canvas.drawCircle(points[i][0], points[i][1], dotRadius, dotPaint)
            }
        }

        canvas.restore()

        // Draw Values outside the clip (so they aren't cut off)
        for (i in 0 until gridLines) {
            val y = pad + (drawH * i / (gridLines - 1))
            val value = dynamicDbMax - (range * i / (gridLines - 1))
            val textY = y - ((textPaint.descent() + textPaint.ascent()) / 2f)
            val valueStr = if (value > 0) "+%.1f".format(value) else "%.1f".format(value)
            canvas.drawText(valueStr, graphStartX - textPadding, textY, textPaint)
        }

        // Draw frequency labels at the bottom, outside the borderRect
        val freqs = arrayOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
        val freqY = h - freqPaint.descent()
        for (i in 0 until BAND_COUNT) {
            val x = graphStartX + slotW * i + slotW / 2f
            canvas.drawText(freqs[i], x, freqY, freqPaint)
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
         * Fraction of the view height reserved as top and bottom padding so the wave never
         * touches the very edges even at maximum boost or cut.
         */
        private const val VERTICAL_PADDING_FRACTION = 0.1f

        /**
         * Calculates the approximate magnitude response in dB of a single parametric EQ band at a given frequency.
         * Uses the analog prototype response of peaking filters.
         */
        private fun getSingleBandResponseAt(f: Float, gainDb: Float, q: Float, f0: Float): Float {
            if (gainDb == 0f || f0 <= 0f) return 0f
            val a = 10f.pow(gainDb / 40f)
            val fRatio = f / f0
            val fRatioSq = fRatio * fRatio
            val oneMinusFRatioSq = 1f - fRatioSq
            val oneMinusFRatioSqSq = oneMinusFRatioSq * oneMinusFRatioSq

            val term1 = a * fRatio / q
            val term2 = (fRatio / q) / a

            val num = oneMinusFRatioSqSq + term1 * term1
            val den = oneMinusFRatioSqSq + term2 * term2

            return 10f * log10(num / den)
        }

        /**
         * Calculates the approximate magnitude response in dB of a parametric EQ at a given frequency.
         */
        private fun getPeqResponseAt(f: Float, bands: List<Triple<Float, Float, Float>>): Float {
            var totalGainDb = 0f
            for ((gainDb, q, f0) in bands) {
                totalGainDb += getSingleBandResponseAt(f, gainDb, q, f0)
            }
            return totalGainDb
        }
    }
}

