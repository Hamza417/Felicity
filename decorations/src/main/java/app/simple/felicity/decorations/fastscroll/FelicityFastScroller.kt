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
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

class FelicityFastScroller @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Appearance configuration
    private val trackWidth = dp(4f)
    private val thumbRadius = dp(10f)
    private val edgeActivationWidth = dp(24f)
    private val minTouchSlopY = dp(4f)

    // Colors (fallback simple palette – could later integrate theme or dynamic colors)
    private val trackColor = 0x44FFFFFF
    private val thumbColorIdle = 0xAAFFFFFF.toInt()
    private val thumbColorActive = 0xFFFFFFFF.toInt()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = trackColor
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = thumbColorIdle
    }

    // Rects reused
    private val trackRect = RectF()

    private var recyclerRef: WeakReference<RecyclerView>? = null

    // Active region (computed each draw / size change)
    private var regionTop = 0f
    private var regionBottom = 0f
    private var regionHeight = 0f

    // Thumb state
    private var thumbCenterY = 0f
    private var isDragging = false
    private var lastTouchY = 0f

    // Adapter meta cache
    private var itemCount = 0

    // Auto hide
    private val hideDelayMs = 1200L
    private var visible = false
    private var fadeAnimator: ValueAnimator? = null

    private val hideRunnable = Runnable { if (!isDragging) fadeOut() }

    // Listener to sync thumb while list scrolls normally
    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (!isDragging) {
                syncThumbWithRecycler()
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
        post { syncThumbWithRecycler() }
    }

    override fun onDetachedFromWindow() {
        recyclerRef?.get()?.removeOnScrollListener(scrollListener)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeRegion(h)
        syncThumbWithRecycler()
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

        // Draw thumb
        val thumbX = (left + right) / 2f
        canvas.drawCircle(thumbX, thumbCenterY.coerceIn(regionTop, regionBottom), thumbRadius, thumbPaint)
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
        updateThumbPosition(y)
        thumbPaint.color = thumbColorActive
        invalidate()
        removeCallbacks(hideRunnable)
    }

    private fun handleDrag(y: Float) {
        if (kotlin.math.abs(y - lastTouchY) < minTouchSlopY) return
        lastTouchY = y
        updateThumbPosition(y)
    }

    private fun endDrag() {
        isDragging = false
        thumbPaint.color = thumbColorIdle
        postDelayed(hideRunnable, hideDelayMs)
        invalidate()
    }

    private fun updateThumbPosition(y: Float) {
        thumbCenterY = y.coerceIn(regionTop, regionBottom)
        mapThumbToRecycler()
        invalidate()
    }

    private fun syncThumbWithRecycler() {
        val recycler = recyclerRef?.get() ?: return
        val adapter = recycler.adapter ?: return
        itemCount = adapter.itemCount
        if (itemCount <= 0) return
        val layoutManager = recycler.layoutManager as? LinearLayoutManager ?: return
        val first = layoutManager.findFirstVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION) return
        val fraction = if (itemCount <= 1) 0f else first.toFloat() / (itemCount - 1).toFloat()
        thumbCenterY = regionTop + regionHeight * fraction
        invalidate()
    }

    private fun mapThumbToRecycler() {
        val recycler = recyclerRef?.get() ?: return
        val adapter = recycler.adapter ?: return
        itemCount = adapter.itemCount
        if (itemCount <= 0) return

        val fraction = (thumbCenterY - regionTop) / regionHeight
        val targetIndex = ((itemCount - 1) * fraction).roundToInt().coerceIn(0, itemCount - 1)
        (recycler.layoutManager as? LinearLayoutManager)?.let { lm ->
            // Jump scrolling (step) – immediate scroll to position
            lm.scrollToPositionWithOffset(targetIndex, 0)
        } ?: recycler.scrollToPosition(targetIndex)
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
                ViewCompat.postInvalidateOnAnimation(this@FelicityFastScroller)
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