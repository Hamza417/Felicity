package app.simple.felicity.decorations.fastscroll

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import app.simple.felicity.decoration.R
import app.simple.felicity.shared.utils.ColorUtils.blendColors
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

// High-performance fast scroller with adapter index mapping, throttling, and prefetch optimization
class SlideFastScroller @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr), ThemeChangedListener {

    private var recyclerRef: WeakReference<RecyclerView>? = null

    // Handle (pill) configuration
    private val handleRadius = dp(28f)
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

    // Path + rect for internal half-pill rendering
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
    private var currentAdapterPosition = -1
    private var lastAppliedPosition = -1

    // Legacy step-based fields (for compatibility)
    private var stepScrollingEnabled = false // Disabled by default in favor of index mapping
    private var stepPercent = 0.05f
    private var jumpToPositionMode = false
    private var lastAppliedStepIndex = -1

    // Animation / visibility
    private var visible = true
    private var autoHideDelay = 1500L
    private var visibilityAnimator: ValueAnimator? = null
    private var fadeToIdleMode = false // If true, fade to idleAlpha instead of hiding completely
    private var idleAlpha = 0.2f // Alpha when idle (only used if fadeToIdleMode is true)
    private var isIdle = false // Track if currently in idle/dimmed state to prevent flooding show() calls
    private val autoHideRunnable = Runnable { if (!dragging) fadeToIdle(true) }

    // Performance optimization fields
    private val handler = Handler(Looper.getMainLooper())
    private val updateThrottleMs = getOptimalUpdateInterval() // Dynamic based on refresh rate
    private var pendingScrollPosition = -1
    private var smoothScrollEnabled = true
    private var lightBindMode = false
    private var originalCacheSize = -1
    private var originalPrefetchCount = -1
    private var lightBindExitPending = false // Prevent multiple exit calls

    // Delayed full-bind while dragging
    private val delayedFullBindDelay = 200L
    private val delayedFullBindRunnable = Runnable {
        if (dragging && lightBindMode) {
            exitLightBindMode()
        }
    }

    // Smooth scrolling support
    private var smoothScrollingEnabled = true
    private var totalScrollRange = 0
    private var lastComputedScrollRange = 0L

    // Batched scroll updates
    private var pendingScrollUpdate: Runnable? = null
    private var pendingPercentUpdate: Runnable? = null
    private val batchedScrollRunnable = Runnable {
        val pos = pendingScrollPosition
        if (pos >= 0) {
            performScrollToPosition(pos)
        }
        pendingScrollUpdate = null
    }

    // Smooth percent scroll runnable for continuous updates
    private val batchedPercentRunnable = Runnable {
        val rv = recyclerRef?.get()
        if (rv != null && dragging) { // Only continue if still dragging
            val targetPercent = percent
            scrollToPercentSmooth(targetPercent)
        }
        pendingPercentUpdate = null
    }

    // Index-to-offset cache for variable height items
    private val indexOffsetCache = mutableMapOf<Int, Int>()
    private var cacheInvalidated = true

    // Reference to a SnapHelper (if attached) to disable auto-snapping during drag
    private var detachedSnapHelper: SnapHelper? = null

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (!dragging) updatePercentFromRecycler()
            if (dy != 0) {
                show(true)
                scheduleAutoHide()
                // Invalidate cache on scroll
                if (cacheInvalidated) {
                    cacheCurrentOffsets()
                    cacheInvalidated = false
                }
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (newState == RecyclerView.SCROLL_STATE_IDLE && lightBindMode && !dragging) {
                // Re-enable heavy binding after scroll settles and user is not dragging
                exitLightBindMode()
            }
        }
    }

    init {
        isClickable = false
        isFocusable = false
        setWillNotDraw(false)
        alpha = 0f
        translationX = handleRadius
        visible = false
    }

    /** Attach the fast scroller overlay to the RecyclerView's parent (must be a ViewGroup). */
    fun attachTo(recyclerView: RecyclerView) {
        recyclerRef = WeakReference(recyclerView)
        recyclerView.removeOnScrollListener(scrollListener)
        recyclerView.addOnScrollListener(scrollListener)

        // Configure prefetch optimization
        setupPrefetching(recyclerView)

        val parent = recyclerView.parent
        if (parent is ViewGroup && parent.indexOfChild(this) == -1) {
            parent.addView(
                    this,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        post {
            updatePercentFromRecycler()
            show(true)
            scheduleAutoHide()
        }
    }

    private fun setupPrefetching(recyclerView: RecyclerView) {
        // Store original values
        originalCacheSize = recyclerView.recycledViewPool.getRecycledViewCount(0)

        // Increase cache size for better performance during fast scrolling
        recyclerView.setItemViewCacheSize(20) // Default is usually 2

        val layoutManager = recyclerView.layoutManager
        if (layoutManager is LinearLayoutManager) {
            originalPrefetchCount = layoutManager.initialPrefetchItemCount
            layoutManager.initialPrefetchItemCount = 10 // Prefetch more items
        }
    }

    private fun cacheCurrentOffsets() {
        val rv = recyclerRef?.get() ?: return
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return

        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()

        if (first >= 0 && last >= 0) {
            for (i in first..last) {
                val view = layoutManager.findViewByPosition(i)
                if (view != null) {
                    indexOffsetCache[i] = view.top
                }
            }
        }
    }

    private fun enterLightBindMode() {
        if (lightBindMode) return
        lightBindMode = true

        val rv = recyclerRef?.get() ?: return
        val adapter = rv.adapter

        // Check for enhanced interface first, then fall back to basic interface
        when {
            adapter is FastScrollBindingController -> {
                adapter.setLightBindMode(true)

                // If adapter wants custom binding control, trigger rebind of visible items
                if (adapter.shouldHandleCustomBinding()) {
                    val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
                    val firstVisible = layoutManager.findFirstVisibleItemPosition()
                    val lastVisible = layoutManager.findLastVisibleItemPosition()

                    if (firstVisible >= 0 && lastVisible >= 0 && firstVisible <= lastVisible) {
                        // Trigger custom binding for visible items
                        for (position in firstVisible..lastVisible) {
                            val view = layoutManager.findViewByPosition(position)
                            if (view != null) {
                                val holder = rv.getChildViewHolder(view)
                                if (holder != null) {
                                    adapter.onBindViewHolder(holder, position, true)
                                }
                            }
                        }
                    }
                }
            }
            adapter is FastScrollOptimizedAdapter -> {
                adapter.setLightBindMode(true)
            }
        }
    }

    private fun exitLightBindMode() {
        if (!lightBindMode || lightBindExitPending) return
        lightBindMode = false
        lightBindExitPending = true
        // Cancel any pending delayed full-bind trigger
        handler.removeCallbacks(delayedFullBindRunnable)

        val rv = recyclerRef?.get() ?: return
        val adapter = rv.adapter ?: return
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return

        when {
            adapter is FastScrollBindingController -> {
                adapter.setLightBindMode(false)

                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible = layoutManager.findLastVisibleItemPosition()

                if (firstVisible >= 0 && lastVisible >= 0 && firstVisible <= lastVisible) {
                    if (adapter.shouldHandleCustomBinding()) {
                        // Use custom binding to restore full content
                        for (position in firstVisible..lastVisible) {
                            val view = layoutManager.findViewByPosition(position)
                            if (view != null) {
                                val holder = rv.getChildViewHolder(view)
                                if (holder != null) {
                                    adapter.onBindViewHolder(holder, position, false)
                                }
                            }
                        }
                    } else {
                        // Fall back to standard notification
                        val itemCount = lastVisible - firstVisible + 1
                        if (itemCount > 0) {
                            adapter.notifyItemRangeChanged(firstVisible, itemCount)
                        }
                    }
                }
            }
            adapter is FastScrollOptimizedAdapter -> {
                adapter.setLightBindMode(false)

                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible = layoutManager.findLastVisibleItemPosition()

                if (firstVisible >= 0 && lastVisible >= 0 && firstVisible <= lastVisible) {
                    val itemCount = lastVisible - firstVisible + 1
                    if (itemCount > 0) {
                        adapter.notifyItemRangeChanged(firstVisible, itemCount)
                    }
                }
            }
            else -> {
                // For non-optimized adapters, force standard rebinding
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible = layoutManager.findLastVisibleItemPosition()

                if (firstVisible >= 0 && lastVisible >= 0 && firstVisible <= lastVisible) {
                    val itemCount = lastVisible - firstVisible + 1
                    if (itemCount > 0) {
                        adapter.notifyItemRangeChanged(firstVisible, itemCount)
                    }
                }
            }
        }

        // Reset the flag after a short delay
        handler.postDelayed({ lightBindExitPending = false }, 100)
    }

    /**
     * Immediately exit light bind mode without delays - used when finger is lifted
     */
    private fun exitLightBindModeImmediate() {
        if (!lightBindMode || lightBindExitPending) return
        lightBindMode = false
        lightBindExitPending = true
        // Cancel any pending delayed full-bind trigger
        handler.removeCallbacks(delayedFullBindRunnable)

        val rv = recyclerRef?.get() ?: return
        val adapter = rv.adapter ?: return
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return

        when {
            adapter is FastScrollBindingController -> {
                adapter.setLightBindMode(false)

                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible = layoutManager.findLastVisibleItemPosition()

                if (firstVisible >= 0 && lastVisible >= 0 && firstVisible <= lastVisible) {
                    if (adapter.shouldHandleCustomBinding()) {
                        // Use custom binding to restore full content immediately
                        for (position in firstVisible..lastVisible) {
                            val view = layoutManager.findViewByPosition(position)
                            if (view != null) {
                                val holder = rv.getChildViewHolder(view)
                                if (holder != null) {
                                    adapter.onBindViewHolder(holder, position, false)
                                }
                            }
                        }
                    } else {
                        // Force immediate rebinding of all visible items
                        val itemCount = lastVisible - firstVisible + 1
                        if (itemCount > 0) {
                            adapter.notifyItemRangeChanged(firstVisible, itemCount)
                        }
                    }
                }
            }
            adapter is FastScrollOptimizedAdapter -> {
                adapter.setLightBindMode(false)

                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible = layoutManager.findLastVisibleItemPosition()

                if (firstVisible >= 0 && lastVisible >= 0 && firstVisible <= lastVisible) {
                    // Force immediate rebinding of all visible items
                    val itemCount = lastVisible - firstVisible + 1
                    if (itemCount > 0) {
                        adapter.notifyItemRangeChanged(firstVisible, itemCount)
                    }
                }
            }
            else -> {
                // For non-optimized adapters, force immediate standard rebinding
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible = layoutManager.findLastVisibleItemPosition()

                if (firstVisible >= 0 && lastVisible >= 0 && firstVisible <= lastVisible) {
                    val itemCount = lastVisible - firstVisible + 1
                    if (itemCount > 0) {
                        adapter.notifyItemRangeChanged(firstVisible, itemCount)
                    }
                }
            }
        }

        // Reset the flag immediately since we're doing synchronous binding
        lightBindExitPending = false
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
        val clamped = p.coerceIn(0f, 1f)
        if (clamped != percent) {
            percent = clamped
            val position = percentToAdapterPosition(clamped)
            scheduleScrollToPosition(position, force = true)
            invalidate()
        }
    }

    /** Set the delay before the scroller auto-hides when not in use (in milliseconds). */
    @Suppress("unused")
    fun setAutoHideDelay(delayMillis: Long) {
        autoHideDelay = delayMillis
    }

    /**
     * Enable or disable fade-to-idle mode.
     * When enabled, the scroller fades to a dim alpha (idleAlpha) instead of hiding completely.
     * This allows users to always see scroll position indicator without obstructing the view.
     */
    @Suppress("unused")
    fun setFadeToIdleMode(enabled: Boolean) {
        fadeToIdleMode = enabled
    }

    /**
     * Set the alpha value when idle (only used if fadeToIdleMode is enabled).
     * @param alpha Alpha value between 0f and 1f (default is 0.2f)
     */
    @Suppress("unused")
    fun setIdleAlpha(alpha: Float) {
        idleAlpha = alpha.coerceIn(0f, 1f)
    }

    /** Enable or disable smooth scrolling to snapped positions. */
    @Suppress("unused")
    fun setSmoothScrollEnabled(enabled: Boolean) {
        smoothScrollEnabled = enabled
    }

    /** Enable or disable pixel-based smooth scrolling during dragging. */
    @Suppress("unused")
    fun setSmoothScrollingEnabled(enabled: Boolean) {
        smoothScrollingEnabled = enabled
    }

    /** Legacy: Enable or disable step-based scrolling. */
    @Suppress("unused")
    fun setStepScrollingEnabled(enabled: Boolean) {
        stepScrollingEnabled = enabled
    }

    /** Legacy: Set the step size for step-based scrolling (default 5%). */
    @Suppress("unused")
    fun setStepPercent(percent: Float) {
        stepPercent = percent.coerceIn(0.01f, 0.5f)
    }

    /** Legacy: Enable or disable jump-to-position mode for scrolling. */
    @Suppress("unused")
    fun setJumpToPositionMode(enabled: Boolean) {
        jumpToPositionMode = enabled
    }

    /** Show the fast scroller, with optional animation. */
    fun show(animated: Boolean) {
        // In fadeToIdleMode, only animate if we're coming from idle state
        val needsAlphaAnimation = fadeToIdleMode && isIdle
        if (visible && !needsAlphaAnimation) return
        visible = true
        val wasIdle = isIdle
        isIdle = false
        visibilityAnimator?.cancel()

        val accentColor = ThemeManager.accent.primaryAccentColor
        val idleColor = Color.LTGRAY

        if (!animated) {
            alpha = 1f
            translationX = 0f
            if (wasIdle && fadeToIdleMode) {
                handleDrawable?.setTint(accentColor)
                handlePaint.color = accentColor
                invalidate()
            }
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
                    // Animate color back from gray to accent if coming from idle
                    if (wasIdle && fadeToIdleMode) {
                        val blendedColor = blendColors(idleColor, accentColor, f)
                        handleDrawable?.setTint(blendedColor)
                        handlePaint.color = blendedColor
                        invalidate()
                    }
                }
                start()
            }
        }
    }

    /** Hide the fast scroller, with optional animation. */
    fun hide(animated: Boolean) {
        if (!visible) return
        visible = false
        isIdle = true
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

    /**
     * Fade to idle state - either hides completely or fades to idleAlpha based on fadeToIdleMode.
     * @param animated Whether to animate the transition
     */
    private fun fadeToIdle(animated: Boolean) {
        if (!fadeToIdleMode) {
            // Use traditional hide behavior
            hide(animated)
            return
        }

        // Already idle, no need to animate again
        if (isIdle) return

        isIdle = true
        // Fade to idle alpha while keeping position visible
        visibilityAnimator?.cancel()

        val idleColor = Color.LTGRAY
        val startColor = ThemeManager.accent.primaryAccentColor

        if (!animated) {
            alpha = idleAlpha
            handleDrawable?.setTint(idleColor)
            handlePaint.color = idleColor
            invalidate()
        } else {
            val startAlpha = alpha
            visibilityAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 260L
                interpolator = DecelerateInterpolator()
                addUpdateListener { va ->
                    val f = va.animatedFraction
                    // Interpolate from current alpha to idleAlpha
                    alpha = startAlpha + (idleAlpha - startAlpha) * f
                    // Interpolate color from accent to light gray
                    val blendedColor = blendColors(startColor, idleColor, f)
                    handleDrawable?.setTint(blendedColor)
                    handlePaint.color = blendedColor
                    invalidate()
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
        // Pixel-based: compute target offset by percent of scrollable range, scrollBy delta.
        val range = rv.computeVerticalScrollRange() - rv.computeVerticalScrollExtent()
        if (range <= 0) return
        val target = (p.coerceIn(0f, 1f) * range).toInt()
        val current = rv.computeVerticalScrollOffset()
        val dy = target - current
        if (dy != 0) rv.scrollBy(0, dy)
    }

    private fun updatePercentFromRecycler() {
        val rv = recyclerRef?.get() ?: return
        val adapter = rv.adapter ?: return
        val count = adapter.itemCount

        if (count <= 1) {
            percent = 0f
            currentAdapterPosition = 0
            invalidate()
            return
        }

        // Use pixel-based calculation for smoother tracking
        val scrollRange = rv.computeVerticalScrollRange() - rv.computeVerticalScrollExtent()
        if (scrollRange > 0) {
            val currentOffset = rv.computeVerticalScrollOffset()
            val newPercent = (currentOffset.toFloat() / scrollRange.toFloat()).coerceIn(0f, 1f)

            if (abs(newPercent - percent) > 0.001f) {
                percent = newPercent
                // Update current position based on layout manager
                val layoutManager = rv.layoutManager as? LinearLayoutManager
                currentAdapterPosition = layoutManager?.findFirstVisibleItemPosition() ?: 0
                invalidate()
            }
        } else {
            // Fall back to position-based calculation for edge cases
            val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
            val firstVisible = layoutManager.findFirstVisibleItemPosition()
            if (firstVisible < 0) return

            val firstView = layoutManager.findViewByPosition(firstVisible)
            val newPercent = if (firstView != null) {
                val viewTop = firstView.top
                val itemHeight = firstView.height.coerceAtLeast(1)
                val offsetPercent = (-viewTop.toFloat() / itemHeight.toFloat()).coerceIn(0f, 1f)
                ((firstVisible + offsetPercent) / (count - 1)).coerceIn(0f, 1f)
            } else {
                firstVisible.toFloat() / (count - 1).toFloat()
            }

            if (abs(newPercent - percent) > 0.001f) {
                percent = newPercent
                currentAdapterPosition = firstVisible
                invalidate()
            }
        }
    }

    private fun applyStepForPercent(p: Float, force: Boolean = false) {
        val rv = recyclerRef?.get() ?: return
        val step = stepPercent.coerceIn(0.01f, 0.5f)
        val maxIndex = floor(1f / step).toInt()
        var idx = floor((p.coerceIn(0f, 0.9999f)) / step).toInt() // 0..maxIndex-1
        if (idx < 0) idx = 0
        if (idx >= maxIndex) idx = maxIndex - 1
        if (!force && idx == lastAppliedStepIndex) return
        lastAppliedStepIndex = idx
        val snappedPercent = (idx * step).coerceIn(0f, 1f)
        if (jumpToPositionMode) {
            val count = rv.adapter?.itemCount ?: 0
            if (count > 0) {
                val pos = ((count - 1) * snappedPercent).toInt().coerceIn(0, count - 1)
                rv.scrollToPosition(pos)
            }
        } else {
            scrollToPercent(snappedPercent)
        }
    }

    private fun percentToAdapterPosition(percent: Float): Int {
        val rv = recyclerRef?.get() ?: return 0
        val count = rv.adapter?.itemCount ?: 0
        return if (count > 0) {
            ((count - 1) * percent.coerceIn(0f, 1f)).toInt().coerceIn(0, count - 1)
        } else 0
    }

    private fun adapterPositionToPercent(position: Int): Float {
        val rv = recyclerRef?.get() ?: return 0f
        val count = rv.adapter?.itemCount ?: 0
        return if (count > 1) {
            position.toFloat() / (count - 1).toFloat()
        } else 0f
    }

    private fun scheduleScrollToPosition(position: Int, force: Boolean = false) {
        // REMOVED: No more deferred scrolling - call performScrollToPosition directly
        performScrollToPosition(position, directPositioning = true)
    }

    private fun performScrollToPosition(position: Int, directPositioning: Boolean = false) {
        val rv = recyclerRef?.get() ?: return
        val count = rv.adapter?.itemCount ?: 0
        if (position < 0 || position >= count) return

        if (position == lastAppliedPosition && !directPositioning) return
        lastAppliedPosition = position

        val layoutManager = rv.layoutManager as? LinearLayoutManager
        // Always use direct positioning (no smooth scrolling)
        if (indexOffsetCache.containsKey(position)) {
            val offset = indexOffsetCache[position] ?: 0
            layoutManager?.scrollToPositionWithOffset(position, offset)
        } else {
            rv.scrollToPosition(position)
        }
    }

    private fun scrollToPercentSmooth(targetPercent: Float) {
        val rv = recyclerRef?.get() ?: return

        // Always perform immediate pixel-based scroll (no smooth animation)
        val scrollRange = rv.computeVerticalScrollRange() - rv.computeVerticalScrollExtent()
        if (scrollRange <= 0) {
            val position = percentToAdapterPosition(targetPercent)
            performScrollToPosition(position, directPositioning = true)
            return
        }

        val currentOffset = rv.computeVerticalScrollOffset()
        val targetOffset = (scrollRange * targetPercent.coerceIn(0f, 1f)).toInt()
        val deltaY = targetOffset - currentOffset
        if (abs(deltaY) > 0) {
            rv.scrollBy(0, deltaY)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rv = recyclerRef?.get()
        val adapterCount = rv?.adapter?.itemCount ?: 0
        val adapterEmpty = adapterCount <= 0
        if (adapterEmpty && !enabledWhileEmpty) return
        if (alpha <= 0f) return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val custom = handleDrawable != null
        if (custom) {
            drawCustomHandle(canvas, w, h)
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
            val shrinkFactor = 1f - 0.15f * (abs(i - (ridgeCount - 1) / 2f))
            val endX = startX + ridgeMaxLength * shrinkFactor
            canvas.drawLine(startX, ry, min(endX, endBase), ry, ridgePaint)
        }
    }

    private fun drawCustomHandle(canvas: Canvas, w: Float, h: Float) {
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

    private fun scheduleDelayedFullBind() {
        handler.removeCallbacks(delayedFullBindRunnable)
        handler.postDelayed(delayedFullBindRunnable, delayedFullBindDelay)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rv = recyclerRef?.get()
        val adapterCount = rv?.adapter?.itemCount ?: 0
        val adapterEmpty = adapterCount <= 0
        if (adapterEmpty && !enabledWhileEmpty) return false

        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (hitTest(event.x, event.y, adapterCount)) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    dragging = true

                    // Detach any SnapHelper to prevent auto-snapping while dragging
                    if (detachedSnapHelper == null) {
                        val snap = rv?.onFlingListener
                        if (snap is SnapHelper) {
                            try {
                                snap.attachToRecyclerView(null)
                                detachedSnapHelper = snap
                            } catch (_: Exception) { /* ignore */
                            }
                        }
                    }

                    // Immediately stop any ongoing smooth scrolls
                    rv?.stopScroll()
                    val layoutManager = rv?.layoutManager as? LinearLayoutManager
                    layoutManager?.let { lm ->
                        // Cancel any pending smooth scroll operations
                        try {
                            lm.startSmoothScroll(null)
                        } catch (_: Exception) { /* ignore */
                        }
                    }

                    enterLightBindMode() // Enable light binding during drag
                    show(true)
                    removeCallbacks(autoHideRunnable)
                    updatePercentFromTouch(event.y, adapterCount)
                    // Schedule full bind if user pauses while still holding
                    scheduleDelayedFullBind()
                    invalidate()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    updatePercentFromTouch(event.y, adapterCount)
                    // If we previously exited, re-enter light bind on movement
                    if (!lightBindMode && !lightBindExitPending) {
                        enterLightBindMode()
                    }
                    // Reschedule delayed full bind on continued movement
                    scheduleDelayedFullBind()
                    invalidate()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)

                    // Cancel ALL pending scroll operations immediately
                    cancelAllPendingScrolls()

                    // Final precise snap - use direct positioning (no smooth scroll)
                    val finalPosition = percentToAdapterPosition(percent)
                    performScrollToPosition(finalPosition, directPositioning = true)

                    // Do NOT reattach SnapHelper to avoid any auto-snapping/scrolling after release
                    detachedSnapHelper = null

                    // Cancel any pending delayed full-bind and exit immediately
                    handler.removeCallbacks(delayedFullBindRunnable)
                    // Immediately exit light bind mode and notify all visible holders
                    exitLightBindModeImmediate()

                    scheduleAutoHide()
                    invalidate()
                    performClick()
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
        val h = height.toFloat()
        if (h <= 0f) return

        val custom = handleDrawable != null
        val newPercent = if (custom) {
            val inactive = handleDrawable ?: return
            val intrinsicH = if (useIntrinsicSize && inactive.intrinsicHeight > 0) {
                inactive.intrinsicHeight.toFloat()
            } else dp(56f)
            val available = (h - intrinsicH).coerceAtLeast(1f)
            val clampedTop = y - intrinsicH / 2f
            val clamped = clampedTop.coerceIn(0f, h - intrinsicH)
            (clamped / available).coerceIn(0f, 1f)
        } else {
            val radius = computeRadius(adapterCount)
            val available = (h - radius * 2f).coerceAtLeast(1f)
            val clampedY = y.coerceIn(radius, h - radius)
            (clampedY - radius) / available
        }

        // Use much smaller threshold during dragging for smoother updates
        val threshold = if (dragging) 0.001f else (if (updateThrottleMs <= 8L) 0.003f else 0.005f)
        if (abs(newPercent - percent) > threshold) {
            percent = newPercent

            // Always use immediate pixel-based scrolling - NO DEFERRED OPERATIONS
            val rv = recyclerRef?.get() ?: return
            val scrollRange = rv.computeVerticalScrollRange() - rv.computeVerticalScrollExtent()
            if (scrollRange > 0) {
                val currentOffset = rv.computeVerticalScrollOffset()
                val targetOffset = (scrollRange * newPercent.coerceIn(0f, 1f)).toInt()
                val deltaY = targetOffset - currentOffset
                if (abs(deltaY) > 0) {
                    rv.scrollBy(0, deltaY)
                    // Ensure there's no residual fling or settling from programmatic scroll
                    rv.stopScroll()
                }
            } else {
                // Direct position scrolling for edge cases
                val position = percentToAdapterPosition(newPercent)
                rv.scrollToPosition(position)
                rv.stopScroll()
            }
        }
    }

    private fun dp(value: Float): Float {
        val metrics: DisplayMetrics = resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics)
    }

    private fun computeRadius(adapterCount: Int): Float = when {
        adapterCount <= 0 -> handleRadius
        adapterCount <= 20 -> maxRadius
        adapterCount >= 400 -> minRadius
        else -> minRadius + (maxRadius - minRadius) * (1f - (adapterCount - 20f) / 380f)
    }

    /** Provide a custom drawable resource for the handle (used for both active & inactive). */
    @Suppress("unused")
    fun setHandleDrawable(@DrawableRes resId: Int, useIntrinsic: Boolean = true) {
        val d = AppCompatResources.getDrawable(context, resId)
        handleDrawable = d
        handleDrawableActive = null
        useIntrinsicSize = useIntrinsic
        invalidate()
    }

    /** Provide separate inactive and active drawables. */
    @Suppress("unused")
    fun setHandleDrawables(@DrawableRes inactiveResId: Int, @DrawableRes activeResId: Int, useIntrinsic: Boolean = true) {
        handleDrawable = AppCompatResources.getDrawable(context, inactiveResId)
        handleDrawableActive = AppCompatResources.getDrawable(context, activeResId)
        useIntrinsicSize = useIntrinsic
        invalidate()
    }

    /** Provide a custom drawable instance. */
    @Suppress("unused")
    fun setHandleDrawable(drawable: Drawable?, drawableActive: Drawable? = null, useIntrinsic: Boolean = true) {
        handleDrawable = drawable
        handleDrawableActive = drawableActive
        useIntrinsicSize = useIntrinsic
        invalidate()
    }

    /** Remove any custom drawable and revert to internal rendering. */
    @Suppress("unused")
    fun clearHandleDrawable() {
        handleDrawable = null
        handleDrawableActive = null
        invalidate()
    }

    override fun onAccentChanged(accent: Accent) {
        super.onAccentChanged(accent)
        handleDrawable?.setTint(accent.secondaryAccentColor)
        handleDrawableActive?.setTint(accent.primaryAccentColor)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ThemeManager.addListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        visibilityAnimator?.cancel()
        visibilityAnimator = null
        removeCallbacks(autoHideRunnable)
        removeCallbacks(batchedScrollRunnable)
        removeCallbacks(batchedPercentRunnable)
        // Cancel delayed full-bind callback as well
        handler.removeCallbacks(delayedFullBindRunnable)
        cancelAllPendingScrolls() // Ensure all scroll operations are cancelled
        val rv = recyclerRef?.get()

        // Do not reattach any previously detached SnapHelper to avoid future auto-snapping
        detachedSnapHelper = null

        rv?.removeOnScrollListener(scrollListener)
        ThemeManager.removeListener(this)
    }

    // Interface for adapters to optimize binding during fast scroll
    interface FastScrollOptimizedAdapter {
        fun setLightBindMode(enabled: Boolean)
    }

    // Enhanced interface that provides more control over binding during fast scroll
    interface FastScrollBindingController {
        /**
         * Called when light bind mode is enabled/disabled
         * @param enabled true when fast scrolling starts, false when it ends
         */
        fun setLightBindMode(enabled: Boolean)

        /**
         * Called during fast scrolling to allow custom binding logic
         * @param holder The ViewHolder being bound
         * @param position The adapter position
         * @param isLightBind true if this is during fast scrolling (light bind), false for normal bind
         */
        fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, isLightBind: Boolean)

        /**
         * Called to check if the adapter wants to handle binding itself during fast scroll
         * @return true if adapter will handle binding via onBindViewHolder callback, false to use default behavior
         */
        fun shouldHandleCustomBinding(): Boolean
    }

    companion object {
        fun attach(recyclerView: RecyclerView): SlideFastScroller {
            val scroller = SlideFastScroller(recyclerView.context)
            scroller.attachTo(recyclerView)
            scroller.setHandleDrawable(R.drawable.ic_scroll_thumb)
            scroller.handleDrawable?.setTint(ThemeManager.accent.primaryAccentColor)
            scroller.handleDrawableActive?.setTint(ThemeManager.accent.secondaryAccentColor)
            return scroller
        }
    }

    private fun getOptimalUpdateInterval(): Long {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // API 30+ - Use Display.getRefreshRate()
                val display = context.display
                val refreshRate = display.refreshRate
                when {
                    refreshRate >= 120f -> 8L  // 120Hz = ~8.33ms interval
                    refreshRate >= 90f -> 11L  // 90Hz = ~11.1ms interval
                    refreshRate >= 75f -> 13L  // 75Hz = ~13.3ms interval
                    else -> 16L                // 60Hz = ~16.7ms interval
                }
            } else {
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

                @Suppress("DEPRECATION")
                val display = windowManager?.defaultDisplay
                val refreshRate = display?.refreshRate ?: 60f
                when {
                    refreshRate >= 120f -> 8L
                    refreshRate >= 90f -> 11L
                    refreshRate >= 75f -> 13L
                    else -> 16L
                }
            }
        } catch (e: Exception) {
            Log.e("SlideFastScroller", "Failed to get display refresh rate, defaulting to 60Hz", e)
            16L // Fallback to 60Hz if detection fails
        }
    }

    private fun cancelAllPendingScrolls() {
        // Cancel all pending scroll operations
        pendingScrollPosition = -1
        pendingScrollUpdate?.let { handler.removeCallbacks(it) }
        pendingScrollUpdate = null

        pendingPercentUpdate?.let { handler.removeCallbacks(it) }
        pendingPercentUpdate = null

        // Stop RecyclerView scrolling
        val rv = recyclerRef?.get()
        rv?.stopScroll()

        // Cancel any layout manager smooth scrolls
        val layoutManager = rv?.layoutManager as? LinearLayoutManager
        layoutManager?.let { lm ->
            try {
                lm.startSmoothScroll(null)
            } catch (_: Exception) {
                // Ignore - just trying to cancel
            }
        }
    }
}
