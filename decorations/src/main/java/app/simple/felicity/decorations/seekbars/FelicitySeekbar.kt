package app.simple.felicity.decorations.seekbars

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import app.simple.felicity.decoration.R
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.preferences.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.preferences.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.theme.managers.ThemeManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FelicitySeekbar @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), SharedPreferences.OnSharedPreferenceChangeListener {

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
    private var defaultProgress = 0 // value to reset to on double tap
    private var hasDefaultSet = false // only reset if explicitly configured

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
    private var thumbRingColor: Int = if (isInEditMode) {
        Color.WHITE
    } else {
        ThemeManager.theme.viewGroupTheme.backgroundColor
    }

    @ColorInt
    private var thumbInnerColor: Int = if (isInEditMode) {
        Color.TRANSPARENT
    } else {
        thumbRingColor
    }

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

    // Press ring paint (MD2-style halo around thumb on press)
    private val thumbPressRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val trackRect = RectF()
    private val smudgeRect = RectF()
    private val progressRect = RectF()

    // Reusable temp rects for thumb drawing to avoid allocations
    private val thumbOuterRect = RectF()
    private val thumbStrokeRect = RectF()
    private val thumbInnerRect = RectF()

    // Extra rect for press ring
    private val thumbPressRingRect = RectF()

    private var isDragging = false
    private var thumbScale = 1f
    private var thumbScaleAnimator: ValueAnimator? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var downOnThumb = false

    // Press ring animation
    private var pressRingProgress = 0f // 0..1
    private var pressRingAnimator: ValueAnimator? = null
    private var pressRingOutsetPx: Float
    private var pressRingStrokePx: Float

    @ColorInt
    private var pressRingColor: Int

    // Helper: total outward outset for press ring including half the stroke
    private fun pressRingTotalOutset(): Float = pressRingOutsetPx + (pressRingStrokePx / 2f)

    // Gesture detection for double-tap to reset
    private var consumedDoubleTap = false
    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (hasDefaultSet) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                resetToDefault(animate = true)
                consumedDoubleTap = true
                startPressRing(false)
                return true
            }
            return false
        }
    }
    private val gestureDetector = GestureDetector(context, gestureListener).apply {
        setOnDoubleTapListener(gestureListener)
    }

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
            stiffness = SpringForce.STIFFNESS_VERY_LOW
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
        }
        addEndListener { _, _, _, _ ->
            listener?.onProgressChanged(this@FelicitySeekbar, getProgress(), animateFromUser)
            // restore base spring if it was altered for fast snap
            spring?.stiffness = baseStiffness
            spring?.dampingRatio = baseDamping
        }
    }
    private val baseStiffness = SpringForce.STIFFNESS_LOW
    private val baseDamping = SpringForce.DAMPING_RATIO_NO_BOUNCY

    private val fastStiffness = SpringForce.STIFFNESS_HIGH
    private val fastDamping = SpringForce.DAMPING_RATIO_NO_BOUNCY

    init {
        val d = resources.displayMetrics.density
        trackHeightPx = 4f * d
        thumbRadiusPx = 12f * d
        thumbRingWidthPx = 4f * d
        // Default pill width: 3x radius (i.e., 1.5x diameter)
        thumbWidthPx = thumbRadiusPx * 3f
        // MD2 press ring defaults
        pressRingOutsetPx = 6f * d
        pressRingStrokePx = 2f * d
        pressRingColor = progressColor

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
                        TypedValue.TYPE_FLOAT -> getFloat(R.styleable.FelicitySeekbar_felicityProgress, minProgress.toFloat())
                        TypedValue.TYPE_INT_DEC, TypedValue.TYPE_INT_HEX -> getInt(R.styleable.FelicitySeekbar_felicityProgress, minProgress).toFloat()
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
        applyThemeProps()

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
        // Press ring picks accent color with dynamic alpha
        thumbPressRingPaint.color = progressColor
        thumbPressRingPaint.strokeWidth = pressRingStrokePx
    }

    private fun applyThemeProps() {
        if (isInEditMode.not()) {
            progressColor = ThemeManager.accent.primaryAccentColor
            trackColor = ThemeManager.theme.viewGroupTheme.highlightColor
            thumbRingColor = Color.WHITE
            thumbInnerColor = progressColor
            smudgeColor = progressColor
            thumbShadowColor = progressColor
            pressRingColor = progressColor
            setThumbCornerRadius(AppearancePreferences.getCornerRadius())
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
        hasDefaultSet = true
        defaultProgress = value.coerceIn(minProgress, maxProgress)
    }

    fun resetToDefault(animate: Boolean = true) {
        setProgress(defaultProgress, fromUser = true, animate = animate)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val baseHeight = max(trackHeightPx, thumbRadiusPx * 2f) + (pressRingTotalOutset() * 2f)
        val verticalBlur = max(thumbShadowRadius, if (smudgeEnabled) (smudgeRadius + abs(smudgeOffsetY)) else 0f)
        val desiredHeight = (paddingTop + paddingBottom + baseHeight + verticalBlur * 2f).toInt()
        val resolvedWidth = resolveSize(suggestedMinimumWidth + paddingLeft + paddingRight, widthMeasureSpec)
        val resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }

    private fun horizontalOutset(): Float = max(max(thumbShadowRadius, if (smudgeEnabled) smudgeRadius else 0f), pressRingTotalOutset())

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

        val baseThumbCornerR = min(thumbCornerRadiusPxOverride ?: scaledR, min(scaledR, scaledHalfW))

        if (thumbShadowRadius > 0f) {
            canvas.drawRoundRect(thumbOuterRect, baseThumbCornerR, baseThumbCornerR, thumbShadowPaint)
        }

        // Inner fill (beneath the ring)
        if (thumbInnerColor != Color.TRANSPARENT) {
            thumbInnerRect.set(thumbOuterRect)
            val inset = thumbRingWidthPx / 2f
            thumbInnerRect.inset(inset, inset)
            val innerR = max(0f, baseThumbCornerR - inset)
            canvas.drawRoundRect(thumbInnerRect, innerR, innerR, thumbInnerPaint)
        }

        // Stroke ring (always drawn around thumb to define shape)
        thumbStrokeRect.set(thumbOuterRect)
        val strokeInset = thumbRingWidthPx / 2f
        thumbStrokeRect.inset(strokeInset, strokeInset)
        val baseRingCornerR = baseThumbCornerR // ring follows thumb shape strictly
        val ringR = max(0f, baseRingCornerR - strokeInset)
        canvas.drawRoundRect(thumbStrokeRect, ringR, ringR, thumbRingPaint)

        // MD2-style press ring (appears/animates on touch) — draw last so it's above progress/track
        if (pressRingProgress > 0f) {
            val extra = pressRingOutsetPx * pressRingProgress
            thumbPressRingRect.set(thumbOuterRect)
            // Expand outwards by extra to keep same shape proportion
            thumbPressRingRect.inset(-extra, -extra)
            // Corner radius scales with expansion, plus a tiny offset to keep corners uniformly round
            val pressRingCornerR = baseRingCornerR + extra // + (pressRingStrokePx / 2f)
            val alpha = (0.35f * pressRingProgress * 255).toInt().coerceIn(0, 255)
            thumbPressRingPaint.color = (pressRingColor and 0x00FFFFFF) or (alpha shl 24)
            thumbPressRingPaint.strokeWidth = pressRingStrokePx
            canvas.drawRoundRect(thumbPressRingRect, pressRingCornerR, pressRingCornerR, thumbPressRingPaint)
        }
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
        val halfH = thumbRadiusPx * thumbScale
        val halfW = (thumbWidthPx / 2f) * thumbScale
        val cornerR = min(thumbCornerRadiusPxOverride ?: halfH, min(halfH, halfW))
        val bodyHalfW = max(0f, halfW - cornerR)
        // Inside central rectangle of rounded-rect
        if (abs(dx) <= bodyHalfW && abs(dy) <= halfH) return true
        // Check circular caps centered at +/- bodyHalfW
        val leftCx = -bodyHalfW
        val rightCx = bodyHalfW
        val dlx = dx - leftCx
        val drx = dx - rightCx
        return (dlx * dlx + dy * dy <= cornerR * cornerR) || (drx * drx + dy * dy <= cornerR * cornerR)
    }

    // Deprecated: no-op to keep binary/source compatibility; press ring provides feedback instead
    private fun startThumbScale(up: Boolean = false) {
        thumbScaleAnimator?.cancel()
        thumbScale = 1f
        invalidate()
    }

    private fun startPressRing(show: Boolean) {
        val target = if (show) 1f else 0f
        if (pressRingProgress == target) return
        pressRingAnimator?.cancel()
        pressRingAnimator = ValueAnimator.ofFloat(pressRingProgress, target).apply {
            duration = if (show) 160 else 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                pressRingProgress = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // First pass events to gesture detector to catch double-tap
        consumedDoubleTap = false
        gestureDetector.onTouchEvent(event)
        if (consumedDoubleTap) {
            // Avoid triggering drag/tap behaviors for this gesture
            isDragging = false
            thumbScaleAnimator?.cancel()
            thumbScale = 1f
            startPressRing(false)
            invalidate()
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isEnabled) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                isDragging = true
                downX = event.x
                downY = event.y
                downOnThumb = isPointOnThumb(downX, downY)
                if (downOnThumb) {
                    startPressRing(true)
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
                updateFromTouch(event.x, true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    listener?.onStopTrackingTouch(this)
                }
                if (downOnThumb) startPressRing(false)
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
        pressRingColor = progress
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

    // Optional overrides for corner radii (rx=ry) of thumb fill only (ring follows thumb)
    private var thumbCornerRadiusPxOverride: Float? = null

    // Public API: set/get corner radius for thumb fill (rx==ry)
    fun setThumbCornerRadius(radius: Float) {
        thumbCornerRadiusPxOverride = max(0f, radius)
        invalidate()
    }

    fun getThumbCornerRadius(): Float? = thumbCornerRadiusPxOverride

    // Convenience: clear thumb override (reverts to pill radius)
    fun clearThumbCornerRadiusOverride() {
        thumbCornerRadiusPxOverride = null
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerSharedPreferenceChangeListener()
    }

    override fun onSharedPreferenceChanged(p0: SharedPreferences?, p1: String?) {
        if (p1 == AppearancePreferences.APP_CORNER_RADIUS) {
            setThumbCornerRadius(AppearancePreferences.getCornerRadius())
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unregisterSharedPreferenceChangeListener()
    }
}