package app.simple.felicity.decorations.seekbars

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.VibrationEffect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import app.simple.felicity.decorations.drawables.ThumbPillDrawable
import app.simple.felicity.decorations.seekbars.FelicityEqualizerSliders.Companion.NO_ACTIVE_BAND
import app.simple.felicity.decorations.seekbars.FelicityEqualizerSliders.Companion.PREAMP_BAND_INDEX
import app.simple.felicity.decorations.typeface.TypeFace
import app.simple.felicity.decorations.utils.VibrateUtils.vibrateEffect
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme
import kotlin.math.abs
import kotlin.math.exp

/**
 * A 10-band graphic equalizer slider view plus a dedicated pre-amplifier slider,
 * spanning 31 Hz to 16 kHz with an overall gain stage.
 *
 * The preamp column is rendered at the far left with a tinted background highlight
 * and a vertical separator that visually distinguishes it from the 10 EQ-band columns.
 * Each band (including the preamp) is rendered as a vertical slider with a fader-style
 * pill thumb featuring three horizontal grip lines. A smooth Catmull-Rom spline
 * (EQ bands only) connects the 10 band-gain thumbs to represent the frequency curve.
 *
 * The text-gap calculation is restructured so [textGapPx] exclusively controls the gap
 * between the track and the label row without accumulating extra space at the bottom of
 * the view when the value is changed.
 *
 * @author Hamza417
 */
class FelicityEqualizerSliders @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), ThemeChangedListener, SharedPreferences.OnSharedPreferenceChangeListener {

    // -------------------------------------------------------------------------
    // Public interface
    // -------------------------------------------------------------------------

    /**
     * Callback fired whenever a band's gain changes due to user interaction.
     */
    fun interface OnBandChangedListener {
        /**
         * @param bandIndex [PREAMP_BAND_INDEX] for the preamp, 0-9 for EQ bands
         * @param gain      current gain in dB, range [MIN_DB .. MAX_DB]
         * @param fromUser  true when the change originated from a touch event
         */
        fun onBandChanged(bandIndex: Int, gain: Float, fromUser: Boolean)
    }

    private var bandChangedListener: OnBandChangedListener? = null

    fun setOnBandChangedListener(listener: OnBandChangedListener?) {
        bandChangedListener = listener
    }

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    companion object {
        /** Number of EQ frequency bands (31 Hz → 16 kHz). */
        private const val BAND_COUNT = 10

        /** Total visual columns: 1 preamp + 10 EQ bands. */
        private const val TOTAL_COLUMNS = BAND_COUNT + 1

        const val MIN_DB = -15f
        const val MAX_DB = 15f
        private const val DEFAULT_DB = 0f

        /**
         * Band index used in [OnBandChangedListener] callbacks to identify the
         * pre-amplifier slider. The 10 EQ bands use indices 0–9.
         */
        const val PREAMP_BAND_INDEX = -1

        /** Sentinel value for [activeBandIndex] meaning no band is currently touched. */
        private const val NO_ACTIVE_BAND = Int.MIN_VALUE

        /** Human-readable frequency labels for each EQ band. */
        val FREQUENCY_LABELS = arrayOf(
                "31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz",
                "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz"
        )

        private const val OVERSCROLL_DECAY_FACTOR = 400f
        private const val MAX_OVERSCROLL_DP = 128f
        private const val TAG = "FelicityEqualizerSliders"
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Raw gain values for EQ bands 0–9 in dB. */
    private val gains = FloatArray(BAND_COUNT) { DEFAULT_DB }

    /** Animated display values for EQ bands 0–9 (driven by ValueAnimator). */
    private val displayGains = FloatArray(BAND_COUNT) { DEFAULT_DB }
    private val gainAnimators = arrayOfNulls<ValueAnimator>(BAND_COUNT)

    /** Raw gain for the preamp in dB. */
    private var preampGain: Float = DEFAULT_DB

    /** Animated display value for the preamp. */
    private var preampDisplayGain: Float = DEFAULT_DB
    private var preampGainAnimator: ValueAnimator? = null

    // -------------------------------------------------------------------------
    // Scroll state
    // -------------------------------------------------------------------------

    private var scrollOffset = 0f
    private var maxScroll = 0f
    private var centeredMode = false
    private var centeringOffset = 0f

    // -------------------------------------------------------------------------
    // Overscroll spring / fling
    // -------------------------------------------------------------------------

    private val maxOverscrollPx get() = MAX_OVERSCROLL_DP * resources.displayMetrics.density

    private val scrollOffsetProperty = object : FloatPropertyCompat<FelicityEqualizerSliders>("scrollOffset") {
        override fun getValue(view: FelicityEqualizerSliders): Float = view.scrollOffset
        override fun setValue(view: FelicityEqualizerSliders, value: Float) {
            view.scrollOffset = value.coerceIn(-maxOverscrollPx, maxScroll + maxOverscrollPx)
            view.invalidate()
        }
    }

    private val scrollSpring = SpringAnimation(this, scrollOffsetProperty).apply {
        spring = SpringForce().apply {
            stiffness = SpringForce.STIFFNESS_LOW
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
        }
    }

    private val scrollFling = FlingAnimation(this, scrollOffsetProperty).apply {
        friction = 1.1f
        addEndListener { _, _, _, _ -> snapScrollToBounds() }
    }

    private var velocityTracker: VelocityTracker? = null

    // -------------------------------------------------------------------------
    // Touch state
    // -------------------------------------------------------------------------

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var activeBandIndex = NO_ACTIVE_BAND
    private var isScrollGesture = false
    private var isBandGesture = false
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var scrollOffsetAtDown = 0f
    private var thumbYAtDown = 0f

    // -------------------------------------------------------------------------
    // Press-scale animations per band (EQ bands 0–9 + preamp)
    // -------------------------------------------------------------------------

    private val pressScales = FloatArray(BAND_COUNT) { 1f }
    private val pressScaleAnimators = arrayOfNulls<ValueAnimator>(BAND_COUNT)
    private var preampPressScale: Float = 1f
    private var preampPressScaleAnimator: ValueAnimator? = null

    // -------------------------------------------------------------------------
    // Layout geometry (recomputed in onSizeChanged)
    // -------------------------------------------------------------------------

    private var columnWidth = 0f
    private var trackTop = 0f
    private var trackBottom = 0f
    private var trackLength = 0f

    /**
     * Top of the label row, computed in [recalculateLayout] as a fixed position
     * relative to the view bottom. [textGapPx] controls the gap between [trackBottom]
     * and this point without affecting the bottom margin.
     */
    private var textRegionTop = 0f
    private var contentWidth = 0f

    // -------------------------------------------------------------------------
    // Dimension constants
    // -------------------------------------------------------------------------

    private val d = resources.displayMetrics.density

    var bandSpacingDp: Float = 50f
        set(value) {
            field = value.coerceAtLeast(36f)
            if (width > 0 && height > 0) {
                recalculateLayout(width, height)
                invalidate()
            }
        }

    private val thumbHalfWidthPx = 12f * d
    private val thumbHalfHeightPx = 24f * d
    private val trackStrokePx = 5f * d
    private val bezierStrokePx = 2.5f * d
    private val thumbRingStrokePx = 3f * d
    private val gripLineStrokePx = 1.5f * d
    private val gripLineHalfLengthFraction = 0.42f
    private val gripLineSpacingFraction = 0.22f
    private val sliderVerticalPaddingPx = thumbHalfHeightPx + 4f * d
    private val pressRingOutsetPx = 5f * d

    /**
     * Gap between the bottom of the track (plus its thumb area) and the top of the
     * label row. Affects only this visual gap; it does not accumulate extra space at
     * the bottom of the view.
     */
    private val textGapPx = 40f * d

    // -------------------------------------------------------------------------
    // Shadow / glow state
    // -------------------------------------------------------------------------

    private var shadowEffectEnabled = false

    // -------------------------------------------------------------------------
    // Colors
    // -------------------------------------------------------------------------

    @ColorInt
    private var trackColor = Color.DKGRAY

    @ColorInt
    private var accentColor = Color.WHITE

    @ColorInt
    private var thumbRingColor = Color.WHITE

    @ColorInt
    private var thumbInnerColor = Color.WHITE

    @ColorInt
    private var primaryTextColor = Color.WHITE

    @ColorInt
    private var secondaryTextColor = Color.GRAY

    @ColorInt
    private var centerLineColor = Color.GRAY

    @ColorInt
    private var bezierColor = Color.WHITE

    // -------------------------------------------------------------------------
    // Paints
    // -------------------------------------------------------------------------

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val bezierPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    /**
     * Filled gradient paint used to draw the translucent fade below the bezier curve.
     * The [LinearGradient] shader is rebuilt dynamically in [drawBezierFill] each frame
     * because the gradient bounds change as the user drags the band thumbs.
     */
    private val bezierFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** Unified pill-thumb drawable shared across all columns (including the preamp). */
    private val thumbPillDrawable = ThumbPillDrawable(ThumbPillDrawable.Orientation.VERTICAL).apply {
        ringStrokePx = thumbRingStrokePx
        gripLineStrokePx = this@FelicityEqualizerSliders.gripLineStrokePx
        gripLineHalfLengthFraction = this@FelicityEqualizerSliders.gripLineHalfLengthFraction
        gripLineSpacingFraction = this@FelicityEqualizerSliders.gripLineSpacingFraction
    }

    private val trackProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }

    private val pressRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val freqTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    /**
     * Semi-transparent fill drawn behind the preamp column to visually distinguish it
     * from the 10 EQ-band columns.
     */
    private val preampBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /**
     * Vertical separator line drawn between the preamp column and the first EQ-band column.
     */
    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }

    private val bezierPath = Path()
    private val bezierFillPath = Path()
    private val pressRingRect = RectF()

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    init {
        isClickable = true
        isFocusable = true
        if (!isInEditMode) {
            applyThemeColors()
        }
        applyPaintColors()
        setupTextPaints()
        if (!isInEditMode) {
            updateShadowEffect()
        }
    }

    // -------------------------------------------------------------------------
    // Theme application
    // -------------------------------------------------------------------------

    private fun applyThemeColors() {
        accentColor = ThemeManager.accent.primaryAccentColor
        trackColor = ThemeManager.theme.viewGroupTheme.highlightColor
        thumbRingColor = Color.WHITE
        thumbInnerColor = accentColor
        primaryTextColor = ThemeManager.theme.textViewTheme.primaryTextColor
        secondaryTextColor = ThemeManager.theme.textViewTheme.secondaryTextColor
        centerLineColor = ThemeManager.theme.viewGroupTheme.dividerColor
        bezierColor = accentColor
    }

    private fun applyPaintColors() {
        trackPaint.color = trackColor
        trackPaint.strokeWidth = trackStrokePx

        bezierPaint.color = bezierColor
        bezierPaint.strokeWidth = bezierStrokePx


        thumbPillDrawable.fillColor = thumbInnerColor
        thumbPillDrawable.ringColor = thumbRingColor

        trackProgressPaint.color = accentColor
        trackProgressPaint.strokeWidth = trackStrokePx


        pressRingPaint.color = accentColor
        pressRingPaint.strokeWidth = 1.5f * d

        centerLinePaint.color = centerLineColor
        centerLinePaint.strokeWidth = 1f * d
        centerLinePaint.alpha = 80

        freqTextPaint.color = secondaryTextColor
        freqTextPaint.textSize = 9.5f * d

        valueTextPaint.color = primaryTextColor
        valueTextPaint.textSize = 10.5f * d

        // Preamp background: accent color with low alpha
        preampBackgroundPaint.color = accentColor
        preampBackgroundPaint.alpha = 18

        separatorPaint.color = centerLineColor
        separatorPaint.strokeWidth = 1f * d
        separatorPaint.alpha = 90

        // Re-apply shadow layers so the glow color tracks accent/theme changes.
        applyShadowLayers()
    }

    private fun setupTextPaints() {
        if (!isInEditMode) {
            val tf = TypeFace.getBoldTypeFace(context)
            freqTextPaint.typeface = tf
            valueTextPaint.typeface = tf
        }
    }

    /**
     * Applies or removes GPU shadow layers on [bezierPaint] and [trackProgressPaint].
     *
     * [Paint.setShadowLayer] is composited on the GPU by the HWUI RenderThread pipeline
     * (API 28+) without requiring [LAYER_TYPE_SOFTWARE], so the view stays hardware
     * accelerated and renders at full frame rate. The shadow radius and color are
     * derived from the current accent color so the glow always matches the UI theme.
     *
     * Called from [updateShadowEffect] when the preference changes and from
     * [applyPaintColors] whenever the accent or theme colors are updated.
     */
    private fun applyShadowLayers() {
        if (shadowEffectEnabled) {
            val r = Color.red(bezierColor)
            val g = Color.green(bezierColor)
            val b = Color.blue(bezierColor)
            val glowColor = Color.argb(200, r, g, b)
            val progressGlowColor = Color.argb(160, r, g, b)

            bezierPaint.setShadowLayer(10f * d, 0f, 0f, glowColor)
            trackProgressPaint.setShadowLayer(8f * d, 0f, 0f, progressGlowColor)
        } else {
            bezierPaint.clearShadowLayer()
            trackProgressPaint.clearShadowLayer()
        }
    }

    /**
     * Reads the shadow-effect preference, updates the shadow layers on the relevant
     * paints, and ensures the view stays on the hardware-accelerated layer at all times.
     */
    private fun updateShadowEffect() {
        shadowEffectEnabled = AppearancePreferences.isShadowEffectOn()
        applyShadowLayers()
        setLayerType(LAYER_TYPE_HARDWARE, null)
        invalidate()
    }

    // -------------------------------------------------------------------------
    // Layout geometry
    // -------------------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateLayout(w, h)
    }

    private fun recalculateLayout(w: Int, h: Int) {
        val availableWidth = w - paddingStart - paddingEnd
        columnWidth = bandSpacingDp * d
        contentWidth = columnWidth * TOTAL_COLUMNS

        // The label row sits at a fixed position anchored from the view bottom.
        // sliderVerticalPaddingPx below the track provides space for the thumb cap.
        // textGapPx exclusively controls the gap from trackBottom to textRegionTop.
        val twoLineTextHeight = freqTextPaint.fontSpacing + valueTextPaint.fontSpacing
        textRegionTop = (h - paddingBottom).toFloat() - twoLineTextHeight - sliderVerticalPaddingPx

        trackTop = paddingTop + sliderVerticalPaddingPx
        trackBottom = textRegionTop - textGapPx
        trackLength = trackBottom - trackTop

        centeredMode = contentWidth <= availableWidth
        // centeringOffset includes paddingStart so all column positions automatically
        // respect horizontal padding without any additional adjustments in the draw methods.
        centeringOffset = paddingStart.toFloat() + if (centeredMode) (availableWidth - contentWidth) / 2f else 0f
        maxScroll = if (centeredMode) 0f else (contentWidth - availableWidth).coerceAtLeast(0f)
        scrollOffset = scrollOffset.coerceIn(0f, maxScroll)
    }

    // -------------------------------------------------------------------------
    // Public gain API
    // -------------------------------------------------------------------------

    /**
     * Sets the gain for an EQ band.
     *
     * @param bandIndex 0-based EQ band index (0 = 31 Hz, 9 = 16 kHz).
     * @param gain      Gain in dB, clamped to the range -15..+15.
     * @param animate   Animate the thumb to the new position.
     * @param fromUser  True when the change was initiated by the user.
     */
    fun setBandGain(bandIndex: Int, gain: Float, animate: Boolean = false, fromUser: Boolean = false) {
        if (bandIndex !in 0 until BAND_COUNT) return
        val clamped = gain.coerceIn(MIN_DB, MAX_DB)
        gains[bandIndex] = clamped
        gainAnimators[bandIndex]?.cancel()
        if (animate) {
            val start = displayGains[bandIndex]
            gainAnimators[bandIndex] = ValueAnimator.ofFloat(start, clamped).apply {
                duration = 300L
                interpolator = DecelerateInterpolator()
                addUpdateListener { displayGains[bandIndex] = it.animatedValue as Float; invalidate() }
                start()
            }
        } else {
            displayGains[bandIndex] = clamped
            invalidate()
        }
        if (fromUser) bandChangedListener?.onBandChanged(bandIndex, clamped, true)
    }

    /** Returns the current gain for EQ [bandIndex] in dB. */
    fun getBandGain(bandIndex: Int): Float = if (bandIndex in 0 until BAND_COUNT) gains[bandIndex] else 0f

    /**
     * Sets the pre-amplifier gain.
     *
     * @param gain     Gain in dB, clamped to the range -15..+15.
     * @param animate  Animate the thumb to the new position.
     * @param fromUser True when the change was initiated by the user.
     */
    fun setPreampGain(gain: Float, animate: Boolean = false, fromUser: Boolean = false) {
        val clamped = gain.coerceIn(MIN_DB, MAX_DB)
        preampGain = clamped
        preampGainAnimator?.cancel()
        if (animate) {
            val start = preampDisplayGain
            preampGainAnimator = ValueAnimator.ofFloat(start, clamped).apply {
                duration = 300L
                interpolator = DecelerateInterpolator()
                addUpdateListener { preampDisplayGain = it.animatedValue as Float; invalidate() }
                start()
            }
        } else {
            preampDisplayGain = clamped
            invalidate()
        }
        if (fromUser) bandChangedListener?.onBandChanged(PREAMP_BAND_INDEX, clamped, true)
    }

    /** Returns the current preamp gain in dB. */
    fun getPreampGain(): Float = preampGain

    /** Resets all EQ bands and the preamp to 0 dB. */
    fun resetAllBands(animate: Boolean = true) {
        for (i in 0 until BAND_COUNT) setBandGain(i, DEFAULT_DB, animate)
        setPreampGain(DEFAULT_DB, animate)
    }

    /** Sets all EQ band gains from an array (does not affect the preamp). */
    fun setAllGains(allGains: FloatArray, animate: Boolean = false) {
        for (i in 0 until BAND_COUNT) {
            setBandGain(i, if (i < allGains.size) allGains[i] else DEFAULT_DB, animate)
        }
    }

    // -------------------------------------------------------------------------
    // Geometry helpers
    // -------------------------------------------------------------------------

    private fun gainToThumbY(gain: Float): Float {
        val fraction = (gain - MIN_DB) / (MAX_DB - MIN_DB)
        return trackBottom - fraction * trackLength
    }

    private fun thumbYToGain(y: Float): Float {
        if (trackLength <= 0f) return DEFAULT_DB
        return (MIN_DB + (trackBottom - y) / trackLength * (MAX_DB - MIN_DB)).coerceIn(MIN_DB, MAX_DB)
    }

    /** Returns the view-space X center of the given internal column (0 = preamp, 1–10 = EQ). */
    private fun columnCenterX(columnIndex: Int): Float =
        centeringOffset + columnIndex * columnWidth + columnWidth / 2f - scrollOffset

    /** Returns the view-space X center of EQ [bandIndex] (0–9). */
    private fun bandCenterX(bandIndex: Int): Float = columnCenterX(bandIndex + 1)

    /** Returns the view-space X center of the preamp column. */
    private fun preampCenterX(): Float = columnCenterX(0)

    /**
     * Returns [PREAMP_BAND_INDEX] when [x] is over the preamp column, 0–9 for EQ bands,
     * or [NO_ACTIVE_BAND] when outside all columns.
     */
    private fun bandIndexAtX(x: Float): Int {
        val contentX = x - centeringOffset + scrollOffset
        if (contentX < 0f || contentX >= contentWidth) return NO_ACTIVE_BAND
        val col = (contentX / columnWidth).toInt().coerceIn(0, TOTAL_COLUMNS - 1)
        return if (col == 0) PREAMP_BAND_INDEX else col - 1
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (trackLength <= 0f) return
        val visibleLeft = -columnWidth
        val visibleRight = width.toFloat() + columnWidth

        drawPreampBackground(canvas)
        drawCenterLine(canvas)
        drawBezierFill(canvas)
        drawBezierCurve(canvas)
        drawPreampSeparator(canvas)
        drawTracksAndThumbs(canvas, visibleLeft, visibleRight)
        drawPreampTrackAndThumb(canvas)
        drawLabels(canvas, visibleLeft, visibleRight)
        drawPreampLabel(canvas)
    }

    /**
     * Draws the tinted highlight rectangle behind the preamp column so it reads as
     * a distinct region from the 10 EQ-band columns.
     */
    private fun drawPreampBackground(canvas: Canvas) {
        val left = centeringOffset - scrollOffset + 3f * d
        val right = left + columnWidth - 6f * d
        val top = trackTop - sliderVerticalPaddingPx * 0.5f
        val bottom = (height - paddingBottom).toFloat()
        canvas.drawRoundRect(left, top, right, bottom, 10f * d, 10f * d, preampBackgroundPaint)
    }

    /**
     * Draws a hairline vertical separator between the preamp column and the first EQ column
     * to reinforce the visual boundary.
     */
    private fun drawPreampSeparator(canvas: Canvas) {
        val x = centeringOffset + columnWidth - scrollOffset
        val top = trackTop - sliderVerticalPaddingPx * 0.5f
        val bottom = (height - paddingBottom).toFloat()
        canvas.drawLine(x, top, x, bottom, separatorPaint)
    }

    private fun drawCenterLine(canvas: Canvas) {
        val zeroY = gainToThumbY(0f)
        // Span the full content including the preamp column.
        val lineLeft = centeringOffset - scrollOffset
        val lineRight = lineLeft + contentWidth
        canvas.drawLine(lineLeft, zeroY, lineRight, zeroY, centerLinePaint)
    }

    /**
     * Draws the track, thumb, progress segment, and grip lines for the preamp column.
     */
    private fun drawPreampTrackAndThumb(canvas: Canvas) {
        val cx = preampCenterX()
        val thumbY = gainToThumbY(preampDisplayGain)
        val zeroY = gainToThumbY(0f)
        drawColumnTrackAndThumb(canvas, cx, thumbY, zeroY, preampPressScale)
    }

    private fun drawTracksAndThumbs(canvas: Canvas, visibleLeft: Float, visibleRight: Float) {
        val zeroY = gainToThumbY(0f)
        for (i in 0 until BAND_COUNT) {
            val cx = bandCenterX(i)
            if (cx < visibleLeft || cx > visibleRight) continue
            drawColumnTrackAndThumb(canvas, cx, gainToThumbY(displayGains[i]), zeroY, pressScales[i])
        }
    }

    /**
     * Draws the track line, progress segment (with optional glow), and pill thumb for a
     * single column at [cx] with the thumb positioned at [thumbY].
     *
     * @param cx        View-space X center of the column.
     * @param thumbY    View-space Y of the thumb center.
     * @param zeroY     View-space Y corresponding to 0 dB reference.
     * @param scale     Current press-scale for the thumb.
     */
    private fun drawColumnTrackAndThumb(canvas: Canvas, cx: Float, thumbY: Float, zeroY: Float, scale: Float) {
        canvas.drawLine(cx, trackTop, cx, trackBottom, trackPaint)

        val progressTop = minOf(zeroY, thumbY)
        val progressBottom = maxOf(zeroY, thumbY)
        if (progressBottom > progressTop) {
            canvas.drawLine(cx, progressTop, cx, progressBottom, trackProgressPaint)
        }

        val halfW = thumbHalfWidthPx * scale
        val halfH = thumbHalfHeightPx * scale

        if (scale > 1f) {
            val haloAlpha = ((scale - 1f) / 0.12f * 80f).toInt().coerceIn(0, 80)
            pressRingRect.set(
                    cx - halfW - pressRingOutsetPx,
                    thumbY - halfH - pressRingOutsetPx,
                    cx + halfW + pressRingOutsetPx,
                    thumbY + halfH + pressRingOutsetPx
            )
            pressRingPaint.alpha = haloAlpha
            canvas.drawRoundRect(
                    pressRingRect,
                    thumbHalfHeightPx + pressRingOutsetPx,
                    thumbHalfHeightPx + pressRingOutsetPx,
                    pressRingPaint
            )
        }

        thumbPillDrawable.setBoundsF(cx - halfW, thumbY - halfH, cx + halfW, thumbY + halfH)
        thumbPillDrawable.draw(canvas)
    }

    /**
     * Draws the Catmull-Rom spline connecting the 10 EQ-band thumbs (not the preamp).
     * The glow effect is applied via [Paint.setShadowLayer] directly on [bezierPaint],
     * keeping rendering on the hardware-accelerated layer at all times.
     */
    private fun drawBezierCurve(canvas: Canvas) {
        bezierPath.reset()
        val pts = Array(BAND_COUNT) { i -> Pair(bandCenterX(i), gainToThumbY(displayGains[i])) }

        bezierPath.moveTo(pts[0].first, pts[0].second)
        for (i in 0 until BAND_COUNT - 1) {
            val p0 = if (i > 0) pts[i - 1] else pts[i]
            val p1 = pts[i]
            val p2 = pts[i + 1]
            val p3 = if (i < BAND_COUNT - 2) pts[i + 2] else pts[i + 1]
            val cp1x = p1.first + (p2.first - p0.first) / 6f
            val cp1y = p1.second + (p2.second - p0.second) / 6f
            val cp2x = p2.first - (p3.first - p1.first) / 6f
            val cp2y = p2.second - (p3.second - p1.second) / 6f
            bezierPath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.first, p2.second)
        }

        canvas.drawPath(bezierPath, bezierPaint)
    }

    /**
     * Draws a translucent gradient fill below the Catmull-Rom spline to add visual depth.
     *
     * The filled path traces the same spline as [drawBezierCurve] and then closes
     * downward to [trackBottom]. A vertical [LinearGradient] fades from the accent
     * color (low opacity) at the topmost visible point of the curve to fully transparent
     * at [trackBottom], so the fill never reaches the label row at the bottom of the view.
     *
     * Must be called BEFORE [drawBezierCurve] in [onDraw] so the stroke renders on top.
     */
    private fun drawBezierFill(canvas: Canvas) {
        bezierFillPath.reset()

        val pts = Array(BAND_COUNT) { i -> Pair(bandCenterX(i), gainToThumbY(displayGains[i])) }

        // Trace the same Catmull-Rom spline as drawBezierCurve.
        bezierFillPath.moveTo(pts[0].first, pts[0].second)
        for (i in 0 until BAND_COUNT - 1) {
            val p0 = if (i > 0) pts[i - 1] else pts[i]
            val p1 = pts[i]
            val p2 = pts[i + 1]
            val p3 = if (i < BAND_COUNT - 2) pts[i + 2] else pts[i + 1]
            val cp1x = p1.first + (p2.first - p0.first) / 6f
            val cp1y = p1.second + (p2.second - p0.second) / 6f
            val cp2x = p2.first - (p3.first - p1.first) / 6f
            val cp2y = p2.second - (p3.second - p1.second) / 6f
            bezierFillPath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.first, p2.second)
        }

        // Close the path straight down to trackBottom, across, and back up to the start,
        // so the filled area sits entirely below the spline line.
        bezierFillPath.lineTo(pts.last().first, trackBottom)
        bezierFillPath.lineTo(pts.first().first, trackBottom)
        bezierFillPath.close()

        // Gradient runs from the topmost (smallest Y) visible curve point to trackBottom.
        // This makes the fill feel anchored to wherever the curve sits at any given moment.
        val topY = pts.minOf { it.second }
        val r = Color.red(bezierColor)
        val g = Color.green(bezierColor)
        val b = Color.blue(bezierColor)

        bezierFillPaint.shader = LinearGradient(
                0f, topY,
                0f, trackBottom,
                Color.argb(52, r, g, b),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        )

        canvas.drawPath(bezierFillPath, bezierFillPaint)
    }

    /** Draws frequency and dB-value labels for the 10 EQ bands. */
    private fun drawLabels(canvas: Canvas, visibleLeft: Float, visibleRight: Float) {
        val freqY = textRegionTop + freqTextPaint.fontSpacing * 0.85f
        val valueY = freqY + freqTextPaint.fontSpacing
        for (i in 0 until BAND_COUNT) {
            val cx = bandCenterX(i)
            if (cx < visibleLeft || cx > visibleRight) continue
            canvas.drawText(FREQUENCY_LABELS[i], cx, freqY, freqTextPaint)
            val gain = displayGains[i]
            valueTextPaint.color = if (abs(gain) < 0.05f) secondaryTextColor else accentColor
            canvas.drawText(formatGain(gain), cx, valueY, valueTextPaint)
        }
    }

    /**
     * Draws the "PREAMP" label and current dB value below the preamp column.
     */
    private fun drawPreampLabel(canvas: Canvas) {
        val cx = preampCenterX()
        if (cx < -columnWidth || cx > width + columnWidth) return
        val freqY = textRegionTop + freqTextPaint.fontSpacing * 0.85f
        val valueY = freqY + freqTextPaint.fontSpacing

        // Draw "PREAMP" in the accent color to match the highlighted region.
        val savedFreqColor = freqTextPaint.color
        freqTextPaint.color = accentColor
        canvas.drawText("PREAMP", cx, freqY, freqTextPaint)
        freqTextPaint.color = savedFreqColor

        valueTextPaint.color = if (abs(preampDisplayGain) < 0.05f) secondaryTextColor else accentColor
        canvas.drawText(formatGain(preampDisplayGain), cx, valueY, valueTextPaint)
    }

    private fun formatGain(gain: Float): String = when {
        abs(gain) < 0.05f -> "0"
        gain > 0f -> "+${"%.1f".format(gain)}"
        else -> "%.1f".format(gain)
    }

    // -------------------------------------------------------------------------
    // Touch handling
    // -------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Only claim the event when the finger lands inside the slider content area.
                // In landscape mode (or any configuration where the content is narrower than
                // the view), touches in the horizontal blank/padding regions are passed back
                // to the parent so they don't block scroll containers or other gestures.
                if (!isTouchWithinContentBounds(event.x)) return false
                handleDown(event)
            }
            MotionEvent.ACTION_MOVE -> {
                handleMove(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handleUp(event)
            }
        }
        performClick()
        return true
    }

    /**
     * Returns true when [x] falls within the scrollable content area in view coordinates.
     *
     * The content spans from [centeringOffset] − [scrollOffset] (left edge of the first
     * column) to that value plus [contentWidth] (right edge of the last column). Touches
     * outside this range — e.g. in horizontal padding or centered-mode blank flanks —
     * should not be consumed by this view.
     */
    private fun isTouchWithinContentBounds(x: Float): Boolean {
        val contentLeft = centeringOffset - scrollOffset
        val contentRight = contentLeft + contentWidth
        return x >= contentLeft && x <= contentRight
    }

    override fun performClick(): Boolean {
        super.performClick(); return true
    }

    private fun handleDown(event: MotionEvent) {
        touchStartX = event.x
        touchStartY = event.y
        scrollOffsetAtDown = scrollOffset
        isScrollGesture = false
        isBandGesture = false

        if (scrollSpring.isRunning) scrollSpring.cancel()
        if (scrollFling.isRunning) scrollFling.cancel()

        velocityTracker?.recycle()
        velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(event)

        val band = bandIndexAtX(event.x)
        if (band != NO_ACTIVE_BAND) {
            thumbYAtDown = gainToThumbY(
                    if (band == PREAMP_BAND_INDEX) preampDisplayGain else displayGains[band]
            )
            startBandPressAnimation(band, true)
            context.vibrateEffect(VibrationEffect.EFFECT_TICK, TAG)
        }
        activeBandIndex = band
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun handleMove(event: MotionEvent) {
        velocityTracker?.addMovement(event)
        val dx = event.x - touchStartX
        val dy = event.y - touchStartY

        if (!isScrollGesture && !isBandGesture) {
            val adx = abs(dx);
            val ady = abs(dy)
            if (adx > touchSlop || ady > touchSlop) {
                isBandGesture = activeBandIndex != NO_ACTIVE_BAND && ady > adx * 0.8f
                isScrollGesture = !isBandGesture && adx > ady * 0.8f
                if (isScrollGesture && activeBandIndex != NO_ACTIVE_BAND) {
                    startBandPressAnimation(activeBandIndex, false)
                }
            }
        }

        when {
            isBandGesture && activeBandIndex != NO_ACTIVE_BAND -> {
                if (isEnabled) {
                    val newGain = thumbYToGain(thumbYAtDown + (event.y - touchStartY)).coerceIn(MIN_DB, MAX_DB)
                    if (activeBandIndex == PREAMP_BAND_INDEX) {
                        val prev = preampGain
                        if (newGain != prev) {
                            if (prev.toInt() != newGain.toInt()) context.vibrateEffect(VibrationEffect.EFFECT_CLICK, TAG)
                            val hitLimit = (newGain == MIN_DB && prev > MIN_DB) || (newGain == MAX_DB && prev < MAX_DB)
                            if (hitLimit) context.vibrateEffect(VibrationEffect.EFFECT_HEAVY_CLICK, TAG)
                            preampGain = newGain
                            preampGainAnimator?.cancel()
                            preampDisplayGain = newGain
                            bandChangedListener?.onBandChanged(PREAMP_BAND_INDEX, newGain, true)
                            invalidate()
                        }
                    } else {
                        val band = activeBandIndex
                        val prev = gains[band]
                        if (newGain != prev) {
                            if (prev.toInt() != newGain.toInt()) context.vibrateEffect(VibrationEffect.EFFECT_CLICK, TAG)
                            val hitLimit = (newGain == MIN_DB && prev > MIN_DB) || (newGain == MAX_DB && prev < MAX_DB)
                            if (hitLimit) context.vibrateEffect(VibrationEffect.EFFECT_HEAVY_CLICK, TAG)
                            gains[band] = newGain
                            gainAnimators[band]?.cancel()
                            displayGains[band] = newGain
                            bandChangedListener?.onBandChanged(band, newGain, true)
                            invalidate()
                        }
                    }
                }
            }
            isScrollGesture -> {
                if (centeredMode) return
                scrollOffset = applyOverscrollResistance(scrollOffsetAtDown - dx)
                invalidate()
            }
        }
    }

    private fun handleUp(@Suppress("UNUSED_PARAMETER") event: MotionEvent) {
        if (activeBandIndex != NO_ACTIVE_BAND) startBandPressAnimation(activeBandIndex, false)

        if (isScrollGesture && !centeredMode) {
            velocityTracker?.computeCurrentVelocity(1000)
            val flingVelocity = -(velocityTracker?.xVelocity ?: 0f)
            if (abs(flingVelocity) > 50f) {
                if (scrollFling.isRunning) scrollFling.cancel()
                scrollFling.setMinValue(-maxOverscrollPx)
                scrollFling.setMaxValue(maxScroll + maxOverscrollPx)
                scrollFling.setStartVelocity(flingVelocity)
                scrollFling.setStartValue(scrollOffset)
                scrollFling.start()
            } else {
                snapScrollToBounds()
            }
        }

        velocityTracker?.recycle(); velocityTracker = null
        activeBandIndex = NO_ACTIVE_BAND
        isScrollGesture = false; isBandGesture = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    // -------------------------------------------------------------------------
    // Overscroll
    // -------------------------------------------------------------------------

    private fun applyOverscrollResistance(rawTarget: Float): Float = when {
        rawTarget < 0f -> {
            val r = -rawTarget
            -(maxOverscrollPx * (1f - exp(-r / OVERSCROLL_DECAY_FACTOR)))
        }
        rawTarget > maxScroll -> {
            val r = rawTarget - maxScroll
            maxScroll + maxOverscrollPx * (1f - exp(-r / OVERSCROLL_DECAY_FACTOR))
        }
        else -> rawTarget
    }

    private fun snapScrollToBounds() {
        val target = scrollOffset.coerceIn(0f, maxScroll)
        if (scrollOffset == target) return
        if (scrollSpring.isRunning) scrollSpring.cancel()
        scrollSpring.setStartValue(scrollOffset)
        scrollSpring.animateToFinalPosition(target)
    }

    // -------------------------------------------------------------------------
    // Press-scale animations
    // -------------------------------------------------------------------------

    /**
     * Dispatches a press animation to the correct per-band slot, handling both the
     * preamp and EQ bands via a single call site.
     *
     * @param bandIndex [PREAMP_BAND_INDEX] for the preamp, 0–9 for EQ bands.
     * @param pressed   True to animate to the pressed scale, false to release.
     */
    private fun startBandPressAnimation(bandIndex: Int, pressed: Boolean) {
        when {
            bandIndex == PREAMP_BAND_INDEX -> startPreampPressAnimation(pressed)
            bandIndex in 0 until BAND_COUNT -> startPressAnimation(bandIndex, pressed)
        }
    }

    private fun startPressAnimation(bandIndex: Int, pressed: Boolean) {
        val target = if (pressed) 1.10f else 1f
        pressScaleAnimators[bandIndex]?.cancel()
        val start = pressScales[bandIndex]
        pressScaleAnimators[bandIndex] = ValueAnimator.ofFloat(start, target).apply {
            duration = if (pressed) 140L else 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener { pressScales[bandIndex] = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun startPreampPressAnimation(pressed: Boolean) {
        val target = if (pressed) 1.10f else 1f
        preampPressScaleAnimator?.cancel()
        val start = preampPressScale
        preampPressScaleAnimator = ValueAnimator.ofFloat(start, target).apply {
            duration = if (pressed) 140L else 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener { preampPressScale = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    // -------------------------------------------------------------------------
    // ThemeChangedListener
    // -------------------------------------------------------------------------

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        super.onThemeChanged(theme, animate)
        applyThemeColors()
        applyPaintColors()
        updateShadowEffect()
    }

    override fun onAccentChanged(accent: Accent) {
        super.onAccentChanged(accent)
        accentColor = accent.primaryAccentColor
        thumbInnerColor = accentColor
        bezierColor = accentColor
        applyPaintColors()
        updateShadowEffect()
    }

    // -------------------------------------------------------------------------
    // SharedPreferences
    // -------------------------------------------------------------------------

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            AppearancePreferences.APP_FONT -> {
                setupTextPaints(); invalidate()
            }
            AppearancePreferences.SHADOW_EFFECT -> updateShadowEffect()
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            registerSharedPreferenceChangeListener()
            ThemeManager.addListener(this)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!isInEditMode) {
            unregisterSharedPreferenceChangeListener()
            ThemeManager.removeListener(this)
        }
        scrollSpring.cancel()
        scrollFling.cancel()
        velocityTracker?.recycle(); velocityTracker = null
        gainAnimators.forEach { it?.cancel() }
        pressScaleAnimators.forEach { it?.cancel() }
        preampGainAnimator?.cancel()
        preampPressScaleAnimator?.cancel()
    }

    // -------------------------------------------------------------------------
    // Measurement
    // -------------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        val resolvedWidth = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val resolvedHeight = resolveSize(
                (paddingTop + paddingBottom + 240f * d).toInt(),
                heightMeasureSpec
        )
        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }
}