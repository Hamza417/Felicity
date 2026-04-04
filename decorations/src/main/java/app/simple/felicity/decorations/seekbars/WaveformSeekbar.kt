package app.simple.felicity.decorations.seekbars

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
import android.util.Log
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
 * Supports two display modes controlled by [isFullWaveform]:
 *  - **Half** (default): bars grow from the center axis upward only.
 *  - **Full**: bars grow symmetrically above *and* below the center axis.
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
        /** Called each time the user's seek position changes during a drag or fling. */
        fun onSeekTo(positionMs: Long)

        /** Called when the user first touches the seekbar to begin dragging. */
        fun onSeekStart() {}

        /** Called when the user lifts their finger; [positionMs] is the final position. */
        fun onSeekEnd(positionMs: Long) {}
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
     * When `true` the waveform is rendered symmetrically above and below the
     * center axis (full waveform). When `false` (default) bars grow from the
     * center axis upward only (half waveform).
     */
    var isFullWaveform: Boolean = false
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
    private var leftLabelProvider: LeftLabelProvider? = null
    private var rightLabelProvider: RightLabelProvider? = null

    private var playedColor: Int = Color.WHITE
    private var unplayedColor: Int = Color.GRAY
    private var labelTextColor: Int = Color.GRAY

    private var barWidthPx: Float
    private var barSpacingPx: Float
    private var barCornerRadiusPx: Float = 0f
    private var fadeEdgeLengthPx: Float
    private var labelTextSizePx: Float
    private var labelPaddingPx: Float
    private var minBarHeightPx: Float

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

    // Pre-allocated gradient shader objects; rebuilt only when the view width changes
    private var leftGradient: LinearGradient? = null
    private var rightGradient: LinearGradient? = null
    private var lastWidth = 0f

    // Touch drag state
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartProgressMs = 0L
    private var dragCurrentProgressMs = 0L

    // Smooth scroll animation for programmatic progress updates
    private var progressAnimator: ValueAnimator? = null
    private var animatedProgressMs: Long = 0L

    // Fling infrastructure — momentum scroll after a drag gesture
    private val overScroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private var isFling = false
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()

    /**
     * Runnable posted via [postOnAnimation] to advance the fling animation one frame at a time.
     * Reads the current position from [overScroller] and converts it back to milliseconds.
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
                    seekListener?.onSeekTo(newProgress)
                }
                invalidate()
                postOnAnimation(this)
            } else {
                isFling = false
            }
        }
    }

    init {
        val d = resources.displayMetrics.density
        barWidthPx = 6f * d
        barSpacingPx = 3f * d
        fadeEdgeLengthPx = 72f * d
        labelTextSizePx = 11f * d
        labelPaddingPx = 10f * d
        minBarHeightPx = 2f * d

        // Read XML attributes when they are provided
        if (attrs != null) {
            Log.w("WaveformSeekbar", "XML attributes are not supported for WaveformSeekbar; " +
                    "please set properties programmatically after initialization.")
            val ta = context.obtainStyledAttributes(attrs, R.styleable.WaveformSeekbar, defStyleAttr, 0)
            try {
                isFullWaveform = ta.getBoolean(R.styleable.WaveformSeekbar_wsbFullWaveform, false)
                barWidthPx = ta.getDimension(R.styleable.WaveformSeekbar_wsbBarWidth, barWidthPx)
                barSpacingPx = ta.getDimension(R.styleable.WaveformSeekbar_wsbBarSpacing, barSpacingPx)
                fadeEdgeLengthPx = ta.getDimension(R.styleable.WaveformSeekbar_wsbFadeEdgeLength, fadeEdgeLengthPx)
                labelGravity = ta.getInt(R.styleable.WaveformSeekbar_wsbLabelGravity, LABEL_GRAVITY_BOTTOM)
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
        playedColor = ThemeManager.accent.primaryAccentColor
        unplayedColor = ThemeManager.accent.secondaryAccentColor
        labelTextColor = ThemeManager.theme.textViewTheme.secondaryTextColor
        labelPaint.color = labelTextColor
        labelPaint.textSize = labelTextSizePx
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        val minH = (64f * d).toInt() + paddingTop + paddingBottom
        val resolvedH = resolveSize(minH, heightMeasureSpec)
        val resolvedW = resolveSize(suggestedMinimumWidth + paddingLeft + paddingRight, widthMeasureSpec)
        setMeasuredDimension(resolvedW, resolvedH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (amplitudes.isEmpty() || durationMs <= 0L) return

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

        for (i in amplitudes.indices) {
            val barCenterX = i * barStep - scrollOffset + centerX
            // Cull bars that are fully outside the visible area
            if (barCenterX + barWidthPx / 2f < 0f || barCenterX - barWidthPx / 2f > w) continue

            val amp = amplitudes[i].coerceIn(0f, 1f)
            val isPast = barCenterX <= centerX
            barPaint.color = if (isPast) playedColor else unplayedColor

            // Bars closest to the center playhead render at full opacity; bars toward the outer
            // edges fade toward HIGHLIGHT_MIN_ALPHA, creating a spotlight effect.
            val distFraction = (abs(barCenterX - centerX) / (w / 2f)).coerceIn(0f, 1f)
            barPaint.alpha = (255 * (HIGHLIGHT_MIN_ALPHA + (1f - HIGHLIGHT_MIN_ALPHA) * (1f - distFraction))).toInt()

            if (isFullWaveform) {
                // Symmetric: bar grows from center upward AND downward
                val halfH = max(minBarHeightPx / 2f, amp * (maxBarArea / 2f))
                barRect.set(
                        barCenterX - barWidthPx / 2f,
                        centerY - halfH,
                        barCenterX + barWidthPx / 2f,
                        centerY + halfH
                )
            } else {
                // Half: bar grows upward from center only
                val barH = max(minBarHeightPx, amp * maxBarArea)
                barRect.set(
                        barCenterX - barWidthPx / 2f,
                        centerY - barH,
                        barCenterX + barWidthPx / 2f,
                        centerY
                )
            }

            canvas.drawRoundRect(barRect, barCornerRadiusPx, barCornerRadiusPx, barPaint)
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
     */
    private fun drawLabels(canvas: Canvas, currentProgressMs: Long, w: Float, h: Float) {
        val leftLabel = leftLabelProvider?.getLabel(currentProgressMs, 0L, durationMs)
        val rightLabel = rightLabelProvider?.getLabel(currentProgressMs, 0L, durationMs)

        labelPaint.textSize = labelTextSizePx
        labelPaint.color = labelTextColor

        val textY = when (labelGravity) {
            LABEL_GRAVITY_TOP -> paddingTop.toFloat() + labelTextSizePx + labelPaddingPx / 2f
            LABEL_GRAVITY_CENTER -> h / 2f + labelTextSizePx / 3f
            else -> h - labelPaddingPx / 2f  // LABEL_GRAVITY_BOTTOM (default)
        }

        if (!leftLabel.isNullOrEmpty()) {
            labelPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(leftLabel, labelPaddingPx, textY, labelPaint)
        }

        if (!rightLabel.isNullOrEmpty()) {
            labelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(rightLabel, w - labelPaddingPx, textY, labelPaint)
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
                    seekListener?.onSeekTo(dragCurrentProgressMs)
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000) // pixels per second
                    val xVelocity = velocityTracker?.xVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null

                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    seekListener?.onSeekEnd(dragCurrentProgressMs)

                    progressMs = dragCurrentProgressMs
                    animatedProgressMs = dragCurrentProgressMs

                    // Launch a fling if the release velocity exceeds the system threshold
                    val barStep = barWidthPx + barSpacingPx
                    if (amplitudes.isNotEmpty() && barStep > 0f && abs(xVelocity) > minFlingVelocity) {
                        val totalScrollPx = amplitudes.size * barStep
                        val scrollPx = (animatedProgressMs.toFloat() / durationMs.toFloat() * totalScrollPx).toInt()
                        // Negate xVelocity: finger moving left (negative) → fling forward (positive scroll)
                        overScroller.fling(
                                scrollPx, 0,
                                (-xVelocity).toInt(), 0,
                                0, totalScrollPx.toInt(),
                                0, 0
                        )
                        isFling = true
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
     * Supplies the normalized amplitude data for the currently loaded track.
     * Each entry represents a single second of audio; values must be in [0.0, 1.0].
     *
     * @param data normalized amplitude array, one value per second
     */
    fun setAmplitudes(data: FloatArray) {
        amplitudes = data
        invalidate()
    }

    /**
     * Updates the current playback position, optionally animating the waveform scroll.
     *
     * @param positionMs playback position in milliseconds
     * @param fromUser   `true` when the change originates from user interaction
     * @param animate    whether to animate the transition from the current position
     */
    fun setProgress(positionMs: Long, fromUser: Boolean = false, animate: Boolean = false) {
        if (isDragging || isFling) return
        val target = positionMs.coerceIn(0L, durationMs)
        progressMs = target

        if (!animate || fromUser) {
            progressAnimator?.cancel()
            animatedProgressMs = target
            invalidate()
        } else {
            val start = animatedProgressMs
            if (abs(start - target) < 80L) {
                progressAnimator?.cancel()
                animatedProgressMs = target
                invalidate()
                return
            }
            progressAnimator?.cancel()
            progressAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 220L
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
     * Resets the seekbar to its initial state, clearing amplitude data and progress.
     */
    fun reset() {
        progressAnimator?.cancel()
        overScroller.abortAnimation()
        isFling = false
        amplitudes = FloatArray(0)
        progressMs = 0L
        animatedProgressMs = 0L
        durationMs = 0L
        isDragging = false
        invalidate()
    }

    /** Registers the seek event listener. */
    fun setOnSeekListener(listener: OnSeekListener?) {
        seekListener = listener
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

    companion object {

        /** Labels are positioned at the top edge of the view. */
        const val LABEL_GRAVITY_TOP = 0

        /** Labels are positioned at the vertical center of the view. */
        const val LABEL_GRAVITY_CENTER = 1

        /** Labels are positioned at the bottom edge of the view (default). */
        const val LABEL_GRAVITY_BOTTOM = 2

        /**
         * Minimum alpha applied to bars at the far edges of the view.
         * Bars at the center playhead always render at full (1.0) alpha.
         * Value range: [0.0, 1.0].
         */
        private const val HIGHLIGHT_MIN_ALPHA = 0.35f
    }
}