package app.simple.felicity.decorations.views

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import androidx.core.graphics.withTranslation
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import app.simple.felicity.decorations.typeface.TypeFace
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.models.Theme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

class RollingTimePicker @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), ThemeChangedListener {

    // Index 0: Hours, 1: Minutes, 2: Seconds
    private val minValues = intArrayOf(0, 0, 0)
    private val maxValues = intArrayOf(23, 59, 59)

    private val scrollYOffsets = floatArrayOf(0f, 0f, 0f)
    private val scrollers = Array(3) { OverScroller(context) }
    private val isFlinging = booleanArrayOf(false, false, false)

    fun setTime(h: Int, m: Int, s: Int, animate: Boolean = true) {
        setColumnValue(0, h, animate)
        setColumnValue(1, m, animate)
        setColumnValue(2, s, animate)
    }

    private fun setColumnValue(column: Int, value: Int, animate: Boolean) {
        val targetY = value.coerceIn(minValues[column], maxValues[column]) * itemHeight
        if (animate) {
            val currentY = scrollYOffsets[column].toInt()
            val dy = (targetY - currentY).toInt()
            if (dy != 0) {
                scrollers[column].startScroll(0, currentY, 0, dy, 500)
                postInvalidateOnAnimation()
            }
        } else {
            scrollYOffsets[column] = targetY
            if (!scrollers[column].isFinished) scrollers[column].forceFinished(true)
            invalidate()
        }
    }

    var hours: Int
        get() = (scrollYOffsets[0] / itemHeight).roundToInt().coerceIn(minValues[0], maxValues[0])
        set(v) {
            setColumnValue(0, v, false)
        }

    var minutes: Int
        get() = (scrollYOffsets[1] / itemHeight).roundToInt().coerceIn(minValues[1], maxValues[1])
        set(v) {
            setColumnValue(1, v, false)
        }

    var seconds: Int
        get() = (scrollYOffsets[2] / itemHeight).roundToInt().coerceIn(minValues[2], maxValues[2])
        set(v) {
            setColumnValue(2, v, false)
        }

    private var textColor: Int = 0xFF000000.toInt()
    private var colorAnimator: ValueAnimator? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = TypeFace.getLightTypeFace(context)
    }

    private val colonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = if (isInEditMode) {
            Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        } else {
            TypeFace.getLightTypeFace(context)
        }
    }

    private var itemHeight = 150f
    private val visibleItems = 3
    private var activeColumn = -1

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (activeColumn == -1) return false
            val minOffset = minValues[activeColumn] * itemHeight
            val maxOffset = maxValues[activeColumn] * itemHeight

            if (scrollYOffsets[activeColumn] !in minOffset..maxOffset) {
                scrollYOffsets[activeColumn] += distanceY * 0.3f // Resistance when overscrolling
            } else {
                scrollYOffsets[activeColumn] += distanceY
            }
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (activeColumn == -1) return false
            isFlinging[activeColumn] = true
            val minOffset = (minValues[activeColumn] * itemHeight).toInt()
            val maxOffset = (maxValues[activeColumn] * itemHeight).toInt()
            val overscroll = (itemHeight * 1.5f).toInt()

            scrollers[activeColumn].fling(
                    0, scrollYOffsets[activeColumn].toInt(),
                    0, -velocityY.toInt(),
                    0, 0,
                    minOffset, maxOffset,
                    0, overscroll
            )
            postInvalidateOnAnimation()
            return true
        }

        override fun onDown(e: MotionEvent): Boolean {
            if (activeColumn != -1 && !scrollers[activeColumn].isFinished) {
                scrollers[activeColumn].forceFinished(true)
                isFlinging[activeColumn] = false
            }
            return true
        }
    })

    init {
        val density = resources.displayMetrics.density
        itemHeight = 60f * density
        setFadingEdgeLength((itemHeight * 3.0f).toInt())
        isVerticalFadingEdgeEnabled = true

        if (!isInEditMode) {
            updateTextColor(ThemeManager.theme.textViewTheme.primaryTextColor, false)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isInEditMode) return
        ThemeManager.addListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ThemeManager.removeListener(this)
        colorAnimator?.cancel()
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        updateTextColor(theme.textViewTheme.primaryTextColor, animate)
    }

    override fun onAccentChanged(accent: Accent) {
        // No accent color used here currently
    }

    private fun updateTextColor(endColor: Int, animate: Boolean) {
        if (animate) {
            colorAnimator?.cancel()
            colorAnimator = ValueAnimator.ofArgb(textColor, endColor).apply {
                duration = resources.getInteger(app.simple.felicity.decoration.R.integer.animation_duration).toLong()
                interpolator = LinearOutSlowInInterpolator()
                addUpdateListener { animation ->
                    textColor = animation.animatedValue as Int
                    paint.color = textColor
                    colonPaint.color = textColor
                    invalidate()
                }
                start()
            }
        } else {
            textColor = endColor
            paint.color = textColor
            colonPaint.color = textColor
            invalidate()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)

            // Determine active column based on touch X
            val colWidth = width / 3f
            activeColumn = when {
                event.x < colWidth -> 0
                event.x > 2 * colWidth -> 2
                else -> 1
            }

            if (!scrollers[activeColumn].isFinished) {
                scrollers[activeColumn].forceFinished(true)
                isFlinging[activeColumn] = false
            }
        }

        var handled = gestureDetector.onTouchEvent(event)

        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            if (activeColumn != -1) {
                if (scrollers[activeColumn].isFinished) {
                    snapToNearest(activeColumn)
                }
                activeColumn = -1
            }
            handled = true
        }
        return handled || super.onTouchEvent(event)
    }

    override fun computeScroll() {
        var needsInvalidate = false
        for (i in 0..2) {
            if (scrollers[i].computeScrollOffset()) {
                scrollYOffsets[i] = scrollers[i].currY.toFloat()
                needsInvalidate = true
            } else if (isFlinging[i]) {
                isFlinging[i] = false
                snapToNearest(i)
                needsInvalidate = true
            }
        }
        if (needsInvalidate) {
            postInvalidateOnAnimation()
        }
    }

    private fun snapToNearest(column: Int) {
        val minOffset = (minValues[column] * itemHeight).toInt()
        val maxOffset = (maxValues[column] * itemHeight).toInt()

        if (scrollYOffsets[column] < minOffset || scrollYOffsets[column] > maxOffset) {
            if (scrollers[column].springBack(0, scrollYOffsets[column].toInt(), 0, 0, minOffset, maxOffset)) {
                postInvalidateOnAnimation()
                return
            }
        }

        val targetIndex = (scrollYOffsets[column] / itemHeight).roundToInt().coerceIn(minValues[column], maxValues[column])
        val targetY = targetIndex * itemHeight
        if (scrollYOffsets[column] != targetY) {
            scrollers[column].startScroll(0, scrollYOffsets[column].toInt(), 0, (targetY - scrollYOffsets[column]).toInt(), 300)
            postInvalidateOnAnimation()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val desiredWidth = (240 * density).toInt()
        val desiredHeight = (itemHeight * visibleItems).toInt()

        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    private val blurCache = mutableMapOf<Int, BlurMaskFilter>()

    private fun getBlurFilter(radius: Int): BlurMaskFilter {
        return blurCache.getOrPut(radius) {
            BlurMaskFilter(radius.toFloat(), BlurMaskFilter.Blur.NORMAL)
        }
    }

    private val fadePaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    private var topFadeShader: Shader? = null
    private var bottomFadeShader: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val fadeHeight = itemHeight * 1.5f
        if (h > 0) {
            topFadeShader = LinearGradient(0f, 0f, 0f, fadeHeight, 0xFF000000.toInt(), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            bottomFadeShader = LinearGradient(0f, h.toFloat(), 0f, h - fadeHeight, 0xFF000000.toInt(), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        super.onDraw(canvas)
        val centerY = height / 2f
        val radius = height / 2f
        val density = resources.displayMetrics.density
        val colWidth = width / 3f

        for (col in 0..2) {
            val centerX = colWidth * col + colWidth / 2f

            val startIndex = max(minValues[col], (scrollYOffsets[col] / itemHeight).toInt() - visibleItems)
            val endIndex = min(maxValues[col], (scrollYOffsets[col] / itemHeight).toInt() + visibleItems)

            for (i in startIndex..endIndex) {
                if (i < minValues[col] || i > maxValues[col]) continue

                val itemCenterY = centerY + (i * itemHeight) - scrollYOffsets[col]

                // Normalize distance from center: -1.0 to 1.0 (at the edges of radius)
                val angle = (itemCenterY - centerY) / radius

                if (abs(angle) > Math.PI / 2) continue // Outside visible half of cylinder

                // Cylinder projection
                val projectedY = centerY + radius * sin(angle.toDouble()).toFloat()
                val scale = cos(angle.toDouble()).toFloat()

                // Adjust alpha and text size based on scale with steeper fade
                val edgeFade = (1f - (abs(angle) / (Math.PI / 2))).pow(2.0).toFloat()
                val alphaVal = (edgeFade * scale * 255).toInt().coerceIn(0, 255)

                // Distance in pixels from center
                val distance = abs(itemCenterY - centerY)

                // Blur span at non focused fonts
                val blurRadius = (distance / itemHeight) * 3f * density
                val blurInt = blurRadius.toInt()
                if (blurInt > 0) {
                    paint.maskFilter = getBlurFilter(blurInt)
                } else {
                    paint.maskFilter = null
                }

                paint.textSize = 60f * density * scale
                paint.alpha = alphaVal
                paint.color = textColor

                val text = String.format(Locale.getDefault(), "%02d", i)

                val fontMetrics = paint.fontMetrics

                canvas.withTranslation(centerX, projectedY) {

                    // Tilt/Rotate the text on the X-axis for a 3D cylindrical perspective
                    // We use scaleY to achieve the visual squishing associated with X-axis rotation
                    // Visual compression (simulating rotation around X-axis)
                    scale(1f, scale)

                    drawText(text, 0f, -(fontMetrics.ascent + fontMetrics.descent) / 2, paint)

                }
            }
        }

        // Draw static colons between columns
        colonPaint.textSize = 60f * density
        colonPaint.color = textColor
        val fontMetrics = colonPaint.fontMetrics
        val colonY = centerY - (fontMetrics.ascent + fontMetrics.descent) / 2

        canvas.drawText(":", colWidth, colonY - (8f * density), colonPaint)
        canvas.drawText(":", colWidth * 2, colonY - (8f * density), colonPaint)

        // Draw vertical fading edges manually
        val fadeHeight = itemHeight * 1.5f

        // Top fade
        if (topFadeShader != null) {
            fadePaint.shader = topFadeShader
            canvas.drawRect(0f, 0f, width.toFloat(), fadeHeight, fadePaint)
        }

        // Bottom fade
        if (bottomFadeShader != null) {
            fadePaint.shader = bottomFadeShader
            canvas.drawRect(0f, height - fadeHeight, width.toFloat(), height.toFloat(), fadePaint)
        }

        canvas.restoreToCount(saveCount)
    }
}
