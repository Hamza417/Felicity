package app.simple.felicity.decorations.fastscroll

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import app.simple.felicity.decorations.itemdecorations.FooterSpacingItemDecoration
import app.simple.felicity.decorations.itemdecorations.HeaderSpacingItemDecoration
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.floor

// High-performance fast scroller with adapter index mapping, throttling, and prefetch optimization
class SlideFastScroller @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr), ThemeChangedListener {

    private var recyclerRef: WeakReference<RecyclerView>? = null

    // Handle (pill) configuration
    private val handleRadius = dp(28f)
    private val touchExtra = dp(12f)

    /** Half-width of the fader-style pill thumb, matching the equalizer slider design. */
    private val thumbHalfWidthPx = dp(12f)

    /** Half-height of the fader-style pill thumb, matching the equalizer slider design. */
    private val thumbHalfHeightPx = dp(24f)

    /** Corner radius that produces a fully rounded pill shape (equals [thumbHalfHeightPx]). */
    private val thumbCornerRadiusPx = thumbHalfHeightPx

    /** Stroke width of the ring drawn around the pill thumb. */
    private val thumbRingStrokePx = dp(3f)

    /** Right-side margin so the pill never presses against the screen edge. */
    private val thumbMarginRightPx = dp(8f)

    /** Fraction of [thumbHalfWidthPx] used as the half-length of each horizontal grip line. */
    private val gripLineHalfLengthFraction = 0.42f

    /** Fraction of [thumbHalfHeightPx] used as the spacing between each horizontal grip line. */
    private val gripLineSpacingFraction = 0.22f

    /** Shadow/glow radius when the thumb is in its resting (idle) state. */
    private val glowRadiusIdle = dp(4f)

    /** Shadow/glow radius when the thumb is being actively dragged. */
    private val glowRadiusActive = dp(10f)

    /**
     * Animated interaction state for the thumb.
     * 0.0 = fully idle, 1.0 = fully dragging.
     * Drives the shadow glow radius and alpha interpolation each frame.
     */
    private var thumbState = 0f
    private var thumbStateAnimator: ValueAnimator? = null

    /**
     * Fill paint for the pill body. Color is always the current accent; the shadow layer
     * is rebuilt each frame in [onDraw] so the glow radius tracks [thumbState] smoothly.
     */
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemeManager.accent.primaryAccentColor
        style = Paint.Style.FILL
    }

    /**
     * Stroke ring drawn around the pill. Uses the theme's background color to create
     * a subtle recessed-border effect against the accent fill.
     */
    private val thumbRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ThemeManager.theme.viewGroupTheme.backgroundColor
        strokeWidth = dp(3f)
    }

    /** Horizontal grip lines drawn on the pill thumb, matching the equalizer slider design. */
    private val ridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        alpha = 130
        strokeWidth = dp(1.5f)
        strokeCap = Paint.Cap.ROUND
    }

    // Reusable rect for the pill thumb to avoid per-frame allocations.
    private val thumbPillRect = RectF()

    // Custom drawable support
    private var handleDrawable: Drawable? = null
    private var handleDrawableActive: Drawable? = null
    private var useIntrinsicSize = true

    /**
     * Vertical inset applied to the top of the scrollable track, in pixels.
     * Use this to prevent the thumb from overlapping the status bar, a floating header,
     * or any [app.simple.felicity.decorations.itemdecorations.HeaderSpacingItemDecoration]
     * whose height you want to mirror here.
     *
     * When a [HeaderSpacingItemDecoration] is detected on the attached [RecyclerView] the value
     * is kept in sync automatically via a [ViewTreeObserver.OnGlobalLayoutListener], so there is
     * no need to call [setTrackTopPadding] manually in that case.
     */
    private var topPaddingPx = 0f

    /**
     * Vertical inset applied to the bottom of the scrollable track, in pixels.
     * Use this to prevent the thumb from overlapping a mini-player, a floating footer,
     * or any [app.simple.felicity.decorations.itemdecorations.FooterSpacingItemDecoration]
     * whose height you want to mirror here.
     *
     * When a [FooterSpacingItemDecoration] is detected on the attached [RecyclerView] the value
     * is kept in sync automatically via a [ViewTreeObserver.OnGlobalLayoutListener], so there is
     * no need to call [setTrackBottomPadding] manually in that case.
     */
    private var bottomPaddingPx = 0f

    /**
     * Listener registered on the attached [RecyclerView]'s [ViewTreeObserver] so that decoration
     * heights (header, footer) are re-read after every layout pass in the activity window.
     * This covers all deferred [View.post] chains that settle asynchronously — e.g. the status-bar
     * inset applied by [app.simple.felicity.decorations.views.AppHeader].
     */
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

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
    private var fadeToIdleMode = true  // Fade to 40 % alpha rather than hiding completely.
    private var idleAlpha = 0.4f        // Dim level when the user is not interacting.
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

    // Periodic position updates during light binding
    private val lightBindUpdateInterval = 30L // Update positions every 30ms during drag (~33fps)
    private val lightBindUpdateRunnable: Runnable = object : Runnable {
        override fun run() {
            if (dragging && lightBindMode) {
                updateVisibleItemPositions()
                handler.postDelayed(this, lightBindUpdateInterval)
            }
        }
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
        // Hardware layer lets Paint.setShadowLayer composite the glow on the HWUI pipeline
        // (API 28+) without falling back to software rendering.
        setLayerType(LAYER_TYPE_HARDWARE, null)
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
                    ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }

        // Register a global-layout listener so track padding is re-synced from
        // HeaderSpacingItemDecoration / FooterSpacingItemDecoration after every layout
        // pass in the activity window.  This captures all deferred post { } chains
        // (e.g. the status-bar inset applied by AppHeader) without requiring the caller
        // to know when those chains have settled.
        globalLayoutListener?.let { old ->
            try {
                recyclerView.viewTreeObserver.removeOnGlobalLayoutListener(old)
            } catch (_: Exception) {
            }
        }
        val listener = ViewTreeObserver.OnGlobalLayoutListener { syncPaddingFromDecorations() }
        globalLayoutListener = listener
        recyclerView.viewTreeObserver.addOnGlobalLayoutListener(listener)

        post {
            syncPaddingFromDecorations()
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

    /**
     * Scans the attached [RecyclerView]'s item-decoration list for a
     * [HeaderSpacingItemDecoration] and a [FooterSpacingItemDecoration] and writes their current
     * heights into [topPaddingPx] and [bottomPaddingPx] respectively.
     *
     * Called on every [ViewTreeObserver.OnGlobalLayoutListener] callback so the track bounds stay
     * accurate even when decoration heights change asynchronously (e.g. after the status-bar inset
     * is applied by [app.simple.felicity.decorations.views.AppHeader] via a deferred
     * [android.view.View.post] chain).  [invalidate] is only called when a value actually changes,
     * so there is no unnecessary redraw overhead.
     */
    private fun syncPaddingFromDecorations() {
        val rv = recyclerRef?.get() ?: return

        // should be thumb_height / 4 so that we keep a padding between header/footer
        val extraPadding = thumbHalfHeightPx / 4f

        var newTop = topPaddingPx + extraPadding
        var newBottom = bottomPaddingPx + extraPadding

        for (i in 0 until rv.itemDecorationCount) {
            when (val dec = rv.getItemDecorationAt(i)) {
                is HeaderSpacingItemDecoration -> newTop = dec.headerHeight.toFloat()
                is FooterSpacingItemDecoration -> newBottom = dec.footerHeight.toFloat()
            }
        }

        if (newTop != topPaddingPx || newBottom != bottomPaddingPx) {
            topPaddingPx = newTop + extraPadding
            bottomPaddingPx = newBottom + extraPadding
            invalidate()
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

        // Start periodic position updates
        handler.removeCallbacks(lightBindUpdateRunnable)
        handler.postDelayed(lightBindUpdateRunnable, lightBindUpdateInterval)
    }

    /**
     * Update positions of visible items during light binding to show correct data
     */
    private fun updateVisibleItemPositions() {
        val rv = recyclerRef?.get() ?: return
        val adapter = rv.adapter ?: return
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return

        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        if (firstVisible < 0 || lastVisible < 0) return

        // Update positions of currently visible items
        when (adapter) {
            is FastScrollBindingController -> {
                if (adapter.shouldHandleCustomBinding()) {
                    // Use custom light binding to update positions only
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
            is FastScrollOptimizedAdapter -> {
                // Notify change with payload to trigger position-only updates
                adapter.notifyItemRangeChanged(
                        firstVisible, lastVisible - firstVisible + 1, "position_update")
            }
        }
    }

    private fun exitLightBindMode() {
        if (!lightBindMode || lightBindExitPending) return
        lightBindMode = false
        lightBindExitPending = true
        // Cancel any pending delayed full-bind trigger and periodic updates
        handler.removeCallbacks(delayedFullBindRunnable)
        handler.removeCallbacks(lightBindUpdateRunnable)

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
                        // Use custom binding to restore full content for visible items
                        for (position in firstVisible..lastVisible) {
                            val view = layoutManager.findViewByPosition(position)
                            if (view != null) {
                                val holder = rv.getChildViewHolder(view)
                                if (holder != null) {
                                    adapter.onBindViewHolder(holder, position, false)
                                }
                            }
                        }
                    }
                }
                // Invalidate entire dataset so off-screen items are also refreshed
                val itemCount = adapter.itemCount
                if (itemCount > 0) {
                    adapter.notifyItemRangeChanged(0, itemCount)
                }
            }
            adapter is FastScrollOptimizedAdapter -> {
                adapter.setLightBindMode(false)
                // Invalidate entire dataset so off-screen items are also refreshed
                val itemCount = adapter.itemCount
                if (itemCount > 0) {
                    adapter.notifyItemRangeChanged(0, itemCount)
                }
            }
            else -> {
                // For non-optimized adapters, invalidate the entire dataset
                val itemCount = adapter.itemCount
                if (itemCount > 0) {
                    adapter.notifyItemRangeChanged(0, itemCount)
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

        // First, notify adapter that light bind mode is off
        when (adapter) {
            is FastScrollBindingController -> adapter.setLightBindMode(false)
            is FastScrollOptimizedAdapter -> adapter.setLightBindMode(false)
        }

        // Wait for RecyclerView to settle before rebinding
        rv.post {
            // Re-check state in case things changed
            if (lightBindExitPending) {
                forceRebindAllItems(rv, layoutManager, adapter)
                lightBindExitPending = false
            }
        }
    }

    /**
     * Forces a full rebind of ALL adapter items after fast scroll ends.
     * This ensures that items scrolled past in light-bind mode (both above and below
     * the current viewport) are properly refreshed when they come back into view.
     * Visible items are rebound immediately; off-screen items are invalidated via
     * notifyItemRangeChanged so RecyclerView will rebind them on next layout pass.
     */
    private fun forceRebindAllItems(
            rv: RecyclerView,
            layoutManager: LinearLayoutManager,
            adapter: RecyclerView.Adapter<*>
    ) {
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        val itemCount = adapter.itemCount
        if (itemCount <= 0) return

        when (adapter) {
            is FastScrollBindingController -> {
                if (adapter.shouldHandleCustomBinding()) {
                    // Immediately rebind currently visible items with full data
                    if (firstVisible >= 0 && lastVisible >= 0) {
                        for (position in firstVisible..lastVisible) {
                            val view = layoutManager.findViewByPosition(position)
                            if (view != null) {
                                val holder = rv.getChildViewHolder(view)
                                if (holder != null) {
                                    adapter.onBindViewHolder(holder, position, false)
                                }
                            }
                        }
                    }
                }
                // Invalidate the entire dataset so off-screen items get rebound on scroll
                adapter.notifyItemRangeChanged(0, itemCount)
            }
            else -> {
                // Invalidate the entire dataset — RecyclerView will rebind visible items
                // immediately and off-screen items when they scroll back into view
                adapter.notifyItemRangeChanged(0, itemCount)
            }
        }
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

    /**
     * Manually overrides the top inset of the scrollable track in pixels so the thumb never
     * travels above this boundary.
     *
     * In most cases you do **not** need to call this method.  When the attached [RecyclerView]
     * carries a [app.simple.felicity.decorations.itemdecorations.HeaderSpacingItemDecoration] the
     * scroller reads its height automatically after every layout pass and the value set here will
     * be superseded on the next global-layout callback.  Only use this method when no
     * [app.simple.felicity.decorations.itemdecorations.HeaderSpacingItemDecoration] is present
     * and you want a fixed inset (e.g. a status-bar-only offset on a screen that has no header).
     *
     * @param px Top inset in pixels (must be >= 0).
     */
    @Suppress("unused")
    fun setTrackTopPadding(px: Int) {
        val clamped = px.coerceAtLeast(0).toFloat()
        if (topPaddingPx != clamped) {
            topPaddingPx = clamped
            invalidate()
        }
    }

    /**
     * Manually overrides the bottom inset of the scrollable track in pixels so the thumb never
     * travels below this boundary.
     *
     * In most cases you do **not** need to call this method.  When the attached [RecyclerView]
     * carries a [app.simple.felicity.decorations.itemdecorations.FooterSpacingItemDecoration] the
     * scroller reads its height automatically after every layout pass and the value set here will
     * be superseded on the next global-layout callback.  Only use this method when no
     * [app.simple.felicity.decorations.itemdecorations.FooterSpacingItemDecoration] is present
     * and you want a fixed inset (e.g. a static bottom-nav bar that has no spacing decoration).
     *
     * @param px Bottom inset in pixels (must be >= 0).
     */
    @Suppress("unused")
    fun setTrackBottomPadding(px: Int) {
        val clamped = px.coerceAtLeast(0).toFloat()
        if (bottomPaddingPx != clamped) {
            bottomPaddingPx = clamped
            invalidate()
        }
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

        if (!animated) {
            alpha = 1f
            translationX = 0f
            if (wasIdle && fadeToIdleMode) {
                handleDrawable?.setTint(ThemeManager.accent.primaryAccentColor)
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
     * Fades the scroller to its idle alpha (40 %) when the user is not interacting.
     * If [fadeToIdleMode] is disabled the scroller hides completely instead.
     *
     * @param animated Whether to animate the transition.
     */
    private fun fadeToIdle(animated: Boolean) {
        if (!fadeToIdleMode) {
            hide(animated)
            return
        }

        // Guard: do not re-animate when already in the idle state.
        if (isIdle) return
        isIdle = true

        visibilityAnimator?.cancel()

        if (!animated) {
            alpha = idleAlpha
            handleDrawable?.setTint(ThemeManager.accent.primaryAccentColor)
            invalidate()
        } else {
            val startAlpha = alpha
            visibilityAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 260L
                interpolator = DecelerateInterpolator()
                addUpdateListener { va ->
                    val f = va.animatedFraction
                    alpha = startAlpha + (idleAlpha - startAlpha) * f
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

        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = layoutManager.findFirstVisibleItemPosition()

        if (firstVisible == RecyclerView.NO_POSITION || firstVisible < 0) return

        val firstView = layoutManager.findViewByPosition(firstVisible)

        val newPercent = if (firstView != null) {
            val viewTop = firstView.top
            val itemHeight = firstView.height

            // Fallback for edge cases where view exists but layout pass hasn't assigned height
            if (itemHeight <= 0) {
                firstVisible.toFloat() / (count - 1).toFloat()
            } else {
                val offsetPercent = (-viewTop.toFloat() / itemHeight.toFloat()).coerceIn(0f, 1f)
                ((firstVisible + offsetPercent) / (count - 1).toFloat()).coerceIn(0f, 1f)
            }
        } else {
            // Fallback for edge cases where layout manager cannot find the view
            firstVisible.toFloat() / (count - 1).toFloat()
        }

        // Visual state and invalidation must happen continuously for smooth tracking.
        percent = newPercent
        currentAdapterPosition = firstVisible
        invalidate()
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

        val accentColor = ThemeManager.accent.primaryAccentColor
        val r = Color.red(accentColor)
        val g = Color.green(accentColor)
        val b = Color.blue(accentColor)

        // Interpolate glow radius and alpha based on the animated thumb state (0 = idle, 1 = dragging).
        val glowRadius = glowRadiusIdle + (glowRadiusActive - glowRadiusIdle) * thumbState
        val glowAlpha = (80 + (120f * thumbState).toInt()).coerceIn(0, 200)
        val glowColor = Color.argb(glowAlpha, r, g, b)

        val halfH = thumbHalfHeightPx
        val halfW = thumbHalfWidthPx
        val cornerRadius = thumbCornerRadiusPx

        val trackTop = topPaddingPx + halfH
        val trackBottom = h - bottomPaddingPx - halfH
        val centerY = trackTop + (trackBottom - trackTop) * percent
        val clampedCY = centerY.coerceIn(trackTop, trackBottom)

        // Keep a comfortable margin from the right edge of the screen.
        val cx = w - halfW - thumbMarginRightPx

        // Apply the animated glow; accent color is always used for the fill.
        handlePaint.color = accentColor
        handlePaint.setShadowLayer(glowRadius, 0f, 0f, glowColor)

        // Draw the filled pill body.
        thumbPillRect.set(cx - halfW, clampedCY - halfH, cx + halfW, clampedCY + halfH)
        canvas.drawRoundRect(thumbPillRect, cornerRadius, cornerRadius, handlePaint)

        // Draw the background-colored ring around the pill.
        val ringInset = thumbRingStrokePx / 2f
        thumbPillRect.inset(ringInset, ringInset)
        canvas.drawRoundRect(
                thumbPillRect,
                cornerRadius - ringInset,
                cornerRadius - ringInset,
                thumbRingPaint
        )
        thumbPillRect.inset(-ringInset, -ringInset)

        // Draw the three horizontal grip lines centered on the pill.
        val gripHalfLen = halfW * gripLineHalfLengthFraction
        val gripSpacing = halfH * gripLineSpacingFraction
        for (i in -1..1) {
            val lineY = clampedCY + i * gripSpacing
            canvas.drawLine(cx - gripHalfLen, lineY, cx + gripHalfLen, lineY, ridgePaint)
        }
    }

    private fun drawCustomHandle(canvas: Canvas, w: Float, h: Float) {
        val inactive = handleDrawable ?: return
        val active = handleDrawableActive
        val drawable = if (dragging) (active ?: inactive) else inactive
        val intrinsicW = if (useIntrinsicSize && drawable.intrinsicWidth > 0) drawable.intrinsicWidth else dp(56f).toInt()
        val intrinsicH = if (useIntrinsicSize && drawable.intrinsicHeight > 0) drawable.intrinsicHeight else dp(56f).toInt()
        val trackTop = topPaddingPx
        val trackBottom = h - bottomPaddingPx
        val available = (trackBottom - trackTop - intrinsicH).coerceAtLeast(1f)
        val top = (trackTop + percent * available).coerceIn(trackTop, trackBottom - intrinsicH)
        val left = w - intrinsicW // flush to right edge
        drawable.setBounds(left.toInt(), top.toInt(), w.toInt(), (top + intrinsicH).toInt())
        drawable.draw(canvas)
    }

    private fun scheduleDelayedFullBind() {
        handler.removeCallbacks(delayedFullBindRunnable)
        // Reduced delay for quicker updates during scroll pauses
        handler.postDelayed(delayedFullBindRunnable, 80L)
    }

    /**
     * Smoothly transitions the thumb between its idle (0.0) and dragging (1.0) states.
     * The animation drives [thumbState], which controls the shadow glow radius rendered
     * each frame in [onDraw]. Pressing uses a snappier duration; releasing eases out gently.
     *
     * @param to Target state: 0.0 for idle, 1.0 for dragging.
     */
    private fun animateThumbState(to: Float) {
        thumbStateAnimator?.cancel()
        if (thumbState == to) return
        thumbStateAnimator = ValueAnimator.ofFloat(thumbState, to).apply {
            duration = if (to >= 0.5f) 150L else 320L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                thumbState = it.animatedValue as Float
                invalidate()
            }
            start()
        }
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
                    animateThumbState(1f)
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
                    animateThumbState(0f)

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
            val trackTop = topPaddingPx
            val trackBottom = height.toFloat() - bottomPaddingPx
            val available = (trackBottom - trackTop - intrinsicH).coerceAtLeast(1f)
            val top = (trackTop + percent * available).coerceIn(trackTop, trackBottom - intrinsicH)
            val rect = RectF(w - intrinsicW - touchExtra, top - touchExtra, w + touchExtra, top + intrinsicH + touchExtra)
            return rect.contains(x, y)
        }
        val halfH = thumbHalfHeightPx
        val halfW = thumbHalfWidthPx
        val trackTop = topPaddingPx + halfH
        val trackBottom = height.toFloat() - bottomPaddingPx - halfH
        val centerY = trackTop + (trackBottom - trackTop) * percent
        val cy = centerY.coerceIn(trackTop, trackBottom)
        val cx = w - halfW - thumbMarginRightPx
        val rect = RectF(cx - halfW - touchExtra, cy - halfH - touchExtra, cx + halfW + touchExtra, cy + halfH + touchExtra)
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
            val trackTop = topPaddingPx
            val trackBottom = h - bottomPaddingPx
            val available = (trackBottom - trackTop - intrinsicH).coerceAtLeast(1f)
            val clampedTop = (y - intrinsicH / 2f - trackTop).coerceIn(0f, available)
            (clampedTop / available).coerceIn(0f, 1f)
        } else {
            val halfH = thumbHalfHeightPx
            val trackTop = topPaddingPx + halfH
            val trackBottom = h - bottomPaddingPx - halfH
            val available = (trackBottom - trackTop).coerceAtLeast(1f)
            val clampedY = y.coerceIn(trackTop, trackBottom)
            (clampedY - trackTop) / available
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

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        super.onThemeChanged(theme, animate)
        // Keep the ring color in sync with the current theme background.
        thumbRingPaint.color = theme.viewGroupTheme.backgroundColor
        invalidate()
    }

    override fun onAccentChanged(accent: Accent) {
        super.onAccentChanged(accent)
        handleDrawable?.setTint(accent.secondaryAccentColor)
        handleDrawableActive?.setTint(accent.primaryAccentColor)
        // Repaint so the new accent color and glow are applied immediately.
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ThemeManager.addListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        visibilityAnimator?.cancel()
        visibilityAnimator = null
        thumbStateAnimator?.cancel()
        thumbStateAnimator = null
        removeCallbacks(autoHideRunnable)
        removeCallbacks(batchedScrollRunnable)
        removeCallbacks(batchedPercentRunnable)
        // Cancel delayed full-bind callback as well
        handler.removeCallbacks(delayedFullBindRunnable)
        cancelAllPendingScrolls() // Ensure all scroll operations are cancelled
        val rv = recyclerRef?.get()

        // Unregister the decoration-sync listener to avoid leaking a reference to this view.
        globalLayoutListener?.let { listener ->
            try {
                rv?.viewTreeObserver?.removeOnGlobalLayoutListener(listener)
            } catch (_: Exception) { /* ViewTreeObserver may already be detached — safe to ignore */
            }
        }
        globalLayoutListener = null

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
