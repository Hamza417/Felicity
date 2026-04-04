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
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
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
 * pinned to the horizontal center of the view while the waveform itself scrolls
 * beneath it.
 *
 * Supports two display modes controlled by [isFullWaveform]:
 *  - **Half** (default): bars grow from the center axis upward only.
 *  - **Full**: bars grow symmetrically above *and* below the center axis.
 *
 * Horizontal blur fading edges are applied on both sides using a
 * [PorterDuff.Mode.DST_OUT] gradient layer, following the same technique
 * as [app.simple.felicity.decorations.lrc.view.ModernLrcView].
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
        /** Called each time the user's seek position changes during a drag. */
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

    private var seekListener: OnSeekListener? = null
    private var leftLabelProvider: LeftLabelProvider? = null
    private var rightLabelProvider: RightLabelProvider? = null

    private var playedColor: Int = Color.WHITE
    private var unplayedColor: Int = Color.GRAY
    private var labelTextColor: Int = Color.GRAY

    private var barWidthPx: Float
    private var barSpacingPx: Float
    private var barCornerRadiusPx: Float
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
    }

    private val barRect = RectF()

    // Pre-allocated gradient shader objects; recreated only in onSizeChanged
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

    init {
        val d = resources.displayMetrics.density
        barWidthPx = 6f * d
        barSpacingPx = 3f * d
        barCornerRadiusPx = 1.5f * d
        fadeEdgeLengthPx = 72f * d
        labelTextSizePx = 11f * d
        labelPaddingPx = 10f * d
        minBarHeightPx = 2f * d

        if (!isInEditMode) {
            applyThemeColors()
        }

        // Hardware layer is needed for DST_OUT compositing to work on API < 28
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isClickable = true
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

        // scrollOffset is in pixels; barCenter at index i = i*barStep - scrollOffset + centerX
        val totalScrollRange = amplitudes.size * barStep
        val scrollOffset = progressFraction * totalScrollRange

        // Save a compositing layer so the DST_OUT fades erase the bars at the edges
        val layerId = canvas.saveLayer(0f, 0f, w, h, null)

        // Reserve space at the bottom for labels so bars don't overlap them
        val labelAreaH = labelTextSizePx + labelPaddingPx * 1.5f
        val maxBarArea = h - labelAreaH - paddingTop - paddingBottom

        for (i in amplitudes.indices) {
            val barCenterX = i * barStep - scrollOffset + centerX
            // Cull bars that are fully outside the view
            if (barCenterX + barWidthPx / 2f < 0f || barCenterX - barWidthPx / 2f > w) continue

            val amp = amplitudes[i].coerceIn(0f, 1f)
            val isPast = barCenterX <= centerX
            barPaint.color = if (isPast) playedColor else unplayedColor

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

        // Left horizontal fade (opaque black → transparent, erasing bars at the left edge)
        if (w != lastWidth) rebuildGradients(w)
        leftGradient?.let { fadePaint.shader = it }
        canvas.drawRect(0f, 0f, fadeEdgeLengthPx, h, fadePaint)

        // Right horizontal fade (transparent → opaque black, erasing bars at the right edge)
        rightGradient?.let { fadePaint.shader = it }
        canvas.drawRect(w - fadeEdgeLengthPx, 0f, w, h, fadePaint)

        canvas.restoreToCount(layerId)

        // Draw time labels outside the compositing layer so they are not erased
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

    private fun drawLabels(canvas: Canvas, currentProgressMs: Long, w: Float, h: Float) {
        val leftLabel = leftLabelProvider?.getLabel(currentProgressMs, 0L, durationMs)
        val rightLabel = rightLabelProvider?.getLabel(currentProgressMs, 0L, durationMs)

        labelPaint.textSize = labelTextSizePx
        labelPaint.color = labelTextColor

        // Place labels at the bottom of the view
        val textY = h - labelPaddingPx / 2f

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
                parent?.requestDisallowInterceptTouchEvent(true)
                isDragging = true
                dragStartX = event.x
                dragStartProgressMs = if (progressAnimator?.isRunning == true) animatedProgressMs else progressMs
                dragCurrentProgressMs = dragStartProgressMs
                seekListener?.onSeekStart()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val deltaX = event.x - dragStartX
                    val barStep = barWidthPx + barSpacingPx
                    val deltaMs = if (amplitudes.isNotEmpty() && barStep > 0f) {
                        // Moving the finger left = seeking forward; right = seeking backward
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
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    seekListener?.onSeekEnd(dragCurrentProgressMs)
                    // Commit the drag position as the real progress
                    progressMs = dragCurrentProgressMs
                    animatedProgressMs = dragCurrentProgressMs
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
        if (isDragging) return
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
     * The view calls this function on every draw pass to obtain the current string.
     *
     * @param provider label string provider, or `null` to hide the label
     */
    fun setLeftLabelProvider(provider: LeftLabelProvider?) {
        leftLabelProvider = provider
        invalidate()
    }

    /**
     * Sets the provider for the right-side (total or remaining) time label.
     * The view calls this function on every draw pass to obtain the current string.
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
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        // No seekbar-specific preference keys at this time
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        super.onThemeChanged(theme, animate)
        applyThemeColors()
    }

    override fun onAccentChanged(accent: Accent) {
        super.onAccentChanged(accent)
        applyThemeColors()
    }
}