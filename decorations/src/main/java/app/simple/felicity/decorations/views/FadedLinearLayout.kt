package app.simple.felicity.decorations.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import androidx.core.graphics.toColorInt
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

class FadedLinearLayout @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var _isExpanded = false
    var isExpanded: Boolean
        get() = _isExpanded
        set(value) {
            setExpanded(value, true)
        }

    fun setExpanded(expanded: Boolean, animate: Boolean) {
        if (_isExpanded != expanded) {
            _isExpanded = expanded
            animator?.cancel()
            if (animate) {
                animateExpansion(expanded)
            } else {
                expansionProgress = if (expanded) 1f else 0f
                invalidate()
            }
        }
    }

    private val density = context.resources.displayMetrics.density
    private var expansionProgress = 0f
    private var fadeHeight = 90f * density
    private var animator: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotRadius = 2f * density
    private val dotSpacing = 16f * density
    private val dotBottomMargin = 12f * density

    private var gradient: LinearGradient? = null
    private var lastHeight = 0

    init {
        // Resolve theme color for dots (using text color primary or secondary, let's use secondary here, or grey)
        dotPaint.color = "#888888".toColorInt()
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    private fun animateExpansion(expanded: Boolean) {
        val target = if (expanded) 1f else 0f
        animator = ValueAnimator.ofFloat(expansionProgress, target).apply {
            duration = 300
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                expansionProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun createGradient() {
        val stops = 20
        val colors = IntArray(stops)
        val positions = FloatArray(stops)
        val interpolator = PathInterpolator(0.42f, 0.0f, 1.0f, 1.0f) // Ease-in curve

        for (i in 0 until stops) {
            val t = i / (stops - 1).toFloat()
            positions[i] = t
            // Alpha from 0 (top of fade) to 255 (bottom of fade)
            val alpha = (interpolator.getInterpolation(t) * 255).toInt()
            colors[i] = Color.argb(alpha, 0, 0, 0)
        }

        gradient = LinearGradient(
                0f, height - fadeHeight, 0f, height.toFloat(),
                colors, positions, Shader.TileMode.CLAMP
        )
        paint.shader = gradient
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h != lastHeight) {
            lastHeight = h
            createGradient()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        if (expansionProgress < 1f && height > 0) {
            val progressAlpha = 1f - expansionProgress

            // Draw fade
            paint.alpha = (progressAlpha * 255).toInt()
            canvas.drawRect(
                    0f,
                    height - fadeHeight,
                    width.toFloat(),
                    height.toFloat(),
                    paint
            )

            // Draw 3 dots
            dotPaint.alpha = (progressAlpha * 255).toInt()
            val cx = width / 2f
            val cy = height - dotBottomMargin

            canvas.drawCircle(cx - dotSpacing, cy, dotRadius, dotPaint)
            canvas.drawCircle(cx, cy, dotRadius, dotPaint)
            canvas.drawCircle(cx + dotSpacing, cy, dotRadius, dotPaint)
        }
    }
}
