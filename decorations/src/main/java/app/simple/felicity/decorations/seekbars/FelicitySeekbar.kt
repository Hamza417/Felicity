package app.simple.felicity.decorations.seekbars

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import app.simple.felicity.decoration.R
import app.simple.felicity.theme.managers.ThemeManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FelicitySeekbar @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnSeekChangeListener {
        fun onProgressChanged(seekbar: FelicitySeekbar, progress: Int, fromUser: Boolean)
        fun onStartTrackingTouch(seekbar: FelicitySeekbar) {}
        fun onStopTrackingTouch(seekbar: FelicitySeekbar) {}
    }

    private var listener: OnSeekChangeListener? = null

    // Range: [minProgress..maxProgress]
    private var minProgress = 0
    private var maxProgress = 100
    private var progressInternal = 0f // current float progress for animation, in absolute units within [min..max]
    private var defaultProgress = 0 // value to reset to on long press

    @ColorInt
    private var trackColor: Int = if (isInEditMode) {
        Color.LTGRAY
    } else {
        ThemeManager.theme.viewGroupTheme.highlightColor
    }

    @ColorInt
    private var progressColor: Int = if (isInEditMode) {
        Color.BLUE
    } else {
        ThemeManager.accent.primaryAccentColor
    }

    @ColorInt
    private var thumbRingColor: Int = Color.WHITE

    @ColorInt
    private var thumbInnerColor: Int = Color.WHITE

    private var trackHeightPx: Float
    private var thumbRadiusPx: Float
    private var thumbRingWidthPx: Float

    // New: horizontal width of pill-shaped thumb (full width, not half). Default set after radius init.
    private var thumbWidthPx: Float

    private var smudgeEnabled = true
    private var smudgeRadius = 10f
    private var smudgeColor = progressColor
    private var smudgeOffsetY = 0f
    private var thumbShadowRadius = 0f
    private var thumbShadowColor = progressColor

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val thumbRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val thumbInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val smudgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val thumbShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val trackRect = RectF()
    private val smudgeRect = RectF()
    private val progressRect = RectF()

    // Reusable temp rects for thumb drawing to avoid allocations
    private val thumbOuterRect = RectF()
    private val thumbStrokeRect = RectF()
    private val thumbInnerRect = RectF()

    private var isDragging = false
    private var thumbScale = 1f
    private var thumbScaleAnimator: ValueAnimator? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var longPressTriggered = false
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private var longPressRunnable: Runnable? = null
    private var downOnThumb = false

    // Spring animation support
    private val progressProperty = object : FloatPropertyCompat<FelicitySeekbar>("felicityIntProgress") {
        override fun getValue(view: FelicitySeekbar): Float = view.progressInternal
        override fun setValue(view: FelicitySeekbar, value: Float) {
            view.progressInternal = value.coerceIn(minProgress.toFloat(), maxProgress.toFloat())
            invalidate()
            // During animation, treat as programmatic (fromUser = false)
            listener?.onProgressChanged(this@FelicitySeekbar, getProgress(), false)
        }
    }

    private var animateFromUser = false
    private val springAnimation = SpringAnimation(this, progressProperty).apply {
        spring = SpringForce().apply {
            stiffness = SpringForce.STIFFNESS_LOW
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }
        addEndListener { _, _, _, _ ->
            listener?.onProgressChanged(this@FelicitySeekbar, getProgress(), animateFromUser)
            // restore base spring if it was altered for fast snap
            spring?.stiffness = baseStiffness
            spring?.dampingRatio = baseDamping
        }
    }
    private val baseStiffness = SpringForce.STIFFNESS_LOW
    private val baseDamping = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
    private val fastStiffness = SpringForce.STIFFNESS_HIGH
    private val fastDamping = SpringForce.DAMPING_RATIO_NO_BOUNCY

    init {
        val d = resources.displayMetrics.density
        trackHeightPx = 4f * d
        thumbRadiusPx = 12f * d
        thumbRingWidthPx = 4f * d
        // Default pill width: 3x radius (i.e., 1.5x diameter)
        thumbWidthPx = thumbRadiusPx * 3f

        context.theme.obtainStyledAttributes(attrs, R.styleable.FelicitySeekbar, defStyleAttr, 0).apply {
            try {
                // Read min first so subsequent attributes can clamp correctly
                if (hasValue(R.styleable.FelicitySeekbar_felicityMin)) {
                    val mn = try {
                        getFloat(R.styleable.FelicitySeekbar_felicityMin, minProgress.toFloat()).toInt()
                    } catch (e: Exception) {
                        getInt(R.styleable.FelicitySeekbar_felicityMin, minProgress)
                    }
                    setMinInternal(mn)
                }
                if (hasValue(R.styleable.FelicitySeekbar_felicityMax)) {
                    val m = try {
                        getFloat(R.styleable.FelicitySeekbar_felicityMax, maxProgress.toFloat()).toInt()
                    } catch (e: Exception) {
                        getInt(R.styleable.FelicitySeekbar_felicityMax, maxProgress)
                    }
                    setMaxInternal(m)
                }
                if (hasValue(R.styleable.FelicitySeekbar_felicityProgress)) {
                    val tv = peekValue(R.styleable.FelicitySeekbar_felicityProgress)
                    progressInternal = when (tv.type) {
                        android.util.TypedValue.TYPE_FLOAT -> getFloat(R.styleable.FelicitySeekbar_felicityProgress, minProgress.toFloat())
                        android.util.TypedValue.TYPE_INT_DEC, android.util.TypedValue.TYPE_INT_HEX -> getInt(R.styleable.FelicitySeekbar_felicityProgress, minProgress).toFloat()
                        else -> getInt(R.styleable.FelicitySeekbar_felicityProgress, minProgress).toFloat()
                    }
                }
                progressInternal = progressInternal.coerceIn(minProgress.toFloat(), maxProgress.toFloat())

                trackColor = getColor(R.styleable.FelicitySeekbar_felicityTrackColor, trackColor)
                progressColor = getColor(R.styleable.FelicitySeekbar_felicityProgressColor, progressColor)
                thumbRingColor = getColor(R.styleable.FelicitySeekbar_felicityThumbRingColor, thumbRingColor)
                thumbInnerColor = getColor(R.styleable.FelicitySeekbar_felicityThumbInnerColor, thumbInnerColor)
                trackHeightPx = getDimension(R.styleable.FelicitySeekbar_felicityTrackHeight, trackHeightPx)
                thumbRadiusPx = getDimension(R.styleable.FelicitySeekbar_felicityThumbRadius, thumbRadiusPx)
                thumbWidthPx = getDimension(R.styleable.FelicitySeekbar_felicityThumbWidth, thumbWidthPx)
                thumbRingWidthPx = getDimension(R.styleable.FelicitySeekbar_felicityThumbRingWidth, thumbRingWidthPx)
                smudgeEnabled = getBoolean(R.styleable.FelicitySeekbar_felicitySmudgeEnabled, smudgeEnabled)
                smudgeRadius = getDimension(R.styleable.FelicitySeekbar_felicitySmudgeRadius, 2f * d)
                smudgeColor = getColor(R.styleable.FelicitySeekbar_felicitySmudgeColor, smudgeColor)
                smudgeOffsetY = getDimension(R.styleable.FelicitySeekbar_felicitySmudgeOffsetY, 0f)
                thumbShadowRadius = getDimension(R.styleable.FelicitySeekbar_felicityThumbShadowRadius, 6f * d)
                thumbShadowColor = getColor(R.styleable.FelicitySeekbar_felicityThumbShadowColor, thumbShadowColor)
            } finally {
                recycle()
            }
        }

        // If width is smaller than diameter, coerce to diameter to avoid inverted corners
        thumbWidthPx = max(thumbWidthPx, thumbRadiusPx * 2f)

        applyPaintColors()
        setupSmudgeAndShadow()
        applyThemeColors()

        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(android.R.string.untitled)
        // Ensure no outline-based clipping occurs (elevation/outline providers)
        clipToOutline = false
        clipBounds = null
        outlineProvider = null
    }

    private fun rangeSpan(): Float = (maxProgress - minProgress).toFloat()

    private fun valueToFraction(value: Float): Float {
        val span = rangeSpan()
        return if (span <= 0f) 0f else (value - minProgress) / span
    }

    private fun fractionToValue(fraction: Float): Float {
        val span = rangeSpan()
        return if (span <= 0f) minProgress.toFloat() else minProgress + (fraction * span)
    }

    private fun applyPaintColors() {
        trackPaint.color = trackColor
        progressPaint.color = progressColor
        thumbRingPaint.color = thumbRingColor
        thumbRingPaint.strokeWidth = thumbRingWidthPx
        thumbInnerPaint.color = thumbInnerColor
    }

    private fun applyThemeColors() {
        if (isInEditMode.not()) {
            progressColor = ThemeManager.accent.primaryAccentColor
            trackColor = ThemeManager.theme.viewGroupTheme.highlightColor
            thumbRingColor = Color.WHITE
            thumbInnerColor = progressColor
            smudgeColor = progressColor
            thumbShadowColor = progressColor
        }
    }

    private fun setupSmudgeAndShadow() {
        if (smudgeEnabled || thumbShadowRadius > 0f) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }
        if (smudgeEnabled) {
            smudgePaint.color = smudgeColor
            smudgePaint.maskFilter = BlurMaskFilter(smudgeRadius, BlurMaskFilter.Blur.NORMAL)
        } else {
            smudgePaint.maskFilter = null
        }
        if (thumbShadowRadius > 0f) {
            thumbShadowPaint.setShadowLayer(thumbShadowRadius, 0f, 0f, thumbShadowColor)
            thumbShadowPaint.color = Color.TRANSPARENT
        } else {
            thumbShadowPaint.clearShadowLayer()
        }
    }

    fun setOnSeekChangeListener(listener: OnSeekChangeListener?) {
        this.listener = listener
    }

    fun setMax(max: Int) {
        setMaxInternal(max)
        if (progressInternal > maxProgress) {
            progressInternal = maxProgress.toFloat()
            invalidate()
        }
        if (defaultProgress > maxProgress) {
            defaultProgress = maxProgress
        }
    }

    private fun setMaxInternal(max: Int) {
        maxProgress = max
        if (maxProgress < minProgress) {
            // Keep range valid: shift min down to max
            minProgress = maxProgress
        }
    }

    fun getMax(): Int = maxProgress

    fun setMin(min: Int) {
        setMinInternal(min)
        if (progressInternal < minProgress) {
            progressInternal = minProgress.toFloat()
            invalidate()
        }
        if (defaultProgress < minProgress) {
            defaultProgress = minProgress
        }
    }

    private fun setMinInternal(min: Int) {
        minProgress = min
        if (maxProgress < minProgress) {
            // Keep range valid: raise max up to min
            maxProgress = minProgress
        }
    }

    fun getMin(): Int = minProgress

    fun setRange(min: Int, max: Int) {
        // Preserve intent even if min>max: collapse to single point at the midpoint after ordering
        if (min <= max) {
            minProgress = min
            maxProgress = max
        } else {
            minProgress = max
            maxProgress = min
        }
        progressInternal = progressInternal.coerceIn(minProgress.toFloat(), maxProgress.toFloat())
        defaultProgress = defaultProgress.coerceIn(minProgress, maxProgress)
        invalidate()
    }

    fun setProgress(progress: Int, fromUser: Boolean = false, animate: Boolean = false) {
        val target = progress.coerceIn(minProgress, maxProgress).toFloat()
        if (!animate) {
            if (springAnimation.isRunning) springAnimation.cancel()
            if (progressInternal == target) return
            progressInternal = target
            invalidate()
            listener?.onProgressChanged(this, getProgress(), fromUser)
        } else {
            animateFromUser = fromUser
            springAnimation.cancel()
            springAnimation.setStartValue(progressInternal)
            springAnimation.spring.finalPosition = target
            springAnimation.start()
        }
    }

    fun getProgress(): Int = progressInternal.toInt()

    fun setDefaultProgress(value: Int) {
        defaultProgress = value.coerceIn(minProgress, maxProgress)
    }

    fun resetToDefault(animate: Boolean = true) {
        setProgress(defaultProgress, fromUser = false, animate = animate)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val baseHeight = max(trackHeightPx, thumbRadiusPx * 2f)
        val verticalBlur = max(thumbShadowRadius, if (smudgeEnabled) (smudgeRadius + abs(smudgeOffsetY)) else 0f)
        val desiredHeight = (paddingTop + paddingBottom + baseHeight + verticalBlur * 2f).toInt()
        val resolvedWidth = resolveSize(suggestedMinimumWidth + paddingLeft + paddingRight, widthMeasureSpec)
        val resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }

    private fun horizontalOutset(): Float = max(thumbShadowRadius, if (smudgeEnabled) smudgeRadius else 0f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val hOut = horizontalOutset()
        // Ensure full thumb fits horizontally: use half of pill width
        val safeInset = thumbWidthPx / 2f
        val left = paddingLeft.toFloat() + hOut + safeInset
        val right = (width - paddingRight).toFloat() - hOut - safeInset
        if (right <= left) return
        val centerY = height / 2f + if (smudgeEnabled) smudgeOffsetY else 0f
        val trackRadius = trackHeightPx / 2f
        trackRect.set(left, centerY - trackRadius, right, centerY + trackRadius)

        val clampedFraction = valueToFraction(progressInternal).coerceIn(0f, 1f)
        val progressRight = left + (right - left) * clampedFraction

        if (smudgeEnabled && progressRight > left) {
            smudgeRect.set(left, trackRect.top, progressRight, trackRect.bottom)
            canvas.drawRoundRect(smudgeRect, trackRadius, trackRadius, smudgePaint)
        }
        canvas.drawRoundRect(trackRect, trackRadius, trackRadius, trackPaint)
        if (progressRight > left) {
            progressRect.set(left, trackRect.top, progressRight, trackRect.bottom)
            canvas.drawRoundRect(progressRect, trackRadius, trackRadius, progressPaint)
        }

        // Thumb (pill-shaped)
        val cx = progressRight
        val cy = trackRect.centerY()
        val scaledR = thumbRadiusPx * thumbScale
        val scaledHalfW = (thumbWidthPx / 2f) * thumbScale

        // Outer rect for shadow and overall silhouette
        thumbOuterRect.set(cx - scaledHalfW, cy - scaledR, cx + scaledHalfW, cy + scaledR)

        if (thumbShadowRadius > 0f) {
            canvas.drawRoundRect(thumbOuterRect, scaledR, scaledR, thumbShadowPaint)
        }

        // Inner fill (beneath the ring)
        if (thumbInnerColor != Color.TRANSPARENT) {
            thumbInnerRect.set(thumbOuterRect)
            val inset = thumbRingWidthPx / 2f
            thumbInnerRect.inset(inset, inset)
            val innerR = max(0f, scaledR - inset)
            canvas.drawRoundRect(thumbInnerRect, innerR, innerR, thumbInnerPaint)
        }

        // Stroke ring
        thumbStrokeRect.set(thumbOuterRect)
        val strokeInset = thumbRingWidthPx / 2f
        thumbStrokeRect.inset(strokeInset, strokeInset)
        val ringR = max(0f, scaledR - strokeInset)
        canvas.drawRoundRect(thumbStrokeRect, ringR, ringR, thumbRingPaint)
    }

    private fun isPointOnThumb(x: Float, y: Float): Boolean {
        val hOut = horizontalOutset()
        val safeInset = thumbWidthPx / 2f
        val left = paddingLeft.toFloat() + hOut + safeInset
        val right = (width - paddingRight).toFloat() - hOut - safeInset
        if (right <= left) return false
        val progressX = left + (right - left) * valueToFraction(progressInternal).coerceIn(0f, 1f)
        val cy = height / 2f + if (smudgeEnabled) smudgeOffsetY else 0f
        val dx = x - progressX
        val dy = y - cy
        val r = thumbRadiusPx * thumbScale
        val halfW = (thumbWidthPx / 2f) * thumbScale
        val bodyHalfW = max(0f, halfW - r)
        // Inside central rectangle
        if (abs(dx) <= bodyHalfW && abs(dy) <= r) return true
        // Check circular caps
        val leftCx = -bodyHalfW
        val rightCx = bodyHalfW
        val dlx = dx - leftCx
        val drx = dx - rightCx
        return (dlx * dlx + dy * dy <= r * r) || (drx * drx + dy * dy <= r * r)
    }

    private fun startThumbScale(up: Boolean = false) {
        val target = if (!up) 1.15f else 1f
        if (thumbScale == target) return
        thumbScaleAnimator?.cancel()
        thumbScaleAnimator = ValueAnimator.ofFloat(thumbScale, target).apply {
            duration = if (!up) 220 else 360
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                thumbScale = (anim.animatedValue as Float)
                invalidate()
            }
            start()
        }
    }

    private fun scheduleLongPress() {
        cancelLongPress()
        longPressRunnable = Runnable {
            if (isPressed && downOnThumb) {
                longPressTriggered = true
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                resetToDefault(animate = true)
            }
        }.also { postDelayed(it, longPressTimeout) }
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        longPressRunnable?.let { removeCallbacks(it) }
        longPressRunnable = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isEnabled) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                isDragging = true
                downX = event.x
                downY = event.y
                downOnThumb = isPointOnThumb(downX, downY)
                longPressTriggered = false
                if (downOnThumb) {
                    scheduleLongPress()
                    startThumbScale(up = false)
                } else {
                    // fast animate to tap position
                    val hOut = horizontalOutset()
                    val safeInset = thumbWidthPx / 2f
                    val left = paddingLeft.toFloat() + hOut + safeInset
                    val right = (width - paddingRight).toFloat() - hOut - safeInset
                    val clamped = min(max(event.x, left), right)
                    val fraction = (clamped - left) / (right - left)
                    val newProgress = fractionToValue(fraction).toInt().coerceIn(minProgress, maxProgress)
                    if (springAnimation.isRunning) springAnimation.cancel()
                    springAnimation.spring.stiffness = fastStiffness
                    springAnimation.spring.dampingRatio = fastDamping
                    setProgress(newProgress, fromUser = true, animate = true)
                }
                listener?.onStartTrackingTouch(this)
                performClick()
                return true
            }
            MotionEvent.ACTION_MOVE -> if (isDragging) {
                val dx = event.x - downX
                val dy = event.y - downY
                val movedFar = dx * dx + dy * dy > touchSlop * touchSlop
                if (movedFar && !longPressTriggered) cancelLongPress()
                updateFromTouch(event.x, true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    if (!longPressTriggered) listener?.onStopTrackingTouch(this)
                }
                cancelLongPress()
                if (downOnThumb) startThumbScale(up = true)
                downOnThumb = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateFromTouch(x: Float, fromUser: Boolean) {
        val hOut = horizontalOutset()
        val safeInset = thumbWidthPx / 2f // ensure full thumb fits inside view bounds
        val left = paddingLeft.toFloat() + hOut + safeInset
        val right = (width - paddingRight).toFloat() - hOut - safeInset
        if (right <= left) return
        val clamped = min(max(x, left), right)
        val fraction = (clamped - left) / (right - left)
        val newProgress = fractionToValue(fraction).toInt().coerceIn(minProgress, maxProgress)
        // User drag: immediate update without animation for responsiveness
        setProgress(newProgress, fromUser, animate = false)
    }

    fun setColors(@ColorInt track: Int = trackColor,
                  @ColorInt progress: Int = progressColor,
                  @ColorInt ring: Int = thumbRingColor,
                  @ColorInt inner: Int = thumbInnerColor) {
        trackColor = track
        progressColor = progress
        thumbRingColor = ring
        thumbInnerColor = inner
        applyPaintColors()
        invalidate()
    }

    fun setSmudge(enabled: Boolean, radius: Float = smudgeRadius, color: Int = smudgeColor, offsetY: Float = smudgeOffsetY) {
        smudgeEnabled = enabled
        smudgeRadius = radius
        smudgeColor = color
        smudgeOffsetY = offsetY
        setupSmudgeAndShadow()
        requestLayout()
        invalidate()
    }

    fun setThumbShadow(radius: Float, color: Int = thumbShadowColor) {
        thumbShadowRadius = radius
        thumbShadowColor = color
        setupSmudgeAndShadow()
        invalidate()
    }
}