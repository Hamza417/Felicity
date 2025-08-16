package app.simple.felicity.decorations.seekbars

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import app.simple.felicity.decoration.R
import app.simple.felicity.theme.managers.ThemeManager
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

    private var maxProgress = 100 // 0..maxProgress inclusive style (like standard SeekBar uses max then 0..max)
    private var progressInternal = 0 // current int progress

    @ColorInt
    private var trackColor: Int = ThemeManager.theme.viewGroupTheme.highlightColor

    @ColorInt
    private var progressColor: Int = ThemeManager.accent.primaryAccentColor

    @ColorInt
    private var thumbRingColor: Int = Color.WHITE

    @ColorInt
    private var thumbInnerColor: Int = ThemeManager.accent.primaryAccentColor

    private var trackHeightPx: Float
    private var thumbRadiusPx: Float
    private var thumbRingWidthPx: Float

    private var smudgeEnabled = false
    private var smudgeRadius = 0f
    private var smudgeColor = 0x22000000
    private var smudgeOffsetY = 0f
    private var thumbShadowRadius = 0f
    private var thumbShadowColor = 0x55000000

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val thumbRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val thumbInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val smudgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val thumbShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val trackRect = RectF()
    private val smudgeRect = RectF()
    private val progressRect = RectF()

    private var isDragging = false

    init {
        val d = resources.displayMetrics.density
        trackHeightPx = 4f * d
        thumbRadiusPx = 12f * d
        thumbRingWidthPx = 4f * d

        context.theme.obtainStyledAttributes(attrs, R.styleable.FelicitySeekbar, defStyleAttr, 0).apply {
            try {
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
                        android.util.TypedValue.TYPE_FLOAT -> getFloat(R.styleable.FelicitySeekbar_felicityProgress, 0f).toInt()
                        android.util.TypedValue.TYPE_INT_DEC, android.util.TypedValue.TYPE_INT_HEX -> getInt(R.styleable.FelicitySeekbar_felicityProgress, 0)
                        else -> getInt(R.styleable.FelicitySeekbar_felicityProgress, 0)
                    }
                }
                progressInternal = progressInternal.coerceIn(0, maxProgress)

                trackColor = getColor(R.styleable.FelicitySeekbar_felicityTrackColor, trackColor)
                progressColor = getColor(R.styleable.FelicitySeekbar_felicityProgressColor, progressColor)
                thumbRingColor = getColor(R.styleable.FelicitySeekbar_felicityThumbRingColor, thumbRingColor)
                thumbInnerColor = getColor(R.styleable.FelicitySeekbar_felicityThumbInnerColor, thumbInnerColor)
                trackHeightPx = getDimension(R.styleable.FelicitySeekbar_felicityTrackHeight, trackHeightPx)
                thumbRadiusPx = getDimension(R.styleable.FelicitySeekbar_felicityThumbRadius, thumbRadiusPx)
                thumbRingWidthPx = getDimension(R.styleable.FelicitySeekbar_felicityThumbRingWidth, thumbRingWidthPx)
                smudgeEnabled = getBoolean(R.styleable.FelicitySeekbar_felicitySmudgeEnabled, smudgeEnabled)
                smudgeRadius = getDimension(R.styleable.FelicitySeekbar_felicitySmudgeRadius, 2f * d)
                smudgeColor = getColor(R.styleable.FelicitySeekbar_felicitySmudgeColor, smudgeColor.toInt())
                smudgeOffsetY = getDimension(R.styleable.FelicitySeekbar_felicitySmudgeOffsetY, 0f)
                thumbShadowRadius = getDimension(R.styleable.FelicitySeekbar_felicityThumbShadowRadius, 6f * d)
                thumbShadowColor = getColor(R.styleable.FelicitySeekbar_felicityThumbShadowColor, thumbShadowColor.toInt())
            } finally {
                recycle()
            }
        }

        applyPaintColors()
        setupSmudgeAndShadow()

        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(android.R.string.untitled)
    }

    private fun applyPaintColors() {
        trackPaint.color = trackColor
        progressPaint.color = progressColor
        thumbRingPaint.color = thumbRingColor
        thumbRingPaint.strokeWidth = thumbRingWidthPx
        thumbInnerPaint.color = thumbInnerColor
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
            progressInternal = maxProgress
            invalidate()
        }
    }

    private fun setMaxInternal(max: Int) {
        maxProgress = if (max < 0) 0 else max
    }

    fun getMax(): Int = maxProgress

    fun setProgress(progress: Int, fromUser: Boolean = false) {
        val newValue = progress.coerceIn(0, maxProgress)
        if (newValue == progressInternal) return
        progressInternal = newValue
        invalidate()
        listener?.onProgressChanged(this, progressInternal, fromUser)
    }

    fun getProgress(): Int = progressInternal

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val baseHeight = max(trackHeightPx, thumbRadiusPx * 2f)
        val verticalBlur = max(thumbShadowRadius, if (smudgeEnabled) (smudgeRadius + kotlin.math.abs(smudgeOffsetY)) else 0f)
        val desiredHeight = (paddingTop + paddingBottom + baseHeight + verticalBlur * 2f).toInt()
        val resolvedWidth = resolveSize(suggestedMinimumWidth + paddingLeft + paddingRight, widthMeasureSpec)
        val resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }

    private fun horizontalOutset(): Float = max(thumbShadowRadius, if (smudgeEnabled) smudgeRadius else 0f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val hOut = horizontalOutset()
        val left = paddingLeft.toFloat() + hOut
        val right = (width - paddingRight).toFloat() - hOut
        if (right <= left) return
        val centerY = height / 2f + if (smudgeEnabled) smudgeOffsetY else 0f
        val trackRadius = trackHeightPx / 2f
        trackRect.set(left, centerY - trackRadius, right, centerY + trackRadius)

        val fraction = if (maxProgress == 0) 0f else progressInternal.toFloat() / maxProgress.toFloat()
        val progressRight = left + (right - left) * fraction.coerceIn(0f, 1f)

        if (smudgeEnabled && progressRight > left) {
            smudgeRect.set(left, trackRect.top, progressRight, trackRect.bottom)
            canvas.drawRoundRect(smudgeRect, trackRadius, trackRadius, smudgePaint)
        }
        canvas.drawRoundRect(trackRect, trackRadius, trackRadius, trackPaint)
        if (progressRight > left) {
            progressRect.set(left, trackRect.top, progressRight, trackRect.bottom)
            canvas.drawRoundRect(progressRect, trackRadius, trackRadius, progressPaint)
        }

        if (progressRight > left) {
            val cx = progressRight
            val cy = trackRect.centerY()
            if (thumbShadowRadius > 0f) canvas.drawCircle(cx, cy, thumbRadiusPx, thumbShadowPaint)
            if (thumbInnerColor != Color.TRANSPARENT) canvas.drawCircle(cx, cy, (thumbRadiusPx - thumbRingWidthPx / 2f).coerceAtLeast(0f), thumbInnerPaint)
            canvas.drawCircle(cx, cy, thumbRadiusPx - thumbRingWidthPx / 2f, thumbRingPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isEnabled) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                isDragging = true
                listener?.onStartTrackingTouch(this)
                updateFromTouch(event.x, true)
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
        val left = paddingLeft.toFloat() + hOut
        val right = (width - paddingRight).toFloat() - hOut
        if (right <= left) return
        val clamped = min(max(x, left), right)
        val fraction = (clamped - left) / (right - left)
        val newProgress = (fraction * maxProgress).toInt().coerceIn(0, maxProgress)
        setProgress(newProgress, fromUser)
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