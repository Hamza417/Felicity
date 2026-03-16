package app.simple.felicity.decorations.seekbars

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import app.simple.felicity.decorations.seekbars.FelicityEqualizerSliders.Companion.MAX_DB
import app.simple.felicity.decorations.seekbars.FelicityEqualizerSliders.Companion.MIN_DB
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
 * A 10-band graphic equalizer slider view spanning 31 Hz to 16 kHz.
 *
 * Each band is rendered as a vertical slider with a fader-style pill thumb (featuring
 * three horizontal grip lines) riding a thin track line. A smooth Catmull-Rom bezier
 * curve with an optional faint glow effect connects all thumbs to visually represent
 * the EQ curve, matching the aesthetic of professional hardware equalizers. The view
 * is horizontally scrollable when the total content width exceeds the available space,
 * with center-gravity when it fits. An exponential-decay overscroll resistance and
 * spring snap-back gives the interaction a premium feel.
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
         * @param bandIndex zero-based band index (0 = 31 Hz, 9 = 16 kHz)
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
        private const val BAND_COUNT = 10
        private const val MIN_DB = -15f
        private const val MAX_DB = 15f
        private const val DEFAULT_DB = 0f

        /** Human-readable frequency labels for each band. */
        val FREQUENCY_LABELS = arrayOf(
                "31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz",
                "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz"
        )

        /** Exponential decay factor for overscroll resistance (in px). A larger value = less resistance. */
        private const val OVERSCROLL_DECAY_FACTOR = 400f

        /** Maximum visual overscroll displacement in dp. */
        private const val MAX_OVERSCROLL_DP = 128f

        private const val TAG = "FelicityEqualizerSliders"
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Raw gain values per band in dB. */
    private val gains = FloatArray(BAND_COUNT) { DEFAULT_DB }

    /** Animated display values per band (driven by ValueAnimator on programmatic set). */
    private val displayGains = FloatArray(BAND_COUNT) { DEFAULT_DB }

    private val gainAnimators = arrayOfNulls<ValueAnimator>(BAND_COUNT)

    // -------------------------------------------------------------------------
    // Scroll state
    // -------------------------------------------------------------------------

    /** Current scroll offset in pixels (content left is at -scrollOffset relative to view). */
    private var scrollOffset = 0f

    /** Maximum allowed scroll offset = contentWidth - viewWidth (0 when content fits). */
    private var maxScroll = 0f

    /** True when content is narrower than the view (center-gravity mode). */
    private var centeredMode = false

    /** Left offset applied in centered mode to horizontally center all bands. */
    private var centeringOffset = 0f

    // -------------------------------------------------------------------------
    // Overscroll spring
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

    /**
     * Fling animation that carries the scroll position forward with momentum after the
     * finger lifts. Snap-to-bounds is triggered from the end listener so any overshot
     * position is corrected by the spring.
     */
    private val scrollFling = FlingAnimation(this, scrollOffsetProperty).apply {
        friction = 1.1f
        addEndListener { _, _, _, _ -> snapScrollToBounds() }
    }

    /** Tracks raw touch velocity during scroll gestures to seed [scrollFling] on finger lift. */
    private var velocityTracker: VelocityTracker? = null

    // -------------------------------------------------------------------------
    // Touch state
    // -------------------------------------------------------------------------

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /** Index of the band currently being dragged, or -1. */
    private var activeBandIndex = -1

    /** True once the gesture is committed to horizontal scrolling. */
    private var isScrollGesture = false

    /** True once the gesture is committed to vertical band control. */
    private var isBandGesture = false

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var scrollOffsetAtDown = 0f

    /** Y coordinate (in view space) of the thumb centre at touch-down time for the active band. */
    private var thumbYAtDown = 0f

    // -------------------------------------------------------------------------
    // Thumb press animation per band
    // -------------------------------------------------------------------------

    private val pressScales = FloatArray(BAND_COUNT) { 1f }
    private val pressScaleAnimators = arrayOfNulls<ValueAnimator>(BAND_COUNT)

    // -------------------------------------------------------------------------
    // Layout geometry (computed in onSizeChanged)
    // -------------------------------------------------------------------------

    private var columnWidth = 0f
    private var trackTop = 0f
    private var trackBottom = 0f
    private var trackLength = 0f
    private var textAreaHeight = 0f
    private var contentWidth = 0f

    // -------------------------------------------------------------------------
    // Dimension constants (scaled by display density)
    // -------------------------------------------------------------------------

    private val d = resources.displayMetrics.density

    /**
     * Horizontal spacing between adjacent band centers, in dp. Reducing this value
     * packs the sliders closer together; increasing it adds more breathing room.
     * Minimum is 36dp to prevent thumbs from overlapping. Default is 60dp.
     */
    var bandSpacingDp: Float = 60f
        set(value) {
            field = value.coerceAtLeast(36f)
            if (width > 0 && height > 0) {
                recalculateLayout(width, height)
                invalidate()
            }
        }

    /** Half-width of the pill thumb (horizontal axis for a vertical slider). */
    private val thumbHalfWidthPx = 12f * d

    /** Half-height of the pill thumb (vertical axis). A taller value produces a chunkier fader cap. */
    private val thumbHalfHeightPx = 28f * d

    /** Stroke width of the vertical track line. */
    private val trackStrokePx = 2f * d

    /** Stroke width of the bezier connecting curve. */
    private val bezierStrokePx = 2.5f * d

    /** Stroke width of the outer ring on the thumb. */
    private val thumbRingStrokePx = 2f * d

    /** Corner radius for the pill thumb (fully rounded ends). */
    private val thumbCornerRadiusPx = thumbHalfHeightPx

    /** Stroke width and half-spacing of the three grip lines drawn on the fader cap. */
    private val gripLineStrokePx = 1f * d

    /** Half-length of each grip line relative to the thumb half-width. */
    private val gripLineHalfLengthFraction = 0.52f

    /** Vertical spacing between the three grip lines as a fraction of thumb half-height. */
    private val gripLineSpacingFraction = 0.18f

    /** Vertical padding above the topmost thumb position and below the bottommost. */
    private val sliderVerticalPaddingPx = thumbHalfHeightPx + 4f * d

    /** Gap between track bottom and the text area. */
    private val textGapPx = 24F * d

    /** Press ring outset for the touch halo. */
    private val pressRingOutsetPx = 5f * d

    // -------------------------------------------------------------------------
    // Shadow / glow state
    // -------------------------------------------------------------------------

    /** Whether the faint bezier glow is currently active (driven by [AppearancePreferences.isShadowEffectOn]). */
    private var shadowEffectEnabled = false

    // -------------------------------------------------------------------------
    // Colors (populated from theme)
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
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val bezierPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /**
     * Secondary bezier paint that carries a [BlurMaskFilter] for the faint glow effect.
     * Only drawn when [shadowEffectEnabled] is true. Requires a software layer on this view.
     */
    private val bezierGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val thumbInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val thumbRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    /**
     * Paint for the three horizontal grip lines drawn across the fader cap,
     * mimicking the tactile ridges found on professional hardware faders.
     */
    private val gripLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /**
     * Paint for the accent-colored progress segment on each vertical track,
     * drawn between the 0 dB reference position and the current thumb position.
     */
    private val trackProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /**
     * Blurred, wider version of [trackProgressPaint] used to produce a faint glow on
     * the progress segment of the track when the shadow effect is enabled.
     */
    private val trackGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val pressRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val freqTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    // Reusable path and rect to avoid per-frame allocation
    private val bezierPath = Path()
    private val thumbRect = RectF()
    private val pressRingRect = RectF()

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    init {
        isClickable = true
        isFocusable = true

        if (isInEditMode.not()) {
            applyThemeColors()
        }
        applyPaintColors()
        setupTextPaints()
        if (isInEditMode.not()) {
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

        // Glow paint: same hue as bezier but wider and blurred — alpha set in updateShadowEffect
        bezierGlowPaint.color = bezierColor

        thumbInnerPaint.color = thumbInnerColor
        thumbRingPaint.color = thumbRingColor
        thumbRingPaint.strokeWidth = thumbRingStrokePx

        // Grip lines: white with moderate alpha so they're visible against any accent fill
        gripLinePaint.color = Color.WHITE
        gripLinePaint.alpha = 130
        gripLinePaint.strokeWidth = gripLineStrokePx

        // Track progress segment: accent color, same width as track
        trackProgressPaint.color = accentColor
        trackProgressPaint.strokeWidth = trackStrokePx

        // Track glow: same color, wider — alpha and blur set in updateShadowEffect
        trackGlowPaint.color = accentColor

        pressRingPaint.color = accentColor
        pressRingPaint.strokeWidth = 1.5f * d

        centerLinePaint.color = centerLineColor
        centerLinePaint.strokeWidth = 1f * d
        centerLinePaint.alpha = 80

        freqTextPaint.color = secondaryTextColor
        freqTextPaint.textSize = 9.5f * d

        valueTextPaint.color = primaryTextColor
        valueTextPaint.textSize = 10.5f * d
    }

    private fun setupTextPaints() {
        if (isInEditMode.not()) {
            val tf = TypeFace.getMediumTypeFace(context)
            freqTextPaint.typeface = tf
            valueTextPaint.typeface = tf
        }
    }

    /**
     * Reads the shadow-effect preference and either arms the [bezierGlowPaint] with a
     * [BlurMaskFilter] (requiring a software layer on this view) or removes it and
     * reverts to the default hardware-accelerated layer.
     */
    private fun updateShadowEffect() {
        shadowEffectEnabled = AppearancePreferences.isShadowEffectOn()
        if (shadowEffectEnabled) {
            val blurRadius = bezierStrokePx * 4.5f
            bezierGlowPaint.maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
            bezierGlowPaint.strokeWidth = bezierStrokePx * 2.8f
            bezierGlowPaint.alpha = 55

            val trackBlurRadius = trackStrokePx * 3f
            trackGlowPaint.maskFilter = BlurMaskFilter(trackBlurRadius, BlurMaskFilter.Blur.NORMAL)
            trackGlowPaint.strokeWidth = trackStrokePx * 3.5f
            trackGlowPaint.alpha = 65

            setLayerType(LAYER_TYPE_SOFTWARE, null)
        } else {
            bezierGlowPaint.maskFilter = null
            trackGlowPaint.maskFilter = null
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }
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
        columnWidth = bandSpacingDp * d
        contentWidth = columnWidth * BAND_COUNT

        val twoLineTextHeight = freqTextPaint.fontSpacing + valueTextPaint.fontSpacing
        textAreaHeight = twoLineTextHeight + textGapPx * 2f

        trackTop = paddingTop + sliderVerticalPaddingPx
        trackBottom = (h - paddingBottom).toFloat() - textAreaHeight - sliderVerticalPaddingPx
        trackLength = trackBottom - trackTop

        centeredMode = contentWidth <= w
        centeringOffset = if (centeredMode) (w - contentWidth) / 2f else 0f

        maxScroll = if (centeredMode) 0f else (contentWidth - w).coerceAtLeast(0f)

        // Clamp current scroll to new bounds
        scrollOffset = scrollOffset.coerceIn(0f, maxScroll)
    }

    // -------------------------------------------------------------------------
    // Gain API
    // -------------------------------------------------------------------------

    /**
     * Set the gain for a specific band.
     *
     * @param bandIndex zero-based index (0 = 31 Hz, 9 = 16 kHz)
     * @param gain      gain in dB, clamped to [MIN_DB .. MAX_DB]
     * @param animate   whether to animate the thumb to the new position
     * @param fromUser  whether the change was initiated by the user
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
                addUpdateListener { anim ->
                    displayGains[bandIndex] = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            displayGains[bandIndex] = clamped
            invalidate()
        }
        bandChangedListener?.onBandChanged(bandIndex, clamped, fromUser)
    }

    /** @return the current gain for [bandIndex] in dB. */
    fun getBandGain(bandIndex: Int): Float {
        return if (bandIndex in 0 until BAND_COUNT) gains[bandIndex] else 0f
    }

    /** Reset all bands to 0 dB. */
    fun resetAllBands(animate: Boolean = true) {
        for (i in 0 until BAND_COUNT) setBandGain(i, DEFAULT_DB, animate, false)
    }

    /** Set all band gains at once from an array. Extra/missing values are ignored/zeroed. */
    fun setAllGains(allGains: FloatArray, animate: Boolean = false) {
        for (i in 0 until BAND_COUNT) {
            setBandGain(i, if (i < allGains.size) allGains[i] else DEFAULT_DB, animate, false)
        }
    }

    // -------------------------------------------------------------------------
    // Geometry helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a gain value to the Y coordinate of the thumb center.
     * Gain [MAX_DB] maps to [trackTop], [MIN_DB] maps to [trackBottom].
     */
    private fun gainToThumbY(gain: Float): Float {
        val fraction = (gain - MIN_DB) / (MAX_DB - MIN_DB)
        return trackBottom - fraction * trackLength
    }

    /**
     * Converts a Y coordinate (view space) to a gain value,
     * clamped to [[MIN_DB] .. [MAX_DB]].
     */
    private fun thumbYToGain(y: Float): Float {
        if (trackLength <= 0f) return DEFAULT_DB
        val fraction = (trackBottom - y) / trackLength
        return (MIN_DB + fraction * (MAX_DB - MIN_DB)).coerceIn(MIN_DB, MAX_DB)
    }

    /**
     * Returns the view-space X coordinate of the center of [bandIndex]'s column,
     * accounting for the current scroll offset and centering offset.
     */
    private fun bandCenterX(bandIndex: Int): Float {
        return centeringOffset + bandIndex * columnWidth + columnWidth / 2f - scrollOffset
    }

    /**
     * Returns the band index corresponding to a view-space X coordinate,
     * or -1 if outside all columns.
     */
    private fun bandIndexAtX(x: Float): Int {
        val contentX = x - centeringOffset + scrollOffset
        @Suppress("ConvertTwoComparisonsToRangeCheck")
        if (contentX < 0f || contentX >= contentWidth) return -1
        return (contentX / columnWidth).toInt().coerceIn(0, BAND_COUNT - 1)
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (trackLength <= 0f) return

        val visibleLeft = -columnWidth
        val visibleRight = width.toFloat() + columnWidth

        drawCenterLine(canvas)
        drawBezierCurve(canvas)
        drawTracksAndThumbs(canvas, visibleLeft, visibleRight)
        drawLabels(canvas, visibleLeft, visibleRight)
    }

    /** Draws the horizontal reference line at 0 dB. */
    private fun drawCenterLine(canvas: Canvas) {
        val zeroY = gainToThumbY(0f)
        val lineLeft = centeringOffset - scrollOffset
        val lineRight = lineLeft + contentWidth
        canvas.drawLine(lineLeft, zeroY, lineRight, zeroY, centerLinePaint)
    }

    /**
     * Draws all vertical track lines and their fader-style pill thumbs with grip lines.
     */
    private fun drawTracksAndThumbs(canvas: Canvas, visibleLeft: Float, visibleRight: Float) {
        val zeroY = gainToThumbY(0f)

        for (i in 0 until BAND_COUNT) {
            val cx = bandCenterX(i)
            if (cx < visibleLeft || cx > visibleRight) continue

            val thumbY = gainToThumbY(displayGains[i])

            // Full background track line
            canvas.drawLine(cx, trackTop, cx, trackBottom, trackPaint)

            // Accent-colored progress segment between the 0 dB line and the thumb
            val progressTop = minOf(zeroY, thumbY)
            val progressBottom = maxOf(zeroY, thumbY)
            if (progressBottom > progressTop) {
                if (shadowEffectEnabled) {
                    canvas.drawLine(cx, progressTop, cx, progressBottom, trackGlowPaint)
                }
                canvas.drawLine(cx, progressTop, cx, progressBottom, trackProgressPaint)
            }

            val scale = pressScales[i]
            val halfW = thumbHalfWidthPx * scale
            val halfH = thumbHalfHeightPx * scale

            thumbRect.set(cx - halfW, thumbY - halfH, cx + halfW, thumbY + halfH)

            // Press halo
            if (scale > 1f) {
                val haloAlpha = ((scale - 1f) / 0.12f * 80f).toInt().coerceIn(0, 80)
                pressRingRect.set(
                        thumbRect.left - pressRingOutsetPx,
                        thumbRect.top - pressRingOutsetPx,
                        thumbRect.right + pressRingOutsetPx,
                        thumbRect.bottom + pressRingOutsetPx
                )
                pressRingPaint.alpha = haloAlpha
                canvas.drawRoundRect(
                        pressRingRect,
                        thumbCornerRadiusPx + pressRingOutsetPx,
                        thumbCornerRadiusPx + pressRingOutsetPx,
                        pressRingPaint
                )
            }

            // Thumb fill
            canvas.drawRoundRect(thumbRect, thumbCornerRadiusPx, thumbCornerRadiusPx, thumbInnerPaint)

            // Thumb ring
            val ringInset = thumbRingStrokePx / 2f
            thumbRect.inset(ringInset, ringInset)
            canvas.drawRoundRect(
                    thumbRect,
                    thumbCornerRadiusPx - ringInset,
                    thumbCornerRadiusPx - ringInset,
                    thumbRingPaint
            )
            thumbRect.inset(-ringInset, -ringInset) // restore

            // Three horizontal grip lines — mimic the tactile ridges of a hardware fader cap
            val gripHalfLen = halfW * gripLineHalfLengthFraction
            val gripSpacing = halfH * gripLineSpacingFraction
            for (line in -1..1) {
                val lineY = thumbY + line * gripSpacing
                canvas.drawLine(cx - gripHalfLen, lineY, cx + gripHalfLen, lineY, gripLinePaint)
            }
        }
    }

    /**
     * Draws a smooth Catmull-Rom bezier curve connecting all thumb centers.
     * When [shadowEffectEnabled] is true, a faint blurred glow pass is drawn first
     * under the crisp main curve to give a professional "lit" appearance.
     */
    private fun drawBezierCurve(canvas: Canvas) {
        bezierPath.reset()

        // Collect all thumb (x, y) pairs — including those off-screen for smooth edge continuity
        val pts = Array(BAND_COUNT) { i ->
            Pair(bandCenterX(i), gainToThumbY(displayGains[i]))
        }

        // Build the Catmull-Rom spline as cubic bezier segments
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

        // Glow pass (requires LAYER_TYPE_SOFTWARE, guarded by shadowEffectEnabled)
        if (shadowEffectEnabled) {
            canvas.drawPath(bezierPath, bezierGlowPaint)
        }

        // Crisp main curve
        canvas.drawPath(bezierPath, bezierPaint)
    }

    /** Draws frequency and dB-value labels below each slider track. */
    private fun drawLabels(canvas: Canvas, visibleLeft: Float, visibleRight: Float) {
        val freqY = trackBottom + textGapPx + freqTextPaint.fontSpacing * 0.85f
        val valueY = freqY + freqTextPaint.fontSpacing

        for (i in 0 until BAND_COUNT) {
            val cx = bandCenterX(i)
            if (cx < visibleLeft || cx > visibleRight) continue

            val freqLabel = FREQUENCY_LABELS[i]
            val gain = displayGains[i]
            val valueLabel = formatGain(gain)

            canvas.drawText(freqLabel, cx, freqY, freqTextPaint)

            // Tint the value text with accent when not at zero for subtle feedback
            val isZero = abs(gain) < 0.05f
            valueTextPaint.color = if (isZero) secondaryTextColor else accentColor
            canvas.drawText(valueLabel, cx, valueY, valueTextPaint)
        }
    }

    /** Formats a gain value for display (e.g., "+6.0", "-3.5", "0"). */
    private fun formatGain(gain: Float): String {
        return when {
            abs(gain) < 0.05f -> "0"
            gain > 0f -> "+${"%.1f".format(gain)}"
            else -> "%.1f".format(gain)
        }
    }

    // -------------------------------------------------------------------------
    // Touch handling
    // -------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleUp(event)
        }
        performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleDown(event: MotionEvent) {
        touchStartX = event.x
        touchStartY = event.y
        scrollOffsetAtDown = scrollOffset
        isScrollGesture = false
        isBandGesture = false

        // Cancel any running momentum animations so touch feels immediately responsive
        if (scrollSpring.isRunning) scrollSpring.cancel()
        if (scrollFling.isRunning) scrollFling.cancel()

        // Begin velocity tracking for potential fling on release
        velocityTracker?.recycle()
        velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(event)

        val band = bandIndexAtX(event.x)
        if (band >= 0) {
            thumbYAtDown = gainToThumbY(displayGains[band])
            // Give immediate visual and haptic feedback on touch-down, before any gesture is committed
            startPressAnimation(band, true)
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
            val adx = abs(dx)
            val ady = abs(dy)
            if (adx > touchSlop || ady > touchSlop) {
                isBandGesture = activeBandIndex >= 0 && ady > adx * 0.8f
                isScrollGesture = !isBandGesture && adx > ady * 0.8f

                // If the gesture resolves to horizontal scroll, immediately release the
                // press animation that was started speculatively in handleDown
                if (isScrollGesture && activeBandIndex >= 0) {
                    startPressAnimation(activeBandIndex, false)
                }
            }
        }

        when {
            isBandGesture && activeBandIndex >= 0 -> {
                val newThumbY = thumbYAtDown + (event.y - touchStartY)
                val newGain = thumbYToGain(newThumbY)
                val clamped = newGain.coerceIn(MIN_DB, MAX_DB)
                val previousGain = gains[activeBandIndex]

                if (clamped != previousGain) {
                    // Click haptic on each integer dB step crossing
                    if (previousGain.toInt() != clamped.toInt()) {
                        context.vibrateEffect(VibrationEffect.EFFECT_CLICK, TAG)
                    }

                    // Heavy click when hitting the hard min/max limit
                    val hitLimit = (clamped == MIN_DB && previousGain > MIN_DB) ||
                            (clamped == MAX_DB && previousGain < MAX_DB)
                    if (hitLimit) {
                        context.vibrateEffect(VibrationEffect.EFFECT_HEAVY_CLICK, TAG)
                    }

                    gains[activeBandIndex] = clamped
                    gainAnimators[activeBandIndex]?.cancel()
                    displayGains[activeBandIndex] = clamped
                    bandChangedListener?.onBandChanged(activeBandIndex, clamped, true)
                    invalidate()
                }
            }
            isScrollGesture -> {
                if (centeredMode) return

                val rawScrollTarget = scrollOffsetAtDown - dx
                scrollOffset = applyOverscrollResistance(rawScrollTarget)
                invalidate()
            }
        }
    }

    private fun handleUp(@Suppress("UNUSED_PARAMETER") event: MotionEvent) {
        // Always release the press state for any band that was touched, regardless of whether
        // the gesture was ultimately classified as a band-drag or a horizontal scroll
        if (activeBandIndex >= 0) {
            startPressAnimation(activeBandIndex, false)
        }

        if (isScrollGesture && !centeredMode) {
            // Compute current scroll velocity and seed a fling animation
            velocityTracker?.computeCurrentVelocity(1000)
            val xVelocity = velocityTracker?.xVelocity ?: 0f
            // Negate: finger moving left → xVelocity < 0 → scroll offset increases → positive fling
            val flingVelocity = -xVelocity

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

        velocityTracker?.recycle()
        velocityTracker = null

        activeBandIndex = -1
        isScrollGesture = false
        isBandGesture = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    // -------------------------------------------------------------------------
    // Overscroll resistance (exponential decay)
    // -------------------------------------------------------------------------

    /**
     * Applies an exponential-decay resistance function when dragging beyond the
     * scroll bounds. The raw target offset is passed through:
     *
     *   displayOverscroll = maxOverscroll * (1 - exp(-rawOverscroll / decayFactor))
     *
     * This causes the content to feel like it has a soft, compliant edge that gets
     * progressively stiffer the further you drag.
     */
    private fun applyOverscrollResistance(rawTarget: Float): Float {
        return when {
            rawTarget < 0f -> {
                val rawOver = -rawTarget
                val visualOver = maxOverscrollPx * (1f - exp(-rawOver / OVERSCROLL_DECAY_FACTOR))
                -visualOver
            }
            rawTarget > maxScroll -> {
                val rawOver = rawTarget - maxScroll
                val visualOver = maxOverscrollPx * (1f - exp(-rawOver / OVERSCROLL_DECAY_FACTOR))
                maxScroll + visualOver
            }
            else -> rawTarget
        }
    }

    /**
     * Animates the scroll position back to the nearest valid boundary using
     * a spring animation for a natural, bouncy feel.
     */
    private fun snapScrollToBounds() {
        val target = scrollOffset.coerceIn(0f, maxScroll)
        if (scrollOffset == target) return

        if (scrollSpring.isRunning) scrollSpring.cancel()
        scrollSpring.setStartValue(scrollOffset)
        scrollSpring.animateToFinalPosition(target)
    }

    // -------------------------------------------------------------------------
    // Thumb press scale animations
    // -------------------------------------------------------------------------

    private fun startPressAnimation(bandIndex: Int, pressed: Boolean) {
        val targetScale = if (pressed) 1.10f else 1f
        pressScaleAnimators[bandIndex]?.cancel()
        val start = pressScales[bandIndex]
        pressScaleAnimators[bandIndex] = ValueAnimator.ofFloat(start, targetScale).apply {
            duration = if (pressed) 140L else 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                pressScales[bandIndex] = anim.animatedValue as Float
                invalidate()
            }
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
                setupTextPaints()
                invalidate()
            }
            AppearancePreferences.SHADOW_EFFECT -> {
                updateShadowEffect()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isInEditMode.not()) {
            registerSharedPreferenceChangeListener()
            ThemeManager.addListener(this)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (isInEditMode.not()) {
            unregisterSharedPreferenceChangeListener()
            ThemeManager.removeListener(this)
        }
        scrollSpring.cancel()
        scrollFling.cancel()
        velocityTracker?.recycle()
        velocityTracker = null
        gainAnimators.forEach { it?.cancel() }
        pressScaleAnimators.forEach { it?.cancel() }
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