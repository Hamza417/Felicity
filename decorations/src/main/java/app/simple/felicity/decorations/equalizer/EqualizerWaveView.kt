package app.simple.felicity.decorations.equalizer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withSave
import app.simple.felicity.decorations.typeface.TypeFace
import app.simple.felicity.decorations.typeface.TypefaceStyle
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.shared.models.ParsedBand
import app.simple.felicity.shared.utils.ColorUtils.toHalfAlpha
import app.simple.felicity.shared.utils.ColorUtils.toQuarterAlpha
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.models.Theme
import kotlin.math.abs
import kotlin.math.pow

/**
 * A compact view that draws a smooth thin wave line representing the shape of a 10-band
 * equalizer curve. Think of it as a tiny frequency-response graph — each of the 10 bands
 * pushes the line-up (boost) or pulls it down (cut), and the points are connected with
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
    private var peqBands = emptyList<ParsedBand>()
    private var bandDelays = FloatArray(0)

    private var prevIsPeq = false
    private var prevPeqBands = emptyList<ParsedBand>()
    private var prevBandDelays = FloatArray(0)

    private var animationProgress = 1f
    private var waveAnimator: ValueAnimator? = null

    private val currentCurve = FloatArray(PEQ_CURVE_RESOLUTION)
    private val previousCurve = FloatArray(PEQ_CURVE_RESOLUTION)

    private data class Dot(val xFrac: Float, val gainDb: Float, val color: Int)

    private val currentDots = mutableListOf<Dot>()
    private val prevDots = mutableListOf<Dot>()

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
    fun setGains(gains: FloatArray, animateReveal: Boolean = false) {
        if (animateReveal) {
            savePreviousState()
        }

        isPeq = false
        val len = minOf(gains.size, BAND_COUNT)

        for (i in 0 until len) {
            bandGains[i] = gains[i].coerceIn(DB_MIN, DB_MAX)
        }

        for (i in len until BAND_COUNT) {
            bandGains[i] = 0f
        }

        updateInternalState()

        if (animateReveal) {
            startMorphAnimation()
        } else {
            animationProgress = 1f
            invalidate()
        }
    }

    /**
     * Updates the wave shape to reflect a parametric EQ response.
     *
     * @param bands A list of PEQ bands.
     */
    fun setPeqBands(bands: List<ParsedBand>, animateReveal: Boolean = false) {
        if (animateReveal) {
            savePreviousState()
        }

        isPeq = true
        peqBands = bands
        bandDelays = FloatArray(bands.size) { (Math.random() * 0.5).toFloat() }

        updateInternalState()

        if (animateReveal) {
            startMorphAnimation()
        } else {
            animationProgress = 1f
            invalidate()
        }
    }

    private fun savePreviousState() {
        System.arraycopy(currentCurve, 0, previousCurve, 0, PEQ_CURVE_RESOLUTION)
        prevIsPeq = isPeq
        prevPeqBands = peqBands.toList()
        prevBandDelays = bandDelays.copyOf()
        prevDots.clear()
        prevDots.addAll(currentDots)
    }

    private fun updateInternalState() {
        currentDots.clear()
        if (isPeq) {
            for (j in 0 until PEQ_CURVE_RESOLUTION) {
                val xFrac = j.toFloat() / (PEQ_CURVE_RESOLUTION - 1)
                val frequency = BASE_FREQ_HZ * 2f.pow(xFrac * BAND_COUNT - 0.5f)
                currentCurve[j] = getPeqResponseAt(frequency, peqBands)
            }
            for ((gain, _, frequencyHz, color) in peqBands) {
                val log2fRatio = kotlin.math.log2(frequencyHz / BASE_FREQ_HZ)
                val xFrac = (log2fRatio + 0.5f) / BAND_COUNT
                currentDots.add(Dot(xFrac, gain, color))
            }
        } else {
            for (j in 0 until PEQ_CURVE_RESOLUTION) {
                val xFrac = j.toFloat() / (PEQ_CURVE_RESOLUTION - 1)
                val bandPos = xFrac * BAND_COUNT - 0.5f
                if (bandPos <= 0f) {
                    currentCurve[j] = bandGains[0]
                } else if (bandPos >= BAND_COUNT - 1f) {
                    currentCurve[j] = bandGains[BAND_COUNT - 1]
                } else {
                    val i = bandPos.toInt()
                    val t = bandPos - i
                    val y1 = bandGains[i]
                    val y2 = bandGains[i + 1]
                    val yt = y1 * (1 - t) * (1 - t) * (1 + 2 * t) + y2 * t * t * (3 - 2 * t)
                    currentCurve[j] = yt
                }
            }
            for (i in 0 until BAND_COUNT) {
                val xFrac = (i + 0.5f) / BAND_COUNT
                currentDots.add(Dot(xFrac, bandGains[i], 0))
            }
        }
    }

    private fun startMorphAnimation() {
        waveAnimator?.cancel()
        animationProgress = 0f
        waveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                animationProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
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

        val stepX = graphW / (PEQ_CURVE_RESOLUTION - 1)
        val slotW = graphW / BAND_COUNT

        var maxAbsGain = 0f

        for (i in 0 until PEQ_CURVE_RESOLUTION) {
            val gain = if (animationProgress < 1f) {
                previousCurve[i] + (currentCurve[i] - previousCurve[i]) * animationProgress
            } else {
                currentCurve[i]
            }

            val absGain = abs(gain)

            if (absGain > maxAbsGain) {
                maxAbsGain = absGain
            }
        }

        // Ensure there's some range to avoid division by zero and overly flat look when all zeros
        val dynamicDbMax = maxOf(maxAbsGain, MIN_DYNAMIC_RANGE_DB)

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

            // Draw previous PEQ individual lines fading out
            if (animationProgress < 1f && prevIsPeq) {
                val alphaMult = 1f - animationProgress

                drawIndividualPeqBands(
                        canvas = canvas,
                        bands = prevPeqBands,
                        alphaMult = alphaMult,
                        sweepProgress = 1f,
                        delays = prevBandDelays,
                        dynamicDbMax = dynamicDbMax,
                        graphStartX = graphStartX,
                        stepX = stepX,
                        midY = midY,
                        drawH = drawH,
                        viewHeight = viewHeight,
                        graphW = graphW
                )
            }

            // Draw current PEQ individual lines drawing in
            if (isPeq) {
                val alphaMult = if (animationProgress < 1f) animationProgress else 1f
                val sweep = animationProgress

                drawIndividualPeqBands(
                        canvas = canvas,
                        bands = peqBands,
                        alphaMult = alphaMult,
                        sweepProgress = sweep,
                        delays = bandDelays,
                        dynamicDbMax = dynamicDbMax,
                        graphStartX = graphStartX,
                        stepX = stepX,
                        midY = midY,
                        drawH = drawH,
                        viewHeight = viewHeight,
                        graphW = graphW
                )
            }

            // Draw the morphed main curve
            wavePath.reset()
            for (i in 0 until PEQ_CURVE_RESOLUTION) {
                val x = graphStartX + i * stepX
                val gain = if (animationProgress < 1f) {
                    previousCurve[i] + (currentCurve[i] - previousCurve[i]) * animationProgress
                } else {
                    currentCurve[i]
                }

                val normalizedGain = gain / dynamicDbMax
                val y = midY - normalizedGain * (drawH / 2f)

                if (i == 0) {
                    wavePath.moveTo(x, y)
                } else {
                    wavePath.lineTo(x, y)
                }
            }

            fillPath.set(wavePath)
            fillPath.lineTo(viewWidth, graphH)
            fillPath.lineTo(graphStartX, graphH)
            fillPath.close()

            drawPath(fillPath, fillPaint)

            wavePaint.shader = gradient
            drawPath(wavePath, wavePaint)

            // Draw dots
            val handleOuterRadius = DOT_RADIUS * resources.displayMetrics.density
            val originalHandleColor = handleFillPaint.color
            val originalDotColor = dotPaint.color

            // Draw previous dots fading out
            if (animationProgress < 1f && prevDots.isNotEmpty()) {
                val scale = 1f - animationProgress

                for ((xFrac, gainDb, color) in prevDots) {
                    val x = graphStartX + xFrac * graphW
                    // Only draw if within graph bounds
                    if (x in graphStartX..viewWidth) {
                        val normalizedGain = gainDb / dynamicDbMax
                        val y = midY - normalizedGain * (drawH / 2f)

                        val paint = if (prevIsPeq) {
                            handleFillPaint
                        } else {
                            dotPaint
                        }

                        val baseColor = if (prevIsPeq) {
                            if (color == 0) originalHandleColor else color.toQuarterAlpha()
                        } else {
                            originalDotColor
                        }

                        paint.color = ColorUtils.setAlphaComponent(
                                baseColor, (Color.alpha(baseColor) * scale).toInt())

                        drawCircle(x, y, handleOuterRadius * scale, paint)
                    }
                }
            }

            // Draw current dots scaling in
            val currentScale = if (animationProgress < 1f) animationProgress else 1f
            for ((xFrac, gainDb, color) in currentDots) {
                val x = graphStartX + xFrac * graphW

                if (x in graphStartX..viewWidth) {
                    val normalizedGain = gainDb / dynamicDbMax
                    val y = midY - normalizedGain * (drawH / 2f)

                    val paint = if (isPeq) {
                        handleFillPaint
                    } else {
                        dotPaint
                    }

                    val baseColor = if (isPeq) {
                        if (color == 0) originalHandleColor else color.toQuarterAlpha()
                    } else {
                        originalDotColor
                    }

                    if (animationProgress < 1f) {
                        paint.color = ColorUtils.setAlphaComponent(
                                baseColor, (Color.alpha(baseColor) * currentScale).toInt())
                    } else {
                        paint.color = baseColor
                    }

                    drawCircle(x, y, handleOuterRadius * currentScale, paint)
                }
            }

            handleFillPaint.color = originalHandleColor
            dotPaint.color = originalDotColor
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

    private fun drawIndividualPeqBands(
            canvas: Canvas,
            bands: List<ParsedBand>,
            alphaMult: Float,
            sweepProgress: Float,
            delays: FloatArray,
            dynamicDbMax: Float,
            graphStartX: Float,
            stepX: Float,
            midY: Float,
            drawH: Float,
            viewHeight: Float,
            graphW: Float
    ) {
        val originalColor = peqBandPaint.color
        for (i in bands.indices) {
            val band = bands[i]
            val delay = delays.getOrElse(i) { 0f }

            val bandSweep = if (sweepProgress >= 1f) 1f else {
                ((sweepProgress - delay) / (1f - delay)).coerceIn(0f, 1f)
            }

            if (bandSweep <= 0f) continue

            singleBandPath.reset()
            for (j in 0 until PEQ_CURVE_RESOLUTION) {
                val x = graphStartX + j * stepX
                val frequency = BASE_FREQ_HZ * 2f.pow((j.toFloat() / (PEQ_CURVE_RESOLUTION - 1)) * BAND_COUNT - 0.5f)
                val bandGain = getSingleBandResponseAt(frequency, band.gain, band.q, band.frequencyHz, band.type)
                val normalizedGain = bandGain / dynamicDbMax
                val y = midY - normalizedGain * (drawH / 2f)

                if (j == 0) {
                    singleBandPath.moveTo(x, y)
                } else {
                    singleBandPath.lineTo(x, y)
                }
            }

            val baseColor = if (band.color == 0) originalColor else band.color.toQuarterAlpha()
            peqBandPaint.color = ColorUtils.setAlphaComponent(
                    baseColor, (Color.alpha(baseColor) * alphaMult * bandSweep).toInt())

            if (bandSweep < 1f) {
                canvas.withSave {
                    val clipRight = graphStartX + graphW * bandSweep
                    canvas.clipRect(0f, 0f, clipRight, viewHeight)
                    drawPath(singleBandPath, peqBandPaint)
                }
            } else {
                canvas.drawPath(singleBandPath, peqBandPaint)
            }
        }
        peqBandPaint.color = originalColor
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

        gradient = LinearGradient(
                textWidth, 0f, w.toFloat(), 0f,
                intArrayOf(primary, secondary),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
        )
        wavePaint.shader = gradient

        val primaryFaint = ColorUtils.setAlphaComponent(primary, 0x1A) // 10%
        val secondaryFaint = ColorUtils.setAlphaComponent(secondary, 0x1A) // 10%

        fillPaint.shader = LinearGradient(
                textWidth, 0f, w.toFloat(), 0f,
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

        /**
         * Calculates the approximate magnitude response in dB of a single parametric EQ band at a given frequency.
         * Uses the analog prototype response of peaking filters.
         */
        @Suppress("LocalVariableName")
        private fun getSingleBandResponseAt(frequency: Float, gainDb: Float, qFactor: Float, centerFrequency: Float, type: Int = 0): Float {
            if (gainDb == 0f || centerFrequency <= 0f) return 0f

            // Use a standard sample rate for visualization
            val sampleRate = 48000f
            var f0 = centerFrequency
            val maxFreq = sampleRate * 0.49f
            if (f0 > maxFreq) f0 = maxFreq
            if (f0 < 1f) f0 = 1f

            val safeQ = if (qFactor < 0.01f) 0.01f else qFactor
            val A = 10f.pow(gainDb / 40f)
            val omega = 2f * Math.PI.toFloat() * f0 / sampleRate
            val cosOmega = kotlin.math.cos(omega)
            val sinOmega = kotlin.math.sin(omega)
            val alpha = sinOmega / (2f * safeQ)

            val b0: Float
            val b1: Float
            val b2: Float
            val a0_inv: Float
            val a1: Float
            val a2: Float

            when (type) {
                1 -> { // Low Shelf
                    val twoSqrtAAlpha = 2f * kotlin.math.sqrt(A) * alpha
                    a0_inv = 1f / ((A + 1f) + (A - 1f) * cosOmega + twoSqrtAAlpha)
                    b0 = A * ((A + 1f) - (A - 1f) * cosOmega + twoSqrtAAlpha) * a0_inv
                    b1 = 2f * A * ((A - 1f) - (A + 1f) * cosOmega) * a0_inv
                    b2 = A * ((A + 1f) - (A - 1f) * cosOmega - twoSqrtAAlpha) * a0_inv
                    a1 = -2f * ((A - 1f) + (A + 1f) * cosOmega) * a0_inv
                    a2 = ((A + 1f) + (A - 1f) * cosOmega - twoSqrtAAlpha) * a0_inv
                }
                2 -> { // High Shelf
                    val twoSqrtAAlpha = 2f * kotlin.math.sqrt(A) * alpha
                    a0_inv = 1f / ((A + 1f) - (A - 1f) * cosOmega + twoSqrtAAlpha)
                    b0 = A * ((A + 1f) + (A - 1f) * cosOmega + twoSqrtAAlpha) * a0_inv
                    b1 = -2f * A * ((A - 1f) + (A + 1f) * cosOmega) * a0_inv
                    b2 = A * ((A + 1f) + (A - 1f) * cosOmega - twoSqrtAAlpha) * a0_inv
                    a1 = 2f * ((A - 1f) - (A + 1f) * cosOmega) * a0_inv
                    a2 = ((A + 1f) - (A - 1f) * cosOmega - twoSqrtAAlpha) * a0_inv
                }
                else -> { // Peaking
                    a0_inv = 1f / (1f + alpha / A)
                    b0 = (1f + alpha * A) * a0_inv
                    b1 = (-2f * cosOmega) * a0_inv
                    b2 = (1f - alpha * A) * a0_inv
                    a1 = (-2f * cosOmega) * a0_inv
                    a2 = (1f - alpha / A) * a0_inv
                }
            }

            val w = 2f * Math.PI.toFloat() * frequency / sampleRate
            val cosW = kotlin.math.cos(w)
            val sinW = kotlin.math.sin(w)
            val cos2W = kotlin.math.cos(2f * w)
            val sin2W = kotlin.math.sin(2f * w)

            val numReal = b0 + b1 * cosW + b2 * cos2W
            val numImag = b1 * sinW + b2 * sin2W
            val denReal = 1f + a1 * cosW + a2 * cos2W
            val denImag = a1 * sinW + a2 * sin2W

            val magSq = (numReal * numReal + numImag * numImag) / (denReal * denReal + denImag * denImag)
            return 10f * kotlin.math.log10(magSq)
        }

        /**
         * Calculates the approximate magnitude response in dB of a parametric EQ at a given frequency.
         */
        private fun getPeqResponseAt(frequency: Float, bands: List<ParsedBand>): Float {
            var totalGainDb = 0f

            for ((gainDb, qFactor, centerFrequency, _, type) in bands) {
                totalGainDb += getSingleBandResponseAt(frequency, gainDb, qFactor, centerFrequency, type)
            }

            return totalGainDb
        }
    }
}

