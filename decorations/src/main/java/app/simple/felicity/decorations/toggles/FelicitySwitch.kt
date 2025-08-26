package app.simple.felicity.decorations.toggles

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Checkable
import androidx.annotation.ColorInt
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import app.simple.felicity.decoration.R
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FelicitySwitch @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr),
    Checkable,
    SharedPreferences.OnSharedPreferenceChangeListener,
    ThemeChangedListener {

    companion object {
        private val CHECKED_STATE_SET = intArrayOf(android.R.attr.state_checked)
    }

    // Paints and geometry
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val trackShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val trackRect = RectF()

    // Colors (configurable)
    @ColorInt
    private var trackOnColor: Int = if (isInEditMode) {
        0xFF4CAF50.toInt()
    } else {
        ThemeManager.accent.primaryAccentColor
    }

    @ColorInt
    private var trackOffColor: Int = if (isInEditMode) {
        0xFF9E9E9E.toInt()
    } else {
        ThemeManager.theme.viewGroupTheme.highlightColor
    }

    @ColorInt
    private var thumbRingColor: Int = 0xFFFFFFFF.toInt()

    // Animated/current track color
    @ColorInt
    private var currentTrackColor: Int = 0

    // Dimensions
    private var ringPaddingPx: Float = dp(4f)
    private var ringStrokeWidthPx: Float = dp(7f)

    // Interpret as scale-up factor (>= 1f)
    private var pressScaleMin: Float = 0.7f

    // Single control for background shadow radius when checked
    private var shadowRadiusPx: Float = 28f

    // Manual shadow color/offset for TRACK
    @ColorInt
    private var shadowColor: Int = if (isInEditMode) {
        0x55000000
    } else {
        ThemeManager.accent.primaryAccentColor
    }

    private var shadowOffsetX: Float = 0f
    private var shadowOffsetY: Float = dp(1f)

    // State
    private var checked: Boolean = false
    private var thumbPos: Float = 0f // 0..1 (logical LTR)
    private var pressScale: Float = 1f

    // Animators
    private var thumbAnimator: ValueAnimator? = null
    private var scaleAnimator: ValueAnimator? = null
    private var colorAnimator: ValueAnimator? = null
    private val thumbInterpolator = OvershootInterpolator(1.5F)
    private val scaleInterpolator = DecelerateInterpolator(1.5f)
    private val colorInterpolator = FastOutSlowInInterpolator()
    private var thumbAnimDuration = 820L
    private var pressAnimDuration = 400L
    private var colorAnimDuration = 600L

    private val SHADOW_SCALE_RGB = 0.85f
    private val SHADOW_SCALE_ALPHA = 0.4f

    // Touch/drag
    private var downX: Float = 0f
    private var dragging = false
    private val touchSlopPx: Float = dp(3f)

    // Listeners
    private var onCheckedChange: ((FelicitySwitch, Boolean) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        clipToOutline = false

        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.FelicitySwitch)
            try {
                if (a.hasValue(R.styleable.FelicitySwitch_felicitySwitchTrackOnColor)) {
                    trackOnColor = a.getColor(R.styleable.FelicitySwitch_felicitySwitchTrackOnColor, trackOnColor)
                }
                if (a.hasValue(R.styleable.FelicitySwitch_felicitySwitchTrackOffColor)) {
                    trackOffColor = a.getColor(R.styleable.FelicitySwitch_felicitySwitchTrackOffColor, trackOffColor)
                }
                if (a.hasValue(R.styleable.FelicitySwitch_felicitySwitchThumbRingColor)) {
                    thumbRingColor = a.getColor(R.styleable.FelicitySwitch_felicitySwitchThumbRingColor, thumbRingColor)
                }
                ringPaddingPx = a.getDimension(R.styleable.FelicitySwitch_felicitySwitchPadding, ringPaddingPx)
                ringStrokeWidthPx = a.getDimension(R.styleable.FelicitySwitch_felicitySwitchStrokeWidth, ringStrokeWidthPx)
                pressScaleMin = a.getFloat(R.styleable.FelicitySwitch_felicitySwitchPressScale, pressScaleMin).coerceAtLeast(1f)
                checked = a.getBoolean(R.styleable.FelicitySwitch_felicitySwitchChecked, false)
                // Reuse the same attr for shadow radius when checked
                shadowRadiusPx = a.getDimension(R.styleable.FelicitySwitch_felicitySwitchCheckedElevation, shadowRadiusPx)
            } finally {
                a.recycle()
            }
        }

        ringPaint.strokeWidth = ringStrokeWidthPx
        // initialize current track color according to state
        currentTrackColor = if (checked) trackOnColor else trackOffColor
        trackPaint.color = currentTrackColor
        thumbPos = if (checked) 1f else 0f
        contentDescription = if (checked) "On" else "Off"
        updateElevation()

        post {
            disableAncestorClipping()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        disableAncestorClipping()
    }

    private fun disableAncestorClipping() {
        var p = parent
        while (p is ViewGroup) {
            try {
                p.clipChildren = false
                p.clipToPadding = false
                p.clipToOutline = false
            } catch (_: Throwable) {
                // ignore
            }
            p = p.parent
        }
    }

    // Measurement: default size similar to a standard switch
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = resources.getDimensionPixelSize(R.dimen.switch_width)
        val desiredHeight = resources.getDimensionPixelSize(R.dimen.switch_height)

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val measuredWidth = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> min(desiredWidth, widthSize)
            else -> desiredWidth
        }

        val measuredHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> min(desiredHeight, heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        trackRect.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        // Draw track shadow first (only when checked and radius > 0)
        if (checked && shadowRadiusPx > 0f) {
            val rTrack = height / 2f
            canvas.drawRoundRect(trackRect, rTrack, rTrack, trackShadowPaint)
        }

        // Draw track with animated/current color (no shadow on this paint)
        trackPaint.color = currentTrackColor
        val rTrack = height / 2f
        canvas.drawRoundRect(trackRect, rTrack, rTrack, trackPaint)

        // Compute ring geometry within padding
        val s = ringPaint.strokeWidth
        val availableHeight = height - 2f * ringPaddingPx
        val baseRadius = max(0f, availableHeight / 2f - s / 2f)
        val maxRadiusAllowed = max(0f, height / 2f - s / 2f)
        val scaledRadius = min(baseRadius * pressScale, maxRadiusAllowed)
        val minCenter = ringPaddingPx + scaledRadius + s / 2f
        val maxCenter = width - (ringPaddingPx + scaledRadius + s / 2f)
        val effectivePosRaw = if (layoutDirection == LAYOUT_DIRECTION_RTL) 1f - thumbPos else thumbPos
        val effectivePos = reflectWithinUnit(effectivePosRaw)
        val cx = lerp(minCenter, maxCenter, effectivePos)
        val cy = height / 2f

        // Draw ring thumb (no shadow here)
        ringPaint.color = thumbRingColor
        canvas.drawCircle(cx, cy, scaledRadius, ringPaint)

        super.onDraw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isPressed = true
                downX = event.x
                dragging = false
                animatePress(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                if (!dragging && abs(dx) > touchSlopPx) dragging = true
                if (dragging) {
                    val s = ringPaint.strokeWidth
                    val baseRadius = max(0f, (height - 2f * ringPaddingPx) / 2f - s / 2f)
                    val maxRadiusAllowed = max(0f, height / 2f - s / 2f)
                    val scaledRadius = min(baseRadius * pressScale, maxRadiusAllowed)
                    val widthUsable = width - 2f * (ringPaddingPx + scaledRadius + s / 2f)
                    if (widthUsable > 0f) {
                        val dirAdjustedDx = if (layoutDirection == LAYOUT_DIRECTION_RTL) -dx else dx
                        val delta = dirAdjustedDx / widthUsable
                        thumbPos = clamp01((if (checked) 1f else 0f) + delta)
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isPressed = false
                val wasDragging = dragging
                dragging = false
                animatePress(false)

                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val effectivePos = if (layoutDirection == LAYOUT_DIRECTION_RTL) 1f - thumbPos else thumbPos
                    val newChecked = if (wasDragging) effectivePos >= 0.5f else !checked
                    setCheckedInternal(newChecked, animateThumb = true)
                    performClick()
                } else {
                    // Cancel -> animate back to current state
                    animateThumbTo(if (checked) 1f else 0f)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        // Accessibility event is sent by super
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)
        return super.performClick()
    }

    // Support drawable state for "checked"
    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val drawableState = super.onCreateDrawableState(extraSpace + 1)
        if (isChecked) mergeDrawableStates(drawableState, CHECKED_STATE_SET)
        return drawableState
    }

    // Checkable implementation
    override fun isChecked(): Boolean = checked

    override fun toggle() {
        isChecked = !checked
    }

    override fun setChecked(checked: Boolean) {
        setCheckedInternal(checked, animateThumb = true)
    }

    private fun setCheckedInternal(newChecked: Boolean, animateThumb: Boolean) {
        if (checked == newChecked) {
            if (animateThumb) animateThumbTo(if (checked) 1f else 0f)
            return
        }
        checked = newChecked
        refreshDrawableState()
        contentDescription = if (checked) "On" else "Off"
        updateElevation()
        // animate track color towards new state color
        animateTrackColorTo(if (checked) trackOnColor else trackOffColor)
        if (animateThumb) animateThumbTo(if (checked) 1f else 0f) else run { thumbPos = if (checked) 1f else 0f; invalidate() }
        onCheckedChange?.invoke(this, checked)
        invalidate()
    }

    private fun animateTrackColorTo(targetColor: Int) {
        val start = currentTrackColor
        if (start == targetColor) return
        colorAnimator?.cancel()
        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), start, targetColor).apply {
            duration = colorAnimDuration
            interpolator = colorInterpolator
            addUpdateListener { anim ->
                currentTrackColor = (anim.animatedValue as Int)
                invalidate()
            }
            start()
        }
    }

    private fun updateElevation() {
        // Apply manual shadow to TRACK using a dedicated shadow paint
        val matrix = ColorMatrix()
        matrix.setScale(SHADOW_SCALE_RGB, SHADOW_SCALE_RGB, SHADOW_SCALE_RGB, SHADOW_SCALE_ALPHA)
        trackShadowPaint.colorFilter = ColorMatrixColorFilter(matrix)

        if (checked && shadowRadiusPx > 0f) {
            trackShadowPaint.setShadowLayer(shadowRadiusPx, shadowOffsetX, shadowOffsetY, shadowColor)
        } else {
            trackShadowPaint.clearShadowLayer()
        }
        clipToOutline = false
        invalidate()
    }

    private fun animateThumbTo(target: Float) {
        val end = clamp01(target)
        val start = thumbPos
        val dir = if (end >= start) 1f else -1f
        val overshootDelta = 0.18f * dir // 8% of travel
        val overshoot = end + overshootDelta

        thumbAnimator?.cancel()
        thumbAnimator = ValueAnimator.ofFloat(start, overshoot, end).apply {
            duration = thumbAnimDuration
            interpolator = thumbInterpolator
            addUpdateListener { anim ->
                thumbPos = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun animatePress(pressed: Boolean) {
        val target = if (pressed) max(1f, pressScaleMin) else 1f
        scaleAnimator?.cancel()
        scaleAnimator = ValueAnimator.ofFloat(pressScale, target).apply {
            duration = pressAnimDuration
            interpolator = scaleInterpolator
            addUpdateListener { anim ->
                pressScale = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // Public API: colors and dimensions
    fun setOnCheckedChangeListener(listener: ((FelicitySwitch, Boolean) -> Unit)?) {
        onCheckedChange = listener
    }

    fun setSwitchColors(@ColorInt trackOn: Int? = null, @ColorInt trackOff: Int? = null, @ColorInt ring: Int? = null) {
        trackOn?.let { trackOnColor = it }
        trackOff?.let { trackOffColor = it }
        ring?.let { thumbRingColor = it }
        // snap current track color to state after external update
        currentTrackColor = if (checked) trackOnColor else trackOffColor
        invalidate()
    }

    fun setTrackOnColor(@ColorInt color: Int) {
        trackOnColor = color; invalidate()
    }

    fun setTrackOffColor(@ColorInt color: Int) {
        trackOffColor = color; invalidate()
    }

    fun setThumbRingColor(@ColorInt color: Int) {
        thumbRingColor = color; invalidate()
    }

    fun setRingPadding(paddingPx: Float) {
        ringPaddingPx = max(0f, paddingPx); invalidate()
    }

    fun setRingStrokeWidth(strokeWidthPx: Float) {
        ringStrokeWidthPx = max(0f, strokeWidthPx); ringPaint.strokeWidth = ringStrokeWidthPx; invalidate()
    }

    fun setPressScaleMin(scale: Float) {
        pressScaleMin = max(1f, scale)
    }

    // Optionally expose shadow radius
    fun setShadowRadiusPx(radius: Float) {
        shadowRadiusPx = max(0f, radius)
        updateElevation()
    }

    // State save/restore
    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        return SavedState(superState).also { state ->
            state.checked = this.checked
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            isChecked = state.checked
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    private class SavedState : BaseSavedState {
        var checked: Boolean = false

        constructor(superState: Parcelable?) : super(superState)
        private constructor(parcel: Parcel) : super(parcel) {
            checked = parcel.readInt() == 1
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(if (checked) 1 else 0)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }

    // Utilities
    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    private fun clamp01(v: Float): Float = min(1f, max(0f, v))

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {

    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        setTrackOffColor(theme.viewGroupTheme.highlightColor)
        invalidate()
    }

    override fun onAccentChanged(accent: Accent) {
        setTrackOnColor(accent.primaryAccentColor)
        invalidate()
    }

    private fun reflectWithinUnit(p: Float): Float {
        // Maps any value to [0,1] by reflecting the overshoot back inside the range
        return when {
            p < 0f -> (-p).coerceAtMost(1f)
            p > 1f -> (2f - p).coerceIn(0f, 1f)
            else -> p
        }
    }
}