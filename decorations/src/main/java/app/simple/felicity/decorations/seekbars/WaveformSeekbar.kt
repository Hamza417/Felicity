package app.simple.felicity.decorations.seekbars

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.seekbars.WaveformSeekbar.Companion.HIGHLIGHT_MIN_ALPHA
import app.simple.felicity.decorations.seekbars.WaveformSeekbar.Companion.LABEL_GRAVITY_BOTTOM
import app.simple.felicity.decorations.seekbars.WaveformSeekbar.Companion.LABEL_GRAVITY_CENTER
import app.simple.felicity.decorations.seekbars.WaveformSeekbar.Companion.LABEL_GRAVITY_TOP
import app.simple.felicity.decorations.seekbars.WaveformSeekbar.Companion.LABEL_HIGHLIGHT_BOTH
import app.simple.felicity.decorations.seekbars.WaveformSeekbar.Companion.LABEL_HIGHLIGHT_FLAT
import app.simple.felicity.decorations.seekbars.WaveformSeekbar.Companion.LABEL_HIGHLIGHT_NONE
import app.simple.felicity.decorations.seekbars.WaveformSeekbar.Companion.LABEL_HIGHLIGHT_OUTLINE
import app.simple.felicity.decorations.seekbars.WaveformSeekbar.Companion.LEFT_FADE_DURATION_MS
import app.simple.felicity.decorations.seekbars.WaveformSeekbar.Companion.WAVEFORM_MODE_FULL
import app.simple.felicity.decorations.seekbars.WaveformSeekbar.Companion.WAVEFORM_MODE_HALF
import app.simple.felicity.decorations.seekbars.WaveformSeekbar.Companion.WAVEFORM_MODE_REFLECTION
import app.simple.felicity.decorations.typeface.TypeFace
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * A horizontally scrolling waveform seekbar that maps audio amplitude data
 * into one bar (spike) per second of audio. The current playhead is always
 * pinned to the horizontal center of the view while the waveform itself scrolls.
 *
 * Supports three display modes controlled by [waveformMode]:
 *  - **Half** ([WAVEFORM_MODE_HALF], default): bars grow from the center axis upward only.
 *  - **Full** ([WAVEFORM_MODE_FULL]): bars grow symmetrically above *and* below the center axis.
 *  - **Reflection** ([WAVEFORM_MODE_REFLECTION]): half spectrum at the top with an alpha-blended,
 *    inverted mirror image below, separated by a thin line and a small gap.
 *
 * Bars nearest the center playhead are rendered at full opacity and fade toward
 * [HIGHLIGHT_MIN_ALPHA] at the outer edges, creating a spotlight highlight effect.
 *
 * Horizontal fading edges are applied on both sides using a
 * [PorterDuff.Mode.DST_OUT] gradient layer, following the same technique
 * as [app.simple.felicity.decorations.lrc.view.ModernLrcView].
 *
 * After a drag gesture the view continues to scroll with momentum via [OverScroller],
 * mimicking a natural fling.
 *
 * @author Hamza417
 */
@Suppress("unused")
class WaveformSeekbar @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr),
    SharedPreferences.OnSharedPreferenceChangeListener,
    ThemeChangedListener {

    /**
     * Callback interface notified when the user interacts with the seekbar.
     */
    interface OnSeekListener {
        /**
         * Called each time the seek position changes during a drag gesture.
         *
         * @param positionMs the new playback position in milliseconds
         * @param fromUser   `true` when the change originates from a direct touch gesture
         *                   (drag); `false` for programmatic updates
         */
        fun onSeekTo(positionMs: Long, fromUser: Boolean) {}

        /** Called when the user first touches the seekbar to begin dragging. */
        fun onSeekStart() {}

        /** Called when the user lifts their finger; [positionMs] is the final drag position. */
        fun onSeekEnd(positionMs: Long) {}
    }

    /**
     * Notified on each animation frame while a fling gesture is actively scrolling the waveform.
     * While any registered [OnFlingRunningListener] is active, [setProgress] will not accept
     * external updates to prevent the song position from snapping back mid-fling.
     */
    fun interface OnFlingRunningListener {
        /**
         * Called continuously, once per animation frame, while the fling is in progress.
         *
         * @param positionMs current estimated playback position in milliseconds
         */
        fun onFlingRunning(positionMs: Long)
    }

    /**
     * Notified once when a fling gesture has fully decelerated to a stop.
     * After this callback, [setProgress] resumes accepting external updates.
     */
    fun interface OnFlingEndListener {
        /**
         * Called when the fling animation has fully settled at its resting position.
         *
         * @param positionMs the final settled position in milliseconds
         */
        fun onFlingEnd(positionMs: Long)
    }

    /** Provides the formatted string for the left-side elapsed time label. */
    fun interface LeftLabelProvider {
        fun getLabel(progress: Long, min: Long, max: Long): String?
    }

    /** Provides the formatted string for the right-side remaining or total time label. */
    fun interface RightLabelProvider {
        fun getLabel(progress: Long, min: Long, max: Long): String?
    }

    private var amplitudes: FloatArray = FloatArray(0)
    private var progressMs: Long = 0L
    private var durationMs: Long = 0L

    /**
     * Controls the visual rendering mode of the waveform.
     * Use [WAVEFORM_MODE_HALF], [WAVEFORM_MODE_FULL], or [WAVEFORM_MODE_REFLECTION].
     * Can also be set via the [R.styleable.WaveformSeekbar_wsbWaveformMode] XML attribute.
     */
    var waveformMode: Int = WAVEFORM_MODE_HALF
        set(value) {
            field = value
            invalidate()
        }


    /**
     * Vertical placement of the elapsed/total time labels.
     * Use [LABEL_GRAVITY_TOP], [LABEL_GRAVITY_CENTER], or [LABEL_GRAVITY_BOTTOM].
     */
    var labelGravity: Int = LABEL_GRAVITY_BOTTOM
        set(value) {
            field = value
            invalidate()
        }

    private var seekListener: OnSeekListener? = null
    private var flingRunningListener: OnFlingRunningListener? = null
    private var flingEndListener: OnFlingEndListener? = null
    private var leftLabelProvider: LeftLabelProvider? = null
    private var rightLabelProvider: RightLabelProvider? = null

    private var playedColor: Int = Color.WHITE
    private var unplayedColor: Int = Color.GRAY
    private var labelTextColor: Int = Color.GRAY

    /**
     * Vertical placement of the time label pill backgrounds.
     * Use [LABEL_HIGHLIGHT_NONE], [LABEL_HIGHLIGHT_FLAT], [LABEL_HIGHLIGHT_OUTLINE],
     * or [LABEL_HIGHLIGHT_BOTH].
     */
    var labelHighlightMode: Int = LABEL_HIGHLIGHT_NONE
        set(value) {
            field = value
            invalidate()
        }

    // Resolved fill and stroke colors for the label pills; updated in applyThemeColors()
    private var labelHighlightFillColor: Int = Color.TRANSPARENT
    private var labelHighlightStrokeColor: Int = Color.WHITE

    private var barWidthPx: Float
    private var barSpacingPx: Float
    private var barCornerRadiusPx: Float = 0f
    private var fadeEdgeLengthPx: Float
    private var labelTextSizePx: Float
    private var labelPaddingPx: Float
    private var minBarHeightPx: Float

    /**
     * Horizontal distance in pixels between each time label and its nearest horizontal view edge.
     * Increase this value to move the labels inward away from the edges.
     * Can also be set via the [R.styleable.WaveformSeekbar_wsbLabelEdgeMargin] XML attribute.
     */
    var labelEdgeMarginPx: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        typeface = if (isInEditMode) Typeface.DEFAULT else TypeFace.getBoldTypeFace(context)
    }

    private val barRect = RectF()
    private val pillRect = RectF()

    // Pill background paint for label highlights (fill mode)
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Pill stroke paint for label highlights (outline / both modes)
    private val labelBgStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    // Pre-allocated gradient shader objects; rebuilt only when the view width changes
    private var leftGradient: LinearGradient? = null
    private var rightGradient: LinearGradient? = null
    private var lastWidth = 0f

    // Reflection mode — the line paint draws the thin horizontal separator.
    private var reflectionLineHeightPx: Float = 0f

    /**
     * Vertical gap in pixels between the main waveform's bottom edge and the center separator line.
     * Can be set via the [R.styleable.WaveformSeekbar_wsbReflectionTopGap] XML attribute.
     */
    var reflectionTopGapPx: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Vertical gap in pixels between the center separator line and the reflected waveform's top edge.
     * Can be set via the [R.styleable.WaveformSeekbar_wsbReflectionBottomGap] XML attribute.
     */
    var reflectionBottomGapPx: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Alpha [0.0, 1.0] applied uniformly to all reflected bars in [WAVEFORM_MODE_REFLECTION].
     * Lower values produce a subtler, more transparent reflection. Bars already carry a spotlight
     * alpha based on distance from the playhead; this value multiplies on top of that, so the
     * effective per-bar alpha is `spotlightAlpha × reflectionAlpha`.
     * Can be set via the [R.styleable.WaveformSeekbar_wsbReflectionAlpha] XML attribute.
     */
    var reflectionAlpha: Float = DEFAULT_REFLECTION_ALPHA
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }


    private val reflectionLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Touch drag state
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartProgressMs = 0L
    private var dragCurrentProgressMs = 0L

    // Smooth scroll animation for programmatic progress updates
    private var progressAnimator: ValueAnimator? = null
    private var animatedProgressMs: Long = 0L

    // Bar height animation — interpolates from previous heights to the new amplitude data.
    // [drawnAmplitudes] is what onDraw actually renders; [animStartAmplitudes] holds the
    // values captured at the moment setAmplitudes() was called so the animator can lerp from them.
    private var drawnAmplitudes: FloatArray = FloatArray(0)
    private var animStartAmplitudes: FloatArray = FloatArray(0)
    private var barAnimator: ValueAnimator? = null

    // Color crossfade animation — transitions played/unplayed/label colors on theme or accent change
    private var colorTransitionAnimator: ValueAnimator? = null

    // Label visibility animation — scales time labels when a drag or fling begins/ends
    private var labelAlpha: Float = 1f
    private var labelScale: Float = 1f
    private var labelAnimator: ValueAnimator? = null

    // Seek line animation — fades the center playhead indicator line in on drag start and out on drag end
    private var seekLineAlpha: Float = 0f
    private var seekLineAnimator: ValueAnimator? = null
    private var seekLineVerticalPaddingPx: Float = 0f
    private val seekLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // Left-bar fade — fades out already-played bars when setDurationWithReset is called.
    // Only bars with index < leftFadePivotIndex are affected; right-side bars stay in place.
    // leftFadeProgress is -1 when inactive, [0,1] while the animation is running.
    private var leftFadeAnimator: ValueAnimator? = null
    private var leftFadeProgress: Float = -1f
    private var leftFadePivotIndex: Int = 0

    /**
     * Amplitude data deferred while a left-bar-fade transition is in progress.
     * [setAmplitudes] stores the already-normalized array here instead of starting a bar
     * animator mid-fade (which would race with the fade restructuring the arrays).
     * [leftFadeAnimator]'s [AnimatorListenerAdapter.onAnimationEnd] consumes and nulls this.
     */
    private var pendingAmplitudesData: FloatArray? = null

    // Fling infrastructure — momentum scroll after a drag gesture
    private val overScroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private var isFling = false
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()

    /**
     * Runnable posted via [postOnAnimation] to advance the fling animation one frame at a time.
     * Reads the current position from [overScroller] and converts it back to milliseconds.
     * Fires [OnFlingRunningListener] each frame and [OnFlingEndListener] when the scroller settles.
     * While [isFling] is `true`, [setProgress] is completely blocked so external position updates
     * cannot snap the waveform back to the pre-fling playback position.
     */
    private val flingRunnable = object : Runnable {
        override fun run() {
            if (!isFling) return
            if (overScroller.computeScrollOffset()) {
                val barStep = barWidthPx + barSpacingPx
                val totalPx = amplitudes.size * barStep
                if (totalPx > 0f && durationMs > 0L) {
                    val newProgress = (overScroller.currX.toFloat() / totalPx * durationMs)
                        .toLong().coerceIn(0L, durationMs)
                    animatedProgressMs = newProgress
                    progressMs = newProgress
                    flingRunningListener?.onFlingRunning(newProgress)
                }
                invalidate()
                postOnAnimation(this)
            } else {
                isFling = false
                flingEndListener?.onFlingEnd(progressMs)
            }
        }
    }

    init {
        val d = resources.displayMetrics.density
        barWidthPx = DEFAULT_BAR_WIDTH_DP * d
        barSpacingPx = DEFAULT_BAR_SPACING_DP * d
        fadeEdgeLengthPx = DEFAULT_FADE_EDGE_LENGTH_DP * d
        labelTextSizePx = DEFAULT_LABEL_TEXT_SIZE_DP * d
        labelPaddingPx = DEFAULT_LABEL_PADDING_DP * d
        labelEdgeMarginPx = DEFAULT_LABEL_EDGE_MARGIN_DP * d
        minBarHeightPx = DEFAULT_MIN_BAR_HEIGHT_DP * d
        labelBgStrokePaint.strokeWidth = DEFAULT_LABEL_STROKE_WIDTH_DP * d
        seekLinePaint.strokeWidth = SEEK_LINE_WIDTH_DP * d
        seekLineVerticalPaddingPx = SEEK_LINE_VERTICAL_PADDING_DP * d
        reflectionTopGapPx = REFLECTION_TOP_GAP_DP * d
        reflectionBottomGapPx = REFLECTION_BOTTOM_GAP_DP * d
        reflectionLineHeightPx = REFLECTION_LINE_HEIGHT_DP * d

        // Read XML attributes when they are provided
        if (attrs != null) {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.WaveformSeekbar, defStyleAttr, 0)
            try {
                waveformMode = ta.getInt(R.styleable.WaveformSeekbar_wsbWaveformMode, WAVEFORM_MODE_HALF)
                barWidthPx = ta.getDimension(R.styleable.WaveformSeekbar_wsbBarWidth, barWidthPx)
                barSpacingPx = ta.getDimension(R.styleable.WaveformSeekbar_wsbBarSpacing, barSpacingPx)
                fadeEdgeLengthPx = ta.getDimension(R.styleable.WaveformSeekbar_wsbFadeEdgeLength, fadeEdgeLengthPx)
                labelEdgeMarginPx = ta.getDimension(R.styleable.WaveformSeekbar_wsbLabelEdgeMargin, labelEdgeMarginPx)
                labelGravity = ta.getInt(R.styleable.WaveformSeekbar_wsbLabelGravity, LABEL_GRAVITY_BOTTOM)
                labelHighlightMode = ta.getInt(R.styleable.WaveformSeekbar_wsbLabelHighlightMode, LABEL_HIGHLIGHT_NONE)
                reflectionTopGapPx = ta.getDimension(R.styleable.WaveformSeekbar_wsbReflectionTopGap, reflectionTopGapPx)
                reflectionBottomGapPx = ta.getDimension(R.styleable.WaveformSeekbar_wsbReflectionBottomGap, reflectionBottomGapPx)
                reflectionAlpha = ta.getFloat(R.styleable.WaveformSeekbar_wsbReflectionAlpha, DEFAULT_REFLECTION_ALPHA)
            } finally {
                ta.recycle()
            }
        }

        if (!isInEditMode) {
            barCornerRadiusPx = computeCornerRadius()
            applyThemeColors()
        } else {
            // Safe fallback for the layout editor
            barCornerRadiusPx = barWidthPx / 2f
        }

        // Hardware layer is required for DST_OUT compositing to work correctly on API < 28
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isClickable = true
    }

    /**
     * Derives the bar corner radius from [AppearancePreferences.getCornerRadius].
     * The preference value (1..80) is normalized and mapped to [0, barWidth/2] so
     * that the maximum preference gives fully pill-shaped bars.
     */
    private fun computeCornerRadius(): Float {
        if (isInEditMode) return barWidthPx / 2f
        return (AppearancePreferences.getCornerRadius() / AppearancePreferences.MAX_CORNER_RADIUS) *
                (barWidthPx / 2f)
    }

    private fun applyThemeColors() {
        val newPlayed = ThemeManager.accent.primaryAccentColor
        val newUnplayed = ThemeManager.accent.secondaryAccentColor
        val newLabel = ThemeManager.theme.textViewTheme.secondaryTextColor

        // Label pill colors match HighlightTextView: fill = theme highlight, stroke = accent
        labelHighlightFillColor = ThemeManager.theme.viewGroupTheme.highlightColor
        labelHighlightStrokeColor = newPlayed
        labelBgPaint.color = labelHighlightFillColor
        labelBgStrokePaint.color = labelHighlightStrokeColor

        // On first init (not yet attached) or when colors are unchanged, skip the animation
        if (!isAttachedToWindow
                || (playedColor == newPlayed && unplayedColor == newUnplayed && labelTextColor == newLabel)) {
            playedColor = newPlayed
            unplayedColor = newUnplayed
            labelTextColor = newLabel
            labelPaint.color = newLabel
            labelPaint.textSize = labelTextSizePx
            invalidate()
            return
        }

        val fromPlayed = playedColor
        val fromUnplayed = unplayedColor
        val fromLabel = labelTextColor

        colorTransitionAnimator?.cancel()
        colorTransitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = COLOR_TRANSITION_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                playedColor = blendArgb(fromPlayed, newPlayed, t)
                unplayedColor = blendArgb(fromUnplayed, newUnplayed, t)
                labelTextColor = blendArgb(fromLabel, newLabel, t)
                labelPaint.color = labelTextColor
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        val minH = (MIN_VIEW_HEIGHT_DP * d).toInt() + paddingTop + paddingBottom
        val resolvedH = resolveSize(minH, heightMeasureSpec)
        val resolvedW = resolveSize(suggestedMinimumWidth + paddingLeft + paddingRight, widthMeasureSpec)
        setMeasuredDimension(resolvedW, resolvedH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (amplitudes.isEmpty() || drawnAmplitudes.isEmpty() || durationMs <= 0L) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val centerX = w / 2f
        val centerY = h / 2f
        val barStep = barWidthPx + barSpacingPx

        val displayProgress = if (isDragging) dragCurrentProgressMs else animatedProgressMs
        val progressFraction = displayProgress.toFloat().coerceIn(0f, durationMs.toFloat()) / durationMs.toFloat()

        // scrollOffset is in pixels: bar center at index i = i*barStep − scrollOffset + centerX
        val totalScrollRange = amplitudes.size * barStep
        val scrollOffset = progressFraction * totalScrollRange

        // Preserve a compositing layer so the DST_OUT fades erase bars near the edges
        val layerId = canvas.saveLayer(0f, 0f, w, h, null)

        // Reserve vertical space for labels so bars do not overlap them
        val labelAreaH = labelTextSizePx + labelPaddingPx * 1.5f
        val maxBarArea = h - labelAreaH - paddingTop - paddingBottom

        // Geometry pre-computed for reflection mode; values are unused in half/full modes.
        // Both halves use the smaller of the two available areas so bars are visually equal
        // regardless of label space or asymmetric padding.
        val mainWaveformBottom = if (waveformMode == WAVEFORM_MODE_REFLECTION) {
            centerY - reflectionTopGapPx
        } else {
            centerY
        }
        val reflectionTop = centerY + reflectionBottomGapPx
        val maxMainBarArea: Float
        val maxReflBarArea: Float
        if (waveformMode == WAVEFORM_MODE_REFLECTION) {
            val topHalfArea = (mainWaveformBottom - paddingTop - labelAreaH).coerceAtLeast(0f)
            val bottomHalfArea = (h - paddingBottom - reflectionTop).coerceAtLeast(0f)
            val equalHalfArea = minOf(topHalfArea, bottomHalfArea)
            maxMainBarArea = equalHalfArea
            maxReflBarArea = equalHalfArea
        } else {
            maxMainBarArea = maxBarArea
            maxReflBarArea = 0f
        }

        for (i in drawnAmplitudes.indices) {
            val barCenterX = i * barStep - scrollOffset + centerX
            // Cull bars that are fully outside the visible area
            if (barCenterX + barWidthPx / 2f < 0f || barCenterX - barWidthPx / 2f > w) continue

            val amp = drawnAmplitudes[i].coerceIn(0f, 1f)

            // Smoothly crossfade between played and unplayed color over a ±transitionZone window
            // centered on the playhead, so the color change is gradual rather than an instant switch.
            val transitionZone = barStep * TRANSITION_ZONE
            val distFromCenter = barCenterX - centerX
            val colorT = ((distFromCenter + transitionZone) / (2f * transitionZone)).coerceIn(0f, 1f)
            barPaint.color = blendArgb(playedColor, unplayedColor, colorT)

            // Bars closest to the center playhead render at full opacity; bars toward the outer
            // edges fade toward HIGHLIGHT_MIN_ALPHA, creating a spotlight effect.
            // During a left-fade transition, bars before the pivot additionally fade out to zero.
            val distFraction = (abs(barCenterX - centerX) / (w / 2f)).coerceIn(0f, 1f)
            val spotlightAlpha = HIGHLIGHT_MIN_ALPHA + (1f - HIGHLIGHT_MIN_ALPHA) * (1f - distFraction)
            val leftFadeAlpha = if (leftFadeProgress >= 0f && i < leftFadePivotIndex) {
                1f - leftFadeProgress
            } else {
                1f
            }
            barPaint.alpha = (255 * spotlightAlpha * leftFadeAlpha).toInt()

            when (waveformMode) {
                WAVEFORM_MODE_FULL -> {
                    // Symmetric: bar grows from center upward AND downward
                    val halfH = max(minBarHeightPx / 2f, amp * (maxBarArea / 2f))
                    barRect.set(
                            barCenterX - barWidthPx / 2f,
                            centerY - halfH,
                            barCenterX + barWidthPx / 2f,
                            centerY + halfH
                    )
                }
                WAVEFORM_MODE_REFLECTION -> {
                    // Reflection main: bar grows upward from just above the separator gap
                    val barH = max(minBarHeightPx, amp * maxMainBarArea)
                    barRect.set(
                            barCenterX - barWidthPx / 2f,
                            mainWaveformBottom - barH,
                            barCenterX + barWidthPx / 2f,
                            mainWaveformBottom
                    )
                }
                else -> {
                    // Half: bar grows upward from center only
                    val barH = max(minBarHeightPx, amp * maxBarArea)
                    barRect.set(
                            barCenterX - barWidthPx / 2f,
                            centerY - barH,
                            barCenterX + barWidthPx / 2f,
                            centerY
                    )
                }
            }

            canvas.drawRoundRect(barRect, barCornerRadiusPx, barCornerRadiusPx, barPaint)
        }

        // Draw the reflected (inverted, alpha-blended) waveform and its separator line in reflection mode.
        // Reflected bars are drawn directly without a sub-layer; [reflectionAlpha] is multiplied into
        // each bar's paint alpha so no extra compositing pass or gradient texture is needed.
        if (waveformMode == WAVEFORM_MODE_REFLECTION && maxReflBarArea > 0f) {

            for (i in drawnAmplitudes.indices) {
                val barCenterX = i * barStep - scrollOffset + centerX
                if (barCenterX + barWidthPx / 2f < 0f || barCenterX - barWidthPx / 2f > w) continue

                val amp = drawnAmplitudes[i].coerceIn(0f, 1f)
                val transitionZone = barStep * TRANSITION_ZONE
                val distFromCenter = barCenterX - centerX
                val colorT = ((distFromCenter + transitionZone) / (2f * transitionZone)).coerceIn(0f, 1f)
                barPaint.color = blendArgb(playedColor, unplayedColor, colorT)

                val distFraction = (abs(barCenterX - centerX) / (w / 2f)).coerceIn(0f, 1f)
                val spotlightAlpha = HIGHLIGHT_MIN_ALPHA + (1f - HIGHLIGHT_MIN_ALPHA) * (1f - distFraction)
                val leftFadeAlpha = if (leftFadeProgress >= 0f && i < leftFadePivotIndex) {
                    1f - leftFadeProgress
                } else {
                    1f
                }
                // Multiply reflectionAlpha on top of the existing spotlight and left-fade factors
                barPaint.alpha = (255 * spotlightAlpha * leftFadeAlpha * reflectionAlpha).toInt()

                // Reflected bar grows downward from the top of the reflection area
                val reflBarH = max(minBarHeightPx, amp * maxReflBarArea)
                barRect.set(
                        barCenterX - barWidthPx / 2f,
                        reflectionTop,
                        barCenterX + barWidthPx / 2f,
                        reflectionTop + reflBarH
                )
                canvas.drawRoundRect(barRect, barCornerRadiusPx, barCornerRadiusPx, barPaint)
            }


            // Draw the thin horizontal separator line inside the outer compositing layer so the
            // horizontal edge fades clip it naturally at both sides.
            reflectionLinePaint.color = playedColor
            reflectionLinePaint.alpha = (255 * REFLECTION_LINE_ALPHA).toInt()
            canvas.drawRect(
                    0f,
                    centerY - reflectionLineHeightPx / 2f,
                    w,
                    centerY + reflectionLineHeightPx / 2f,
                    reflectionLinePaint
            )
        }

        // Rebuild gradient shaders only when the view width changes
        if (w != lastWidth) rebuildGradients(w)

        // Left horizontal fade (opaque → transparent, erasing bars at the left edge)
        leftGradient?.let { fadePaint.shader = it }
        canvas.drawRect(0f, 0f, fadeEdgeLengthPx, h, fadePaint)

        // Right horizontal fade (transparent → opaque, erasing bars at the right edge)
        rightGradient?.let { fadePaint.shader = it }
        canvas.drawRect(w - fadeEdgeLengthPx, 0f, w, h, fadePaint)

        canvas.restoreToCount(layerId)

        // Draw the seek line at the center playhead position while the user is dragging;
        // rendered outside the compositing layer so fade edges never clip it.
        // Alpha is capped at SEEK_LINE_MAX_ALPHA to keep it visually translucent.
        if (seekLineAlpha > 0f) {
            seekLinePaint.color = playedColor
            seekLinePaint.alpha = (255 * seekLineAlpha * SEEK_LINE_MAX_ALPHA).toInt()
            canvas.drawLine(
                    centerX,
                    seekLineVerticalPaddingPx,
                    centerX,
                    h - seekLineVerticalPaddingPx,
                    seekLinePaint
            )
        }

        // Draw time labels outside the compositing layer so they are never erased
        drawLabels(canvas, displayProgress, w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildGradients(w.toFloat())
    }

    /** Rebuilds the cached [LinearGradient] shaders whenever the view width changes. */
    private fun rebuildGradients(w: Float) {
        if (w <= 0f) return
        lastWidth = w
        leftGradient = LinearGradient(
                0f, 0f, fadeEdgeLengthPx, 0f,
                intArrayOf(0xFF000000.toInt(), 0x00000000),
                null,
                Shader.TileMode.CLAMP
        )
        rightGradient = LinearGradient(
                w - fadeEdgeLengthPx, 0f, w, 0f,
                intArrayOf(0x00000000, 0xFF000000.toInt()),
                null,
                Shader.TileMode.CLAMP
        )
    }

    /**
     * Draws the elapsed and total/remaining time labels at the position determined by [labelGravity].
     * When [labelHighlightMode] is not [LABEL_HIGHLIGHT_NONE] a pill-shaped background matching
     * the [HighlightTextView][app.simple.felicity.decorations.highlight.HighlightTextView] color
     * model is drawn behind each label first.
     */
    private fun drawLabels(canvas: Canvas, currentProgressMs: Long, w: Float, h: Float) {
        val leftLabel = leftLabelProvider?.getLabel(currentProgressMs, 0L, durationMs)
        val rightLabel = rightLabelProvider?.getLabel(currentProgressMs, 0L, durationMs)
        if (leftLabel.isNullOrEmpty() && rightLabel.isNullOrEmpty()) return
        if (labelAlpha <= 0f) return

        // Scale text size and apply seek-driven alpha to both paint and pill backgrounds
        val effectiveTextSize = labelTextSizePx * labelScale
        labelPaint.textSize = effectiveTextSize
        labelPaint.color = labelTextColor
        labelPaint.alpha = (255 * labelAlpha).toInt()

        val textY = when (labelGravity) {
            LABEL_GRAVITY_TOP -> paddingTop.toFloat() + effectiveTextSize + labelPaddingPx / 2f
            LABEL_GRAVITY_CENTER -> h / 2f + effectiveTextSize / 3f
            else -> h - labelPaddingPx / 2f  // LABEL_GRAVITY_BOTTOM (default)
        }

        // Draw pill backgrounds behind each label when a highlight mode is active
        if (labelHighlightMode != LABEL_HIGHLIGHT_NONE) {
            // Use actual font metrics so the pill precisely hugs the visible text glyphs,
            // ensuring the text is perfectly centered inside the pill vertically.
            val fm = labelPaint.fontMetrics
            val pillHPad = labelPaddingPx * PILL_H_PAD_FACTOR
            val pillVPad = labelPaddingPx * PILL_V_PAD_FACTOR
            val textVisualTop = textY + fm.ascent
            val textVisualBottom = textY + fm.descent
            val pillR = (textVisualBottom - textVisualTop + pillVPad * 2f) / 2f
            val pillAlpha = (255 * labelAlpha).toInt()

            labelBgPaint.color = labelHighlightFillColor
            labelBgPaint.alpha = pillAlpha
            labelBgStrokePaint.color = labelHighlightStrokeColor
            labelBgStrokePaint.alpha = pillAlpha

            if (!leftLabel.isNullOrEmpty()) {
                val tw = labelPaint.measureText(leftLabel)
                pillRect.set(
                        labelEdgeMarginPx - pillHPad,
                        textVisualTop - pillVPad,
                        labelEdgeMarginPx + tw + pillHPad,
                        textVisualBottom + pillVPad
                )
                drawLabelPill(canvas, pillRect, pillR)
            }

            if (!rightLabel.isNullOrEmpty()) {
                val tw = labelPaint.measureText(rightLabel)
                pillRect.set(
                        w - labelEdgeMarginPx - tw - pillHPad,
                        textVisualTop - pillVPad,
                        w - labelEdgeMarginPx + pillHPad,
                        textVisualBottom + pillVPad
                )
                drawLabelPill(canvas, pillRect, pillR)
            }
        }

        if (!leftLabel.isNullOrEmpty()) {
            labelPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(leftLabel, labelEdgeMarginPx, textY, labelPaint)
        }

        if (!rightLabel.isNullOrEmpty()) {
            labelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(rightLabel, w - labelEdgeMarginPx, textY, labelPaint)
        }
    }

    /**
     * Draws the fill and/or stroke of a single label pill according to [labelHighlightMode].
     *
     * @param canvas target canvas
     * @param rect   bounding rectangle of the pill
     * @param r      corner radius
     */
    private fun drawLabelPill(canvas: Canvas, rect: RectF, r: Float) {
        when (labelHighlightMode) {
            LABEL_HIGHLIGHT_FLAT -> canvas.drawRoundRect(rect, r, r, labelBgPaint)
            LABEL_HIGHLIGHT_OUTLINE -> canvas.drawRoundRect(rect, r, r, labelBgStrokePaint)
            LABEL_HIGHLIGHT_BOTH -> {
                canvas.drawRoundRect(rect, r, r, labelBgPaint)
                canvas.drawRoundRect(rect, r, r, labelBgStrokePaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Stop any in-flight fling or programmatic animation
                overScroller.abortAnimation()
                isFling = false
                progressAnimator?.cancel()

                parent?.requestDisallowInterceptTouchEvent(true)
                isDragging = true
                dragStartX = event.x
                dragStartProgressMs = animatedProgressMs
                dragCurrentProgressMs = dragStartProgressMs

                // Start velocity tracking for the upcoming drag
                if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
                velocityTracker!!.clear()
                velocityTracker!!.addMovement(event)

                animateLabelVisibility(false)
                animateSeekLine(true)
                seekListener?.onSeekStart()
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    velocityTracker?.addMovement(event)

                    val deltaX = event.x - dragStartX
                    val barStep = barWidthPx + barSpacingPx
                    val deltaMs = if (amplitudes.isNotEmpty() && barStep > 0f) {
                        // Swiping left → seeking forward; swiping right → seeking backward
                        (-deltaX / barStep * (durationMs.toFloat() / amplitudes.size)).roundToLong()
                    } else {
                        0L
                    }
                    dragCurrentProgressMs = (dragStartProgressMs + deltaMs).coerceIn(0L, durationMs)
                    seekListener?.onSeekTo(dragCurrentProgressMs, fromUser = true)
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(FLING_VELOCITY_UNITS)
                    val xVelocity = velocityTracker?.xVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null

                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    animateLabelVisibility(true)
                    animateSeekLine(false)

                    progressMs = dragCurrentProgressMs
                    animatedProgressMs = dragCurrentProgressMs

                    // Decide whether a fling will launch BEFORE firing any callbacks.
                    // Setting isFling = true here ensures setProgress is blocked during the window
                    // between seekListener.onSeekEnd and the first flingRunnable frame, preventing
                    // the song's playback position from snapping the waveform back mid-fling.
                    val barStep = barWidthPx + barSpacingPx
                    val willFling = amplitudes.isNotEmpty()
                            && barStep > 0f
                            && abs(xVelocity) > minFlingVelocity
                    if (willFling) isFling = true

                    seekListener?.onSeekEnd(dragCurrentProgressMs)

                    if (willFling) {
                        val totalScrollPx = amplitudes.size * barStep
                        val scrollPx = (animatedProgressMs.toFloat() / durationMs.toFloat() * totalScrollPx).toInt()
                        // Negate xVelocity: finger moving left (negative) → fling forward (positive scroll)
                        overScroller.fling(
                                scrollPx, 0,
                                (-xVelocity).toInt(), 0,
                                0, totalScrollPx.toInt(),
                                0, 0
                        )
                        postOnAnimation(flingRunnable)
                    }

                    invalidate()
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /**
     * Animates the label scale toward the seek-active or idle state.
     * Called automatically when a drag gesture starts ([visible] = `false`) or ends ([visible] = `true`).
     * Labels remain fully opaque at all times; they are enlarged slightly to 1.1× while seeking
     * so the user can read the time easily during a drag.
     *
     * @param visible `true` to restore labels to their default 1× scale; `false` to scale up for seek mode.
     */
    private fun animateLabelVisibility(visible: Boolean) {
        labelAnimator?.cancel()
        val fromAlpha = labelAlpha
        val fromScale = labelScale
        val toAlpha = 1f
        val toScale = if (visible) 1f else LABEL_SEEK_ACTIVE_SCALE
        labelAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = LABEL_ANIM_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                labelAlpha = fromAlpha + (toAlpha - fromAlpha) * t
                labelScale = fromScale + (toScale - fromScale) * t
                invalidate()
            }
            start()
        }
    }

    /**
     * Animates the center playhead seek line alpha toward fully visible or fully hidden.
     *
     * @param show `true` to fade the line in when a drag starts; `false` to fade it out on release.
     */
    private fun animateSeekLine(show: Boolean) {
        seekLineAnimator?.cancel()
        val from = seekLineAlpha
        val to = if (show) 1f else 0f
        seekLineAnimator = ValueAnimator.ofFloat(from, to).apply {
            duration = SEEK_LINE_ANIM_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                seekLineAlpha = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * Supplies the normalized amplitude data for the currently loaded track.
     * Each entry represents a single second of audio; values must be in [0.0, 1.0].
     *
     * Before animating, the raw values are rescaled via min-max normalization so
     * that the quietest bar maps to 0.0 and the loudest maps to 1.0. This ensures
     * the waveform always uses the full available vertical space regardless of
     * whether the track is uniformly loud or uniformly quiet.
     *
     * Bar heights always animate from wherever they currently are (zero on first load,
     * previous values on subsequent loads) to the new target, so the transition is
     * never abrupt.
     *
     * @param data raw amplitude array, one value per second (any non-negative range)
     */
    fun setAmplitudes(data: FloatArray) {
        val normalizedData = normalizeAmplitudes(data)

        // If a left-bar-fade transition is currently running, deferring is mandatory.
        // Starting a barAnimator now would race with the fade's onAnimationEnd, which
        // restructures (shrinks) drawnAmplitudes, causing an ArrayIndexOutOfBoundsException
        // when the barAnimator tries to write past the new array boundary.
        if (leftFadeProgress >= 0f) {
            pendingAmplitudesData = normalizedData
            return
        }

        applyNormalizedAmplitudes(normalizedData)
    }

    /**
     * Wires up the bar-height animator from [drawnAmplitudes] to [normalizedData].
     * Must only be called when no left-bar-fade is in progress, as it mutates
     * [drawnAmplitudes] and [animStartAmplitudes] directly.
     *
     * @param normalizedData amplitude array already rescaled to [0.0, 1.0]
     */
    private fun applyNormalizedAmplitudes(normalizedData: FloatArray) {
        // Cancel BEFORE touching any arrays. The old onAnimationEnd closure captures the
        // previous data reference; if we resize drawnAmplitudes first its size would no
        // longer match that closure, causing an ArrayIndexOutOfBoundsException.
        barAnimator?.cancel()
        barAnimator = null

        amplitudes = normalizedData

        // Align start array to new data length, padding with zeros when growing
        if (animStartAmplitudes.size != normalizedData.size) {
            animStartAmplitudes = FloatArray(normalizedData.size) { i ->
                if (i < drawnAmplitudes.size) drawnAmplitudes[i] else 0f
            }
            drawnAmplitudes = animStartAmplitudes.copyOf()
        } else {
            for (i in normalizedData.indices) animStartAmplitudes[i] = drawnAmplitudes[i]
        }

        barAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = BAR_ANIM_DURATION_MS
            interpolator = DecelerateInterpolator(BAR_ANIM_DECELERATE)
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                for (i in normalizedData.indices) {
                    drawnAmplitudes[i] = animStartAmplitudes[i] + (normalizedData[i] - animStartAmplitudes[i]) * t
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Snap to exact target values to eliminate any floating-point drift
                    for (i in normalizedData.indices) drawnAmplitudes[i] = normalizedData[i]
                }
            })
            start()
        }
    }

    /**
     * Applies min-max normalization to [data] so the minimum value maps to 0.0 and
     * the maximum value maps to 1.0, spreading the full dynamic range across the
     * available visual height regardless of the absolute loudness of the track.
     *
     * Returns a copy of [data] unchanged when:
     * - [data] is empty or has a single element (no range to compute), or
     * - all values are identical (range is zero, which would cause a division by zero).
     *
     * @param data raw amplitude array in any non-negative range
     * @return a new [FloatArray] with values in [0.0, 1.0]
     */
    private fun normalizeAmplitudes(data: FloatArray): FloatArray {
        if (data.size < 2) return data.copyOf()
        val minVal = data.min()
        val maxVal = data.max()
        val range = maxVal - minVal
        return if (range > 0f) {
            FloatArray(data.size) { i -> (data[i] - minVal) / range }
        } else {
            data.copyOf()
        }
    }

    /**
     * Updates the current playback position, optionally animating the waveform scroll.
     * This call is silently ignored while any of the following are active:
     *  - A drag gesture ([isDragging])
     *  - A fling animation ([isFling]) — use [OnFlingRunningListener] / [OnFlingEndListener] instead
     *  - A left-bar fade transition ([leftFadeProgress] ≥ 0)
     *
     * @param positionMs playback position in milliseconds
     * @param fromUser   `true` when the change originates from user interaction
     * @param animate    whether to animate the transition from the current position
     */
    fun setProgress(positionMs: Long, fromUser: Boolean = false, animate: Boolean = false) {
        // Ignore external progress updates while the left-bar fade is running; the waveform
        // position is intentionally locked during that transition.
        if (isDragging || isFling || leftFadeProgress >= 0f) return

        val target = positionMs.coerceIn(0L, durationMs)
        progressMs = target

        if (!animate || fromUser) {
            progressAnimator?.cancel()
            animatedProgressMs = target
            invalidate()
        } else {
            val start = animatedProgressMs
            if (abs(start - target) < PROGRESS_SNAP_THRESHOLD_MS) {
                progressAnimator?.cancel()
                animatedProgressMs = target
                invalidate()
                return
            }
            progressAnimator?.cancel()
            progressAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = PROGRESS_ANIM_DURATION_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    val t = anim.animatedValue as Float
                    animatedProgressMs = start + ((target - start) * t).roundToLong()
                    invalidate()
                }
                start()
            }
        }
    }

    /**
     * Sets the total duration of the audio track in milliseconds.
     *
     * @param durationMs total duration in milliseconds
     */
    fun setDuration(durationMs: Long) {
        this.durationMs = durationMs
        invalidate()
    }

    /**
     * Updates the total duration and triggers a seamless track-change transition with zero
     * horizontal scroll movement.
     *
     * How it works:
     *  1. The bars to the **left** of the current playhead (already-played bars) are faded out
     *     in alpha over [LEFT_FADE_DURATION_MS].  The waveform's scroll position is **not**
     *     touched during this time, so there is no visible movement.
     *  2. When the fade completes the played bars are discarded.  The bars to the **right** of
     *     the playhead are retained at their current heights — they are not zeroed out.  A
     *     sub-bar fractional residual is carried into a tiny `residualProgressMs` offset so the
     *     first post-transition frame lands at exactly the same pixel position as the last frame
     *     of the fade, eliminating any micro-jitter.
     *  3. When [setAmplitudes] is called next, the retained bars animate from their current
     *     heights to the new song's heights organically.
     *  4. Progress and duration are updated to the new values.
     *
     * If there are no bars to fade (empty waveform) the function fast-paths to a direct update.
     *
     * @param newDurationMs new total duration in milliseconds
     */
    fun setDurationWithReset(newDurationMs: Long) {
        progressAnimator?.cancel()
        barAnimator?.cancel()
        barAnimator = null
        leftFadeAnimator?.cancel()
        seekLineAnimator?.cancel()
        overScroller.abortAnimation()
        isFling = false
        isDragging = false
        seekLineAlpha = 0f
        pendingAmplitudesData = null

        if (drawnAmplitudes.isEmpty() || durationMs <= 0L) {
            amplitudes = FloatArray(0)
            drawnAmplitudes = FloatArray(0)
            animStartAmplitudes = FloatArray(0)
            progressMs = 0L
            animatedProgressMs = 0L
            durationMs = newDurationMs
            invalidate()
            return
        }

        // Snapshot the current pivot index so the closure is self-contained even if the
        // outer state changes before the animation ends.
        val pivotFraction = progressMs.toFloat() / durationMs.toFloat()
        val pivot = (pivotFraction * drawnAmplitudes.size).toInt().coerceIn(0, drawnAmplitudes.size)

        leftFadePivotIndex = pivot
        leftFadeProgress = 0f

        leftFadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = LEFT_FADE_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                leftFadeProgress = anim.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Cancel any barAnimator that may have been started by a setAmplitudes
                    // call that slipped in before the fade guard was active. This MUST happen
                    // before drawnAmplitudes is reassigned; otherwise the running animator
                    // will try to index the old-size array on the next frame and crash.
                    barAnimator?.cancel()
                    barAnimator = null

                    // Compute the exact fractional scroll position from the animated progress
                    // so the remaining bars land on the same pixel coordinates after the
                    // restructure. This eliminates the sub-bar micro-jitter that would occur
                    // if the pivot index (integer) does not perfectly align with the floating-
                    // point animated scroll offset.
                    val exactScrollBars = if (durationMs > 0L) {
                        animatedProgressMs.toFloat() / durationMs.toFloat() * drawnAmplitudes.size
                    } else 0f
                    val pivot = exactScrollBars.toInt().coerceIn(0, drawnAmplitudes.size)
                    val residualFrac = (exactScrollBars - pivot).coerceAtLeast(0f)

                    // Retain the bars to the right of the playhead without zeroing them.
                    // The next setAmplitudes call will animate them from their current heights
                    // to the new song's amplitudes, so pre-flattening here is unnecessary.
                    val remaining = if (pivot < drawnAmplitudes.size) {
                        drawnAmplitudes.copyOfRange(pivot, drawnAmplitudes.size)
                    } else FloatArray(1) { 0f }

                    val newSize = remaining.size

                    // Map the fractional offset into the new duration space so that the very
                    // first onDraw call after the transition produces an identical pixel
                    // position to the last frame of the fade animation — zero visual snap.
                    val residualProgressMs = if (newSize > 0 && newDurationMs > 0L) {
                        (residualFrac / newSize * newDurationMs).toLong()
                    } else 0L

                    amplitudes = remaining.copyOf()
                    drawnAmplitudes = remaining
                    animStartAmplitudes = remaining.copyOf()

                    progressMs = residualProgressMs
                    animatedProgressMs = residualProgressMs
                    durationMs = newDurationMs

                    // Mark the fade as inactive BEFORE applying pending amplitudes so that
                    // applyNormalizedAmplitudes is not blocked by the leftFadeProgress guard.
                    leftFadeProgress = -1f

                    // If setAmplitudes was called while the fade was running, apply that data
                    // now. applyNormalizedAmplitudes will start a smooth bar animation from
                    // the restructured remaining bars to the new track's target heights.
                    pendingAmplitudesData?.let { pending ->
                        pendingAmplitudesData = null
                        applyNormalizedAmplitudes(pending)
                    }

                    invalidate()
                }
            })
            start()
        }
    }

    /**
     * Resets the seekbar to its initial state, clearing amplitude data and progress.
     */
    fun reset() {
        progressAnimator?.cancel()
        barAnimator?.cancel()
        colorTransitionAnimator?.cancel()
        leftFadeAnimator?.cancel()
        seekLineAnimator?.cancel()
        overScroller.abortAnimation()
        isFling = false
        amplitudes = FloatArray(0)
        drawnAmplitudes = FloatArray(0)
        animStartAmplitudes = FloatArray(0)
        pendingAmplitudesData = null
        progressMs = 0L
        animatedProgressMs = 0L
        durationMs = 0L
        leftFadeProgress = -1f
        seekLineAlpha = 0f
        isDragging = false
        invalidate()
    }

    /** Registers the seek event listener. */
    fun setOnSeekListener(listener: OnSeekListener?) {
        seekListener = listener
    }

    /**
     * Registers a listener that receives position updates on every animation frame during a fling.
     * While the fling is running, [setProgress] ignores all external calls to prevent the playback
     * position from overwriting the fling trajectory.
     *
     * @param listener the [OnFlingRunningListener] to register, or `null` to unregister
     */
    fun setOnFlingRunningListener(listener: OnFlingRunningListener?) {
        flingRunningListener = listener
    }

    /**
     * Registers a listener notified once when a fling gesture has fully settled.
     * After this callback fires, [setProgress] resumes accepting external updates.
     *
     * @param listener the [OnFlingEndListener] to register, or `null` to unregister
     */
    fun setOnFlingEndListener(listener: OnFlingEndListener?) {
        flingEndListener = listener
    }

    /**
     * Sets the provider for the left-side (elapsed) time label.
     *
     * @param provider label string provider, or `null` to hide the label
     */
    fun setLeftLabelProvider(provider: LeftLabelProvider?) {
        leftLabelProvider = provider
        invalidate()
    }

    /**
     * Sets the provider for the right-side (total or remaining) time label.
     *
     * @param provider label string provider, or `null` to hide the label
     */
    fun setRightLabelProvider(provider: RightLabelProvider?) {
        rightLabelProvider = provider
        invalidate()
    }

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
        progressAnimator?.cancel()
        barAnimator?.cancel()
        colorTransitionAnimator?.cancel()
        labelAnimator?.cancel()
        leftFadeAnimator?.cancel()
        seekLineAnimator?.cancel()
        overScroller.abortAnimation()
        isFling = false
        velocityTracker?.recycle()
        velocityTracker = null
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            AppearancePreferences.APP_CORNER_RADIUS -> {
                barCornerRadiusPx = computeCornerRadius()
                invalidate()
            }
        }
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        super.onThemeChanged(theme, animate)
        applyThemeColors()
    }

    override fun onAccentChanged(accent: Accent) {
        super.onAccentChanged(accent)
        applyThemeColors()
    }

    /**
     * Linearly interpolates each ARGB channel of [colorA] toward [colorB] by [fraction].
     *
     * @param colorA start color (ARGB)
     * @param colorB end color (ARGB)
     * @param fraction blend factor in [0.0, 1.0]; 0 = full [colorA], 1 = full [colorB]
     * @return the blended ARGB color
     */
    private fun blendArgb(colorA: Int, colorB: Int, fraction: Float): Int = Color.argb(
            (Color.alpha(colorA) + (Color.alpha(colorB) - Color.alpha(colorA)) * fraction).toInt(),
            (Color.red(colorA) + (Color.red(colorB) - Color.red(colorA)) * fraction).toInt(),
            (Color.green(colorA) + (Color.green(colorB) - Color.green(colorA)) * fraction).toInt(),
            (Color.blue(colorA) + (Color.blue(colorB) - Color.blue(colorA)) * fraction).toInt()
    )

    companion object {

        /** Rendering mode: bars grow from the center axis upward only (default). */
        const val WAVEFORM_MODE_HALF = 0

        /** Rendering mode: bars grow symmetrically above and below the center axis. */
        const val WAVEFORM_MODE_FULL = 1

        /**
         * Rendering mode: half spectrum at the top with a faded, inverted mirror image below,
         * separated by a thin line and a small gap.
         */
        const val WAVEFORM_MODE_REFLECTION = 2

        /** Labels are positioned at the top edge of the view. */
        const val LABEL_GRAVITY_TOP = 0

        /** Labels are positioned at the vertical center of the view. */
        const val LABEL_GRAVITY_CENTER = 1

        /** Labels are positioned at the bottom edge of the view (default). */
        const val LABEL_GRAVITY_BOTTOM = 2

        /** No pill background is drawn behind the labels (default). */
        const val LABEL_HIGHLIGHT_NONE = -1

        /** Filled pill background using the theme highlight color, matching [HighlightTextView][app.simple.felicity.decorations.highlight.HighlightTextView] flat mode. */
        const val LABEL_HIGHLIGHT_FLAT = 0

        /** Stroke-only pill using the accent color, matching [HighlightTextView][app.simple.felicity.decorations.highlight.HighlightTextView] outline mode. */
        const val LABEL_HIGHLIGHT_OUTLINE = 1

        /** Filled pill plus an accent-colored stroke, matching [HighlightTextView][app.simple.felicity.decorations.highlight.HighlightTextView] both mode. */
        const val LABEL_HIGHLIGHT_BOTH = 2

        /**
         * Minimum alpha applied to bars at the far edges of the view.
         * Bars at the center playhead always render at full (1.0) alpha.
         * Value range: [0.0, 1.0].
         */
        private const val HIGHLIGHT_MIN_ALPHA = 0.35f

        /** Number of bar-widths on each side of the playhead over which the played/unplayed color crossfades. */
        private const val TRANSITION_ZONE = 3f

        // Default dimension values in dp
        private const val DEFAULT_BAR_WIDTH_DP = 6f
        private const val DEFAULT_BAR_SPACING_DP = 3f
        private const val DEFAULT_FADE_EDGE_LENGTH_DP = 72f
        private const val DEFAULT_LABEL_TEXT_SIZE_DP = 12f
        private const val DEFAULT_LABEL_PADDING_DP = 12f

        /** Default horizontal inset of labels from the left and right view edges, in dp. */
        private const val DEFAULT_LABEL_EDGE_MARGIN_DP = 16f
        private const val DEFAULT_MIN_BAR_HEIGHT_DP = 2f
        private const val DEFAULT_LABEL_STROKE_WIDTH_DP = 0.5f

        /** Minimum view height reserved for the waveform area, in dp. */
        private const val MIN_VIEW_HEIGHT_DP = 64f

        // Animation durations in milliseconds
        private const val COLOR_TRANSITION_DURATION_MS = 300L
        private const val BAR_ANIM_DURATION_MS = 700L
        private const val PROGRESS_ANIM_DURATION_MS = 520L

        /** Duration of the label scale animation triggered by drag start and drag end. */
        private const val LABEL_ANIM_DURATION_MS = 280L

        /** Duration of the seek line fade-in / fade-out animation, in milliseconds. */
        private const val SEEK_LINE_ANIM_DURATION_MS = 280L

        /**
         * Duration of the left-bar fade-out animation triggered by [setDurationWithReset].
         * Played bars fade away over this period while the waveform stays visually still.
         */
        private const val LEFT_FADE_DURATION_MS = 450L


        // Thresholds and interpolation factors
        private const val PROGRESS_SNAP_THRESHOLD_MS = 80L

        /** Units (pixels per interval) used when computing fling velocity from the tracker. */
        private const val FLING_VELOCITY_UNITS = 1000

        /** Deceleration exponent passed to [DecelerateInterpolator] for bar height animation. */
        private const val BAR_ANIM_DECELERATE = 3f

        /** Horizontal padding factor applied to [labelPaddingPx] to compute pill side inset. */
        private const val PILL_H_PAD_FACTOR = 0.72f

        /** Vertical padding factor applied to [labelPaddingPx] to compute pill top/bottom inset. */
        private const val PILL_V_PAD_FACTOR = 0.22f

        /**
         * Scale applied to label text and pill while the user is seeking.
         * Labels grow slightly beyond their normal size so they are easy to read during a drag.
         */
        private const val LABEL_SEEK_ACTIVE_SCALE = 1.0f

        /** Stroke width of the seek line drawn at the center playhead position, in dp. */
        private const val SEEK_LINE_WIDTH_DP = 2f

        /**
         * Maximum alpha of the seek line as a fraction of 255.
         * Keeping this below 1.0 ensures the line remains translucent so bars behind it are visible.
         */
        private const val SEEK_LINE_MAX_ALPHA = 0.70f

        /** Vertical inset applied to both ends of the seek line so it does not touch the view boundaries, in dp. */
        private const val SEEK_LINE_VERTICAL_PADDING_DP = 12f

        /** Default gap in dp between the main waveform's bottom edge and the center separator line. */
        private const val REFLECTION_TOP_GAP_DP = 0f

        /** Default gap in dp between the center separator line and the reflected waveform's top edge. */
        private const val REFLECTION_BOTTOM_GAP_DP = 3f

        /** Height in dp of the thin horizontal separator line drawn in reflection mode. */
        private const val REFLECTION_LINE_HEIGHT_DP = 1f

        /**
         * Default [reflectionAlpha]: uniform alpha applied to all reflected bars in
         * [WAVEFORM_MODE_REFLECTION]. 0.4 gives a noticeable but clearly secondary mirror image
         * that does not compete with the main waveform above.
         */
        const val DEFAULT_REFLECTION_ALPHA = 0.4f

        /**
         * Alpha fraction [0.0, 1.0] for the separator line in reflection mode.
         * A value below 1.0 keeps the line subtle so it does not overpower the waveform.
         */
        private const val REFLECTION_LINE_ALPHA = 0.45f
    }
}
