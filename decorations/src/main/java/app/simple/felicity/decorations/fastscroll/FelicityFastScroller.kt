package app.simple.felicity.decorations.fastscroll

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.theme.managers.ThemeManager
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

class FelicityFastScroller @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Appearance configuration
    private val trackWidth = dp(4f)
    private val majorGraduationLength = dp(18f) // Increased from 12f
    private val minorGraduationLength = dp(10f) // Increased from 6f
    private val graduationWidth = dp(3f) // Increased from 2f
    private val magnifiedGraduationLength = dp(28f) // New: magnified length
    private val magnifiedGraduationWidth = dp(4f) // New: magnified width
    private val edgeActivationWidth = dp(24f)
    private val minTouchSlopY = dp(4f)
    private val magnifyRadius = dp(40f) // Radius around touch point for magnification

    // Colors (fallback simple palette – could later integrate theme or dynamic colors)
    private val trackColor = 0x00000000 // Transparent
    private val majorGraduationColor = ThemeManager.theme.viewGroupTheme.dividerColor
    private val minorGraduationColor = ThemeManager.theme.viewGroupTheme.highlightColor
    private val activeGraduationColor = ThemeManager.accent.primaryAccentColor

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = trackColor
    }
    private val majorGraduationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = majorGraduationColor
        strokeWidth = graduationWidth
        strokeCap = Paint.Cap.ROUND
    }
    private val minorGraduationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = minorGraduationColor
        strokeWidth = graduationWidth
        strokeCap = Paint.Cap.ROUND
    }
    private val activeGraduationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = activeGraduationColor
        strokeWidth = graduationWidth
        strokeCap = Paint.Cap.ROUND
    }
    private val magnifiedMajorGraduationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = majorGraduationColor
        strokeWidth = magnifiedGraduationWidth
        strokeCap = Paint.Cap.ROUND
    }
    private val magnifiedMinorGraduationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = minorGraduationColor
        strokeWidth = magnifiedGraduationWidth
        strokeCap = Paint.Cap.ROUND
    }

    // Rects reused
    private val trackRect = RectF()

    private var recyclerRef: WeakReference<RecyclerView>? = null

    // Active region (computed each draw / size change)
    private var regionTop = 0f
    private var regionBottom = 0f
    private var regionHeight = 0f

    // Current position state
    private var currentPosition = 0f // 0.0 to 1.0 representing position in the list
    private var isDragging = false
    private var lastTouchY = 0f

    // Adapter meta cache
    private var itemCount = 0
    private var majorStepCount = 10 // Number of major graduations
    private var minorStepsPerMajor = 5 // Number of minor steps between major ones

    // Auto hide
    private val hideDelayMs = 1200L
    private var visible = false
    private var fadeAnimator: ValueAnimator? = null

    private val hideRunnable = Runnable { if (!isDragging) fadeOut() }

    // Listener to sync thumb while list scrolls normally
    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (!isDragging) {
                syncGraduationWithRecycler()
            }
        }
    }

    init {
        // Keep it visible but transparent so it can intercept edge touches
        alpha = 0f
        visibility = VISIBLE
        isClickable = true
        isFocusable = false
    }

    /** Attach this fast scroller to a RecyclerView. Adds itself to parent if needed. */
    fun attachTo(recyclerView: RecyclerView) {
        recyclerRef?.get()?.removeOnScrollListener(scrollListener)
        recyclerRef = WeakReference(recyclerView)
        recyclerView.addOnScrollListener(scrollListener)

        val parent = recyclerView.parent
        if (parent is ViewGroup && parent.indexOfChild(this) == -1) {
            parent.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        post { syncGraduationWithRecycler() }
    }

    override fun onDetachedFromWindow() {
        recyclerRef?.get()?.removeOnScrollListener(scrollListener)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeRegion(h)
        syncGraduationWithRecycler()
    }

    private fun computeRegion(totalHeight: Int) {
        regionHeight = totalHeight * 0.6f
        regionTop = (totalHeight - regionHeight) / 2f
        regionBottom = regionTop + regionHeight
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!visible && !isDragging) return

        // Draw track (right aligned)
        val left = width - trackWidth - dp(8f) // small inset from absolute edge
        val right = left + trackWidth
        trackRect.set(left, regionTop, right, regionBottom)
        canvas.drawRoundRect(trackRect, trackWidth, trackWidth, trackPaint)

        // Draw graduations
        drawGraduations(canvas, left, right)
    }

    private fun drawGraduations(canvas: Canvas, left: Float, right: Float) {
        val touchY = if (isDragging) lastTouchY else -1f

        // Draw major graduations
        for (i in 0..majorStepCount) {
            val y = regionTop + (i.toFloat() / majorStepCount) * regionHeight
            val startX = right

            // Check if this graduation is within magnify radius
            val isMagnified = isDragging && touchY > 0 && kotlin.math.abs(y - touchY) < magnifyRadius
            val length = if (isMagnified) magnifiedGraduationLength else majorGraduationLength
            val endX = right + length

            // Check if this is the active graduation
            val graduationFraction = i.toFloat() / majorStepCount
            val isActive = kotlin.math.abs(currentPosition - graduationFraction) < (1f / majorStepCount / 2f)

            val paint = when {
                isActive && (visible || isDragging) -> activeGraduationPaint
                isMagnified -> magnifiedMajorGraduationPaint
                else -> majorGraduationPaint
            }

            canvas.drawLine(startX, y, endX, y, paint)
        }

        // Draw minor graduations between major ones
        for (i in 0 until majorStepCount) {
            for (j in 1 until minorStepsPerMajor) {
                val majorY1 = regionTop + (i.toFloat() / majorStepCount) * regionHeight
                val majorY2 = regionTop + ((i + 1).toFloat() / majorStepCount) * regionHeight
                val y = majorY1 + (j.toFloat() / minorStepsPerMajor) * (majorY2 - majorY1)

                val startX = right

                // Check if this graduation is within magnify radius
                val isMagnified = isDragging && touchY > 0 && kotlin.math.abs(y - touchY) < magnifyRadius
                val length = if (isMagnified) magnifiedGraduationLength * 0.7f else minorGraduationLength
                val endX = right + length

                val paint = if (isMagnified) magnifiedMinorGraduationPaint else minorGraduationPaint

                canvas.drawLine(startX, y, endX, y, paint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val recycler = recyclerRef?.get() ?: return false
        if (recycler.adapter == null) return false

        val y = event.y
        val x = event.x

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (shouldActivateFromDown(x, y)) {
                    parent.requestDisallowInterceptTouchEvent(true)
                    startDrag(y)
                    performClick()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    handleDrag(y)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    endDrag()
                    return true
                }
            }
        }
        return isDragging
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun shouldActivateFromDown(x: Float, y: Float): Boolean {
        // Edge activation OR (already visible and touching within reasonable horizontal proximity of track)
        val inEdge = x >= width - edgeActivationWidth
        val inRegion = y in regionTop..regionBottom
        return inRegion && (inEdge || visible)
    }

    private fun startDrag(y: Float) {
        isDragging = true
        lastTouchY = y
        fadeIn()
        updateGraduationPosition(y)
        invalidate()
        removeCallbacks(hideRunnable)
    }

    private fun handleDrag(y: Float) {
        if (kotlin.math.abs(y - lastTouchY) < minTouchSlopY) return
        lastTouchY = y
        updateGraduationPosition(y)
    }

    private fun endDrag() {
        isDragging = false
        postDelayed(hideRunnable, hideDelayMs)
        invalidate()
    }

    private fun mapGraduationToRecycler() {
        val recycler = recyclerRef?.get() ?: return
        val adapter = recycler.adapter ?: return
        itemCount = adapter.itemCount
        if (itemCount <= 0) return

        val targetIndex = (currentPosition * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
        val lm = recycler.layoutManager as? LinearLayoutManager
        if (lm != null) {
            // Jump scrolling (step) – immediate scroll to position
            lm.scrollToPositionWithOffset(targetIndex, 0)
        } else {
            recycler.scrollToPosition(targetIndex)
        }
    }

    private fun findNearestMajorStep(position: Float): Float {
        val stepSize = 1f / majorStepCount
        val nearestStep = (position / stepSize).roundToInt()
        return (nearestStep * stepSize).coerceIn(0f, 1f)
    }

    private fun updateGraduationPosition(y: Float) {
        val rawPosition = ((y - regionTop) / regionHeight).coerceIn(0f, 1f)

        // Snap to nearest major graduation when close enough
        val snapThreshold = 1f / majorStepCount / 3f // Snap within 1/3 of a major step
        val nearestMajorStep = findNearestMajorStep(rawPosition)

        currentPosition = if (kotlin.math.abs(rawPosition - nearestMajorStep) < snapThreshold) {
            nearestMajorStep
        } else {
            rawPosition
        }

        mapGraduationToRecycler()
        invalidate()
    }

    private fun syncGraduationWithRecycler() {
        val recycler = recyclerRef?.get() ?: return
        val adapter = recycler.adapter ?: return
        itemCount = adapter.itemCount
        if (itemCount <= 0) return
        val layoutManager = recycler.layoutManager as? LinearLayoutManager ?: return
        val first = layoutManager.findFirstVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION) return
        val fraction = if (itemCount <= 1) 0f else first.toFloat() / (itemCount - 1).toFloat()
        currentPosition = fraction
        invalidate()
    }

    private fun fadeIn() {
        if (visible) return
        visible = true
        animateAlpha(1f)
    }

    private fun fadeOut() {
        if (!visible || isDragging) return
        animateAlpha(0f) { visible = false }
    }

    private fun animateAlpha(target: Float, end: (() -> Unit)? = null) {
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(alpha, target).apply {
            duration = if (target == 1f) 160 else 260
            interpolator = DecelerateInterpolator()
            addUpdateListener { valueAnimator ->
                this@FelicityFastScroller.alpha = valueAnimator.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    end?.invoke()
                }
            })
            start()
        }
    }

    private fun dp(value: Float): Float {
        val metrics: DisplayMetrics = resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics)
    }

    companion object {
        /** Convenient helper to attach a new fast scroller to a given RecyclerView. */
        fun attach(recyclerView: RecyclerView): FelicityFastScroller {
            val ctx = recyclerView.context
            val scroller = FelicityFastScroller(ctx)
            scroller.attachTo(recyclerView)
            return scroller
        }
    }
}