package app.simple.felicity.decorations.fastscroll

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.decoration.R
import app.simple.felicity.theme.managers.ThemeManager
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min

/**
 * A lightweight vertical slider (pill) fast scroller that:
 *  - Attaches as an overlay to a RecyclerView (added to its parent).
 *  - Displays a draggable pill along the right edge.
 *  - Dragging the pill scrolls the list using percentage (0f..1f) of total item count.
 *  - Observes RecyclerView scroll events to keep pill position in sync when user scrolls normally.
 *
 * Percentage mapping rules:
 *  - percent = (firstVisible + intraItemOffset) / (totalItems - 1) when totalItems > 1 else 0.
 *  - intraItemOffset adds smoothness based on first visible view's top offset.
 */
class SectionedFastScroller @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var recyclerRef: WeakReference<RecyclerView>? = null

    // Half-circle handle configuration
    private val handleRadius = dp(28f) // visual radius (diameter = height)
    private val minRadius = dp(22f)
    private val maxRadius = dp(40f)
    private val touchExtra = dp(12f)

    private val handleColorActive = ThemeManager.accent.primaryAccentColor
    private val handleColorInactive = ThemeManager.theme.viewGroupTheme.backgroundColor
    private val ridgeColor = ThemeManager.theme.textViewTheme.secondaryTextColor

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = handleColorInactive }
    private val ridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ridgeColor
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
    }

    // Path + rect for internal half-circle rendering
    private val handlePath = Path()
    private val circleRect = RectF()

    // Custom drawable support
    private var handleDrawable: Drawable? = null
    private var handleDrawableActive: Drawable? = null
    private var useIntrinsicSize = true

    // State
    private var percent = 0f // 0..1
    private var dragging = false
    private var enabledWhileEmpty = false

    // Animation / visibility
    private var visible = true
    private var autoHideDelay = 1500L
    private var visibilityAnimator: ValueAnimator? = null
    private val autoHideRunnable = Runnable { if (!dragging) hide(true) }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (!dragging) updatePercentFromRecycler()
            if (dy != 0) {
                show(true)
                scheduleAutoHide()
            }
        }
    }

    init {
        isClickable = false
        isFocusable = false
        setWillNotDraw(false)
        alpha = 0f // start hidden until first scroll
        translationX = handleRadius // off-screen to right partially
        visible = false
    }

    /** Attach the fast scroller overlay to the RecyclerView's parent (must be a ViewGroup). */
    fun attachTo(recyclerView: RecyclerView) {
        recyclerRef = WeakReference(recyclerView)
        recyclerView.removeOnScrollListener(scrollListener)
        recyclerView.addOnScrollListener(scrollListener)
        val parent = recyclerView.parent
        if (parent is ViewGroup && parent.indexOfChild(this) == -1) {
            parent.addView(
                    this,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        post { updatePercentFromRecycler(); show(true); scheduleAutoHide() }
    }

    /** Allow enabling drag even if adapter empty (mostly for testing). */
    @Suppress("unused")
    fun setEnabledWhileEmpty(enable: Boolean) {
        enabledWhileEmpty = enable
    }

    /** Returns current scroll progress percent [0f,1f]. */
    @Suppress("unused")
    fun getPercent(): Float = percent

    /** Programmatically set scroll percent and scroll list (clamped). */
    @Suppress("unused")
    fun setPercent(p: Float) {
        val clamped = p.coerceIn(0f, 1f); if (clamped != percent) {
            percent = clamped; scrollToPercent(clamped); invalidate()
        }
    }

    /** Set the delay before the scroller auto-hides when not in use (in milliseconds). */
    @Suppress("unused")
    fun setAutoHideDelay(delayMillis: Long) {
        autoHideDelay = delayMillis
    }

    /** Show the fast scroller, with optional animation. */
    fun show(animated: Boolean) {
        if (visible) return
        visible = true
        visibilityAnimator?.cancel()
        if (!animated) {
            alpha = 1f
            translationX = 0f
        } else {
            val startAlpha = alpha
            val startTx = translationX
            visibilityAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 220L
                interpolator = DecelerateInterpolator()
                addUpdateListener { va ->
                    val f = va.animatedFraction
                    alpha = startAlpha + (1f - startAlpha) * f
                    translationX = startTx + (0f - startTx) * f
                }
                start()
            }
        }
    }

    /** Hide the fast scroller, with optional animation. */
    fun hide(animated: Boolean) {
        if (!visible) return
        visible = false
        visibilityAnimator?.cancel()
        if (!animated) {
            alpha = 0f
            translationX = handleRadius
        } else {
            val startAlpha = alpha
            val startTx = translationX
            visibilityAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 260L
                interpolator = DecelerateInterpolator()
                addUpdateListener { va ->
                    val f = va.animatedFraction
                    alpha = startAlpha * (1f - f)
                    translationX = startTx + (handleRadius - startTx) * f
                }
                start()
            }
        }
    }

    private fun scheduleAutoHide() {
        removeCallbacks(autoHideRunnable)
        postDelayed(autoHideRunnable, autoHideDelay)
    }

    private fun scrollToPercent(p: Float) {
        val rv = recyclerRef?.get() ?: return
        val adapter = rv.adapter ?: return
        val count = adapter.itemCount
        if (count <= 0) return
        val target = ((count - 1) * p).toInt().coerceIn(0, count - 1)
        val lm = rv.layoutManager
        if (lm is LinearLayoutManager) {
            // Use scrollToPositionWithOffset for consistent placement.
            lm.scrollToPositionWithOffset(target, 0)
        } else {
            rv.scrollToPosition(target)
        }
    }

    private fun updatePercentFromRecycler() {
        val rv = recyclerRef?.get() ?: return
        val adapter = rv.adapter ?: return
        val total = adapter.itemCount
        if (total <= 1) {
            percent = 0f; invalidate(); return
        }
        val lm = rv.layoutManager
        if (lm is LinearLayoutManager && lm.orientation == RecyclerView.VERTICAL) {
            val first = lm.findFirstVisibleItemPosition()
            val firstView = lm.findViewByPosition(first)
            if (first == RecyclerView.NO_POSITION || firstView == null) return
            val itemHeight = max(1, firstView.height)
            val offsetInside = (-firstView.top).toFloat() / itemHeight.toFloat()
            val raw = (first + offsetInside) / (total - 1).toFloat()
            val newPercent = raw.coerceIn(0f, 1f)
            if (newPercent != percent) {
                percent = newPercent; invalidate()
            }
        } else {
            // Fallback: use computeVerticalScrollOffset / range for non-linear managers
            val range = rv.computeVerticalScrollRange() - rv.computeVerticalScrollExtent()
            val off = rv.computeVerticalScrollOffset()
            val raw = if (range <= 0) 0f else off.toFloat() / range.toFloat()
            val newPercent = raw.coerceIn(0f, 1f)
            if (newPercent != percent) {
                percent = newPercent; invalidate()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rv = recyclerRef?.get()
        val adapterCount = rv?.adapter?.itemCount ?: 0
        val adapterEmpty = adapterCount <= 0
        if (adapterEmpty && !enabledWhileEmpty) return
        if (alpha <= 0f) return
        val w = width.toFloat();
        val h = height.toFloat(); if (w <= 0f || h <= 0f) return

        val custom = handleDrawable != null
        if (custom) {
            drawCustomHandle(canvas, adapterCount, w, h)
            return
        }

        val radius = computeRadius(adapterCount)
        val centerY = radius + (h - radius * 2f) * percent
        val clampedCY = centerY.coerceIn(radius, h - radius)
        circleRect.set(w - radius * 2f, clampedCY - radius, w, clampedCY + radius)
        handlePath.reset()
        handlePath.moveTo(w, clampedCY - radius)
        handlePath.lineTo(w, clampedCY + radius)
        handlePath.addArc(circleRect, 90f, 180f)
        handlePath.close()
        handlePaint.color = if (dragging) handleColorActive else handleColorInactive
        canvas.drawPath(handlePath, handlePaint)
        val ridgeCount = 3
        val ridgeSpacing = radius * 0.5f / (ridgeCount - 1)
        val ridgeMaxLength = radius * 1.05f
        val startX = w - radius * 1.6f
        val endBase = w - radius * 0.3f
        for (i in 0 until ridgeCount) {
            val ry = clampedCY - ridgeSpacing * (ridgeCount - 1) / 2f + i * ridgeSpacing
            val shrinkFactor = 1f - 0.15f * (kotlin.math.abs(i - (ridgeCount - 1) / 2f))
            val endX = startX + ridgeMaxLength * shrinkFactor
            canvas.drawLine(startX, ry, min(endX, endBase), ry, ridgePaint)
        }
    }

    private fun drawCustomHandle(canvas: Canvas, adapterCount: Int, w: Float, h: Float) {
        val inactive = handleDrawable ?: return
        val active = handleDrawableActive
        val drawable = if (dragging) (active ?: inactive) else inactive
        val intrinsicW = if (useIntrinsicSize && drawable.intrinsicWidth > 0) drawable.intrinsicWidth else dp(56f).toInt()
        val intrinsicH = if (useIntrinsicSize && drawable.intrinsicHeight > 0) drawable.intrinsicHeight else dp(56f).toInt()
        val available = (h - intrinsicH).coerceAtLeast(1f)
        val top = (percent * available).coerceIn(0f, h - intrinsicH)
        val left = w - intrinsicW // flush to right edge
        drawable.setBounds(left.toInt(), top.toInt(), w.toInt(), (top + intrinsicH).toInt())
        drawable.draw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rv = recyclerRef?.get()
        val adapterCount = rv?.adapter?.itemCount ?: 0
        val adapterEmpty = adapterCount <= 0
        if (adapterEmpty && !enabledWhileEmpty) return false
        val w = width.toFloat()
        val h = height.toFloat(); if (w == 0f || h == 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (hitTest(event.x, event.y, adapterCount)) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    dragging = true
                    show(true)
                    removeCallbacks(autoHideRunnable)
                    updatePercentFromTouch(event.y, adapterCount)
                    invalidate()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    updatePercentFromTouch(event.y, adapterCount)
                    invalidate()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    scheduleAutoHide()
                    invalidate()
                    performClick() // formalize end of interaction
                    return true
                }
                return false
            }
        }
        return false
    }

    override fun performClick(): Boolean {
        // No click action, but maintain accessibility contract
        return super.performClick()
    }

    private fun hitTest(x: Float, y: Float, adapterCount: Int): Boolean {
        val w = width.toFloat()
        val custom = handleDrawable != null
        if (custom) {
            val inactive = handleDrawable ?: return false
            val intrinsicW = if (useIntrinsicSize && inactive.intrinsicWidth > 0) inactive.intrinsicWidth else dp(56f).toInt()
            val intrinsicH = if (useIntrinsicSize && inactive.intrinsicHeight > 0) inactive.intrinsicHeight else dp(56f).toInt()
            val available = (height - intrinsicH).coerceAtLeast(1)
            val top = (percent * available).coerceIn(0f, (height - intrinsicH).toFloat())
            val rect = RectF(w - intrinsicW - touchExtra, top - touchExtra, w + touchExtra, top + intrinsicH + touchExtra)
            return rect.contains(x, y)
        }
        val radius = computeRadius(adapterCount)
        val centerY = radius + (height - radius * 2f) * percent
        val cy = centerY.coerceIn(radius, height - radius)
        val rect = RectF(w - radius * 2f - touchExtra, cy - radius - touchExtra, w + touchExtra, cy + radius + touchExtra)
        return rect.contains(x, y)
    }

    private fun updatePercentFromTouch(y: Float, adapterCount: Int) {
        val h = height.toFloat(); if (h <= 0f) return
        val custom = handleDrawable != null
        if (custom) {
            val inactive = handleDrawable ?: return
            val intrinsicH = if (useIntrinsicSize && inactive.intrinsicHeight > 0) inactive.intrinsicHeight.toFloat() else dp(56f)
            val available = (h - intrinsicH).coerceAtLeast(1f)
            val clampedTop = y - intrinsicH / 2f
            val clamped = clampedTop.coerceIn(0f, h - intrinsicH)
            val newPercent = (clamped / available).coerceIn(0f, 1f)
            if (newPercent != percent) {
                percent = newPercent; scrollToPercent(percent)
            }
            return
        }
        val radius = computeRadius(adapterCount)
        val available = (h - radius * 2f).coerceAtLeast(1f)
        val clampedY = y.coerceIn(radius, h - radius)
        val newPercent = (clampedY - radius) / available
        if (newPercent != percent) {
            percent = newPercent; scrollToPercent(percent)
        }
    }

    private fun dp(value: Float): Float {
        val metrics: DisplayMetrics = resources.displayMetrics; return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics)
    }

    private fun computeRadius(adapterCount: Int): Float = when {
        adapterCount <= 0 -> handleRadius
        adapterCount <= 20 -> maxRadius
        adapterCount >= 400 -> minRadius
        else -> minRadius + (maxRadius - minRadius) * (1f - (adapterCount - 20f) / 380f)
    }

    /** Provide a custom drawable resource for the handle (used for both active & inactive). */
    fun setHandleDrawable(@DrawableRes resId: Int, useIntrinsic: Boolean = true) {
        val d = AppCompatResources.getDrawable(context, resId)
        handleDrawable = d
        handleDrawableActive = null
        useIntrinsicSize = useIntrinsic
        invalidate()
    }

    /** Provide separate inactive and active drawables. */
    fun setHandleDrawables(@DrawableRes inactiveResId: Int, @DrawableRes activeResId: Int, useIntrinsic: Boolean = true) {
        handleDrawable = AppCompatResources.getDrawable(context, inactiveResId)
        handleDrawableActive = AppCompatResources.getDrawable(context, activeResId)
        useIntrinsicSize = useIntrinsic
        invalidate()
    }

    /** Provide a custom drawable instance. */
    fun setHandleDrawable(drawable: Drawable?, drawableActive: Drawable? = null, useIntrinsic: Boolean = true) {
        handleDrawable = drawable
        handleDrawableActive = drawableActive
        useIntrinsicSize = useIntrinsic
        invalidate()
    }

    /** Remove any custom drawable and revert to internal rendering. */
    fun clearHandleDrawable() {
        handleDrawable = null
        handleDrawableActive = null
        invalidate()
    }

    companion object {
        fun attach(recyclerView: RecyclerView): SectionedFastScroller {
            val scroller = SectionedFastScroller(recyclerView.context); scroller.attachTo(recyclerView);
            scroller.setHandleDrawable(R.drawable.ic_fast_thumb)
            scroller.handleDrawable?.setTint(ThemeManager.accent.primaryAccentColor)
            return scroller
        }
    }
}
