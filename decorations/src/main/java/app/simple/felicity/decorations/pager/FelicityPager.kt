package app.simple.felicity.decorations.pager

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import app.simple.felicity.decorations.pager.FelicityPager.Companion.SCROLL_STATE_DRAGGING
import app.simple.felicity.decorations.pager.FelicityPager.Companion.SCROLL_STATE_IDLE
import app.simple.felicity.decorations.pager.FelicityPager.Companion.SCROLL_STATE_SETTLING
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A raw horizontal pager [ViewGroup] that is intentionally free of any image-loading
 * or Glide dependency. It is a pure scroll / layout / touch engine:
 *
 * - Pages are arbitrary [View]s produced and recycled entirely by [PageAdapter].
 * - Positions are driven by `translationX` on each child view.
 * - All drag, fling, snap, and auto-slide logic lives here.
 *
 * To display images, use an [ImagePageAdapter] (a ready-made subclass that accepts an
 * `ImageBitmapProvider` + `ImageBitmapCanceller` pair), or write your own [PageAdapter].
 *
 * **Scroll model:** Page N is centred when `scrollPx == N * width`.
 *
 * **Drag:** `ACTION_MOVE` shifts `scrollPx` continuously; bounds-clamped to
 * `[0, (count-1) * width]`.
 *
 * **Fling:** velocity → pages-to-advance (`vPagesPerSec × windowSec`, capped at 3).
 *
 * **Slow release:** advance if `|drag| > advanceThreshold (0.25) × width`, else snap back.
 *
 * **Settlement:** [Choreographer] + `easeOutCubic`; start-time latched on the first vsync
 * frame to avoid uptime/vsync clock-source jitter.
 *
 * **[OnPageChangeListener]:** `DRAGGING → SETTLING → IDLE`; [OnPageChangeListener.onPageScrolled]
 * fires every frame; [OnPageChangeListener.onPageSelected] fires only after a settle completes
 * (or immediately for instant jumps). The `fromUser` overload distinguishes user swipes from
 * programmatic [setCurrentItem].
 *
 * **Auto-slide:** [startAutoSlide] / [stopAutoSlide].
 *
 * @author Hamza417
 */
class FelicityPager @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : ViewGroup(context, attrs), GestureDetector.OnGestureListener {

    /**
     * Base adapter for [FelicityPager]. Implement this directly for fully custom pages,
     * or use `ImagePageAdapter` for the common image-display use-case.
     */
    interface PageAdapter {
        /** Returns the total number of pages. */
        fun getCount(): Int

        /**
         * Returns a stable, unique id for [position].
         * Used to avoid re-binding a view that already shows the right content.
         * Defaults to [position] as [Long].
         */
        fun getItemId(position: Int): Long = position.toLong()

        /**
         * Creates a brand-new page view for [position].
         * **Do not** add it to any parent — [FelicityPager] will do that.
         */
        fun onCreateView(position: Int, parent: ViewGroup): View

        /**
         * Binds data for [position] into [view].
         * Called both when a view is freshly created and when a recycled view is re-used.
         */
        fun onBindView(position: Int, view: View)

        /**
         * Called just before [view] is removed from the window.
         * Release any async resources (cancel image loads, clear bitmaps, etc.).
         * The view is then placed in the recycle pool and may be re-bound later via [onBindView].
         */
        fun onRecycleView(position: Int, view: View)
    }

    /**
     * Listener for pager scroll events, page-selection changes, and scroll-state transitions.
     *
     * All methods have default no-op implementations so callers only need to override what
     * they care about. The [onPageSelected] overload with `fromUser` lets callers distinguish
     * between user-initiated swipes and programmatic [setCurrentItem] calls.
     */
    interface OnPageChangeListener {
        /** Called every frame while the pager is scrolling. */
        fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

        /** Called when a new page becomes the selected page. */
        fun onPageSelected(position: Int) {}

        /**
         * Called when a new page becomes selected, with a flag indicating whether the
         * change was triggered by the user (swipe/fling) or programmatically.
         * Backward-compatible — defaults to a no-op.
         */
        fun onPageSelected(position: Int, fromUser: Boolean) {}

        /** Called when the scroll state changes between [SCROLL_STATE_IDLE],
         *  [SCROLL_STATE_DRAGGING], and [SCROLL_STATE_SETTLING]. */
        fun onPageScrollStateChanged(state: Int) {}
    }

    /**
     * Listener for vertical drag gestures that originate on this [FelicityPager].
     *
     * When the user's dominant swipe direction is vertical (i.e., the Y displacement
     * exceeds the X displacement and crosses the touch-slop threshold) the pager
     * delegates the gesture to this listener instead of consuming it for horizontal
     * page-flipping. Typical use-case: swipe-down-to-close on a full-screen player.
     */
    interface OnVerticalDragListener {
        /**
         * Called once when the vertical drag gesture is first recognized.
         */
        fun onVerticalDragBegin() {}

        /**
         * Called continuously while the user is dragging vertically.
         *
         * @param totalDeltaY Total vertical displacement in pixels since [onVerticalDragBegin].
         *                    Positive values indicate a downward swipe.
         */
        fun onVerticalDrag(totalDeltaY: Float, event: MotionEvent) {}

        /**
         * Called when the drag gesture ends (finger lifted or gesture cancelled).
         *
         * @param totalDeltaY Total vertical displacement in pixels since [onVerticalDragBegin].
         * @param velocityY   Vertical fling velocity in pixels per second at the moment of release.
         */
        fun onVerticalDragEnd(totalDeltaY: Float, velocityY: Float, event: MotionEvent) {}
    }

    companion object {
        /** The pager is not being scrolled and no animation is running. */
        const val SCROLL_STATE_IDLE = 0

        /** The pager is currently being dragged by the user. */
        const val SCROLL_STATE_DRAGGING = 1

        /** The pager is animating toward a resting page position. */
        const val SCROLL_STATE_SETTLING = 2
    }

    /**
     * The set of [OnPageChangeListener]s currently registered on this pager.
     * Uses [CopyOnWriteArrayList] so listeners can be added/removed safely from callbacks.
     */
    private val pageChangeListeners = CopyOnWriteArrayList<OnPageChangeListener>()

    /** Registers [l] to receive page-scroll, page-selection, and state-change events. */
    fun addOnPageChangeListener(l: OnPageChangeListener) {
        pageChangeListeners.add(l)
    }

    /** Unregisters [l] so it no longer receives events. */
    fun removeOnPageChangeListener(l: OnPageChangeListener) {
        pageChangeListeners.remove(l)
    }

    /** Removes all registered [OnPageChangeListener]s at once. */
    fun clearOnPageChangeListeners() {
        pageChangeListeners.clear()
    }

    /**
     * The currently attached [PageAdapter], or `null` if none has been set.
     * Changing this value resets scroll position, cancels any running animation,
     * and recycles all active page views.
     */
    private var adapter: PageAdapter? = null

    /**
     * Attaches [adapter] to this pager, resetting scroll state and reloading all pages.
     * If the view has not yet been laid out (width == 0), the initial page load is deferred
     * until the first layout pass completes.
     */
    fun setAdapter(adapter: PageAdapter?) {
        // Clear the recycle pool so stale views from a previous adapter are not reused.
        recyclePool.clear()
        this.adapter = adapter
        cancelAnimation()
        scrollPx = 0f
        currentPage = -1   // reset so dispatchPageSelected(0) always fires
        recycleAllPages()
        if (width > 0) {
            ensurePages()
            applyTranslations()
            dispatchScrolled()
            dispatchPageSelected(0, fromUser = false)
            dispatchStateChanged(SCROLL_STATE_IDLE)
        } else {
            // Width is 0 — the view has not been laid out yet.
            // Defer the initial page load until the first layout pass completes.
            post {
                if (this.adapter === adapter && width > 0) {
                    ensurePages()
                    applyTranslations()
                    dispatchScrolled()
                    dispatchPageSelected(0, fromUser = false)
                    dispatchStateChanged(SCROLL_STATE_IDLE)
                }
            }
        }
    }

    /**
     * Notifies the pager that the underlying data set has changed.
     * All active pages are recycled and reloaded. If the current scroll position
     * is now beyond the new end of the list, it is clamped to the last valid page.
     */
    fun notifyDataSetChanged() {
        cancelAnimation()
        recycleAllPages()
        if (scrollPx > maxScrollPx()) scrollPx = maxScrollPx()
        if (width > 0) {
            ensurePages()
            applyTranslations()
        }
        dispatchScrolled()
    }

    /**
     * Duration in milliseconds used for programmatic smooth-scrolls triggered by
     * [setCurrentItem]. Values below 0 are clamped to 0 (instant jump).
     */
    var animationDurationMs: Long = 620L
        set(v) {
            field = v.coerceAtLeast(0L)
        }

    /**
     * Fraction of the page width that a drag must exceed in order to advance to the next
     * page on a slow (sub-fling) release. Default is 0.25 (25 % of page width).
     */
    private val advanceThreshold = 0.25f

    /** Minimum velocity (px/s, scaled for the display) required to trigger a fling. */
    private val minFlingVelocity =
        ViewConfiguration.get(context).scaledMinimumFlingVelocity * 1.35f

    /**
     * Number of pages to keep loaded on each side of the currently visible page.
     * A value of 1 loads the immediate neighbors; 2 loads two pages on each side, etc.
     */
    private val pageRadius = 2

    /**
     * Active pages currently attached to this [ViewGroup], keyed by adapter position.
     * Only pages within [pageRadius] of the current scroll position are kept here;
     * all others are recycled into [recyclePool].
     */
    private val activePages = HashMap<Int, View>()

    /**
     * A pool of detached views available for re-use. Views are placed here by [recyclePage]
     * and retrieved by [obtainView], avoiding repeated inflation for the same view type.
     */
    private val recyclePool = ArrayDeque<View>(8)

    /**
     * Returns a view for [position], either by rebinding a pooled view or by creating
     * a fresh one via [PageAdapter.onCreateView].
     */
    private fun obtainView(position: Int): View {
        val ad = adapter!!
        return recyclePool.removeLastOrNull()?.also { ad.onBindView(position, it) }
            ?: ad.onCreateView(position, this).also { ad.onBindView(position, it) }
    }

    /**
     * Recycles the active page at [position]: calls [PageAdapter.onRecycleView], moves the
     * view to [recyclePool], and removes it from this [ViewGroup].
     */
    private fun recyclePage(position: Int) {
        val v = activePages.remove(position) ?: return
        adapter?.onRecycleView(position, v)
        recyclePool.addLast(v)
        removeView(v)
    }

    /** Recycles every currently active page. */
    private fun recycleAllPages() {
        activePages.keys.toList().forEach { recyclePage(it) }
    }

    /**
     * Loads and attaches the page at [position] if it is not already active.
     * The view is immediately positioned via [applyTranslationTo] using the current
     * [width] so it lands in the correct place even before the next layout pass.
     */
    private fun loadPage(position: Int) {
        val ad = adapter ?: return
        if (position !in 0 until ad.getCount()) return
        if (activePages.containsKey(position)) return
        val v = obtainView(position)
        activePages[position] = v
        addView(v)
        // Measure and lay out the new child immediately so translationX is meaningful.
        if (width > 0 && height > 0) {
            val cw = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
            val ch = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            v.measure(cw, ch)
            v.layout(0, 0, width, height)
        }
        applyTranslationTo(v, position)
    }

    /**
     * Ensures that all pages within `[center - pageRadius, center + pageRadius]` are loaded
     * and that any pages outside that window are recycled.
     *
     * The center is the page index closest to the current [scrollPx] (see [scrollPageIndex]).
     */
    private fun ensurePages() {
        val count = adapter?.getCount() ?: return
        if (count == 0) return
        // Use the resolved current page rather than scrollPageIndex() when width is not
        // yet available, but guard against the sentinel value -1 used during adapter reset.
        val center = if (width > 0) scrollPageIndex() else currentPage.coerceAtLeast(0)
        val lo = max(0, center - pageRadius)
        val hi = minOf(count - 1, center + pageRadius)
        for (i in lo..hi) loadPage(i)
        activePages.keys.filter { it !in lo..hi }.forEach { recyclePage(it) }
    }

    /**
     * Continuous horizontal scroll position in pixels.
     * Page N is centred when `scrollPx == N * width`.
     */
    private var scrollPx = 0f

    /** The adapter position of the page most recently reported as selected. */
    private var currentPage = 0

    /** Current scroll state: one of [SCROLL_STATE_IDLE], [SCROLL_STATE_DRAGGING], [SCROLL_STATE_SETTLING]. */
    private var scrollState = SCROLL_STATE_IDLE

    /**
     * The current scroll state exposed as a read-only property.
     * Useful for callers that need to know whether the user is actively dragging
     * before pushing a programmatic position update.
     *
     * @see SCROLL_STATE_IDLE
     * @see SCROLL_STATE_DRAGGING
     * @see SCROLL_STATE_SETTLING
     * @author Hamza417
     */
    val currentScrollState: Int get() = scrollState

    private fun pageCount() = adapter?.getCount() ?: 0
    private fun maxLastPage() = (pageCount() - 1).coerceAtLeast(0)
    private fun maxScrollPx() = maxLastPage() * width.toFloat()

    /**
     * Returns the integer page index closest to the current [scrollPx].
     * Falls back to [currentPage] when the view width is not yet known.
     */
    private fun scrollPageIndex(): Int {
        val w = width.takeIf { it > 0 } ?: return currentPage.coerceAtLeast(0)
        return (scrollPx / w).roundToInt().coerceIn(0, maxLastPage())
    }

    /** Returns the adapter position of the currently selected page. */
    fun getCurrentItem(): Int = currentPage

    /**
     * Programmatically scrolls to [item].
     *
     * If [smoothScroll] is `false` (the default) the jump is instant; if `true` the pager
     * animates using [animationDurationMs]. If the view has not been laid out yet the call
     * is deferred until after the first layout pass.
     */
    fun setCurrentItem(item: Int, smoothScroll: Boolean = false) {
        if (width == 0) {
            post { setCurrentItem(item, smoothScroll) }
            return
        }
        val bounded = item.coerceIn(0, maxLastPage())
        if (!smoothScroll) {
            cancelAnimation()
            scrollPx = bounded * width.toFloat()
            applyTranslations()
            ensurePages()
            dispatchScrolled()
            dispatchPageSelected(bounded, fromUser = false)
            dispatchStateChanged(SCROLL_STATE_IDLE)
        } else {
            smoothScrollTo(bounded * width.toFloat(), durationOverrideMs = null, fromUser = false)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // Every page fills the pager exactly.
        val cw = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY)
        val ch = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(cw, ch)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        for (i in 0 until childCount) getChildAt(i).layout(0, 0, w, h)
        if (w > 0) {
            if (changed) {
                // Re-anchor scroll so the current page stays centred after a size change.
                scrollPx = currentPage.coerceAtLeast(0) * w.toFloat()
            }
            // Always ensure pages are loaded — covers the case where the adapter was
            // set before the first layout pass (width was 0 at that time).
            ensurePages()
            applyTranslations()
        }
    }

    /**
     * Recomputes [View.translationX] for every active page based on the current [scrollPx].
     */
    private fun applyTranslations() {
        val w = width.takeIf { it > 0 } ?: return
        for ((pos, view) in activePages) applyTranslationTo(view, pos, w)
    }

    /**
     * Sets [View.translationX] on [view] so that page [position] is centred at the current
     * scroll position. Uses [w] as the page width (defaults to [width]).
     */
    private fun applyTranslationTo(view: View, position: Int, w: Int = width) {
        if (w > 0) view.translationX = position * w.toFloat() - scrollPx
    }

    /**
     * Fires [OnPageChangeListener.onPageScrolled] on all registered listeners with the
     * position, fractional offset, and pixel offset derived from [scrollPx].
     */
    private fun dispatchScrolled() {
        val w = width.takeIf { it > 0 } ?: return
        val posF = scrollPx / w
        val pos = posF.toInt().coerceIn(0, maxLastPage())
        val offset = (posF - pos).coerceIn(0f, 1f)
        val px = (offset * w).toInt()
        pageChangeListeners.forEach {
            it.onPageScrolled(pos, offset, px)
        }
    }

    /**
     * Fires [OnPageChangeListener.onPageSelected] on all registered listeners if [position]
     * differs from [currentPage], then updates [currentPage].
     *
     * @param fromUser `true` when the page change was triggered by a user gesture.
     */
    private fun dispatchPageSelected(position: Int, fromUser: Boolean) {
        if (position != currentPage) {
            currentPage = position
            pageChangeListeners.forEach { l ->
                l.onPageSelected(position, fromUser)
                l.onPageSelected(position)
            }
        }
    }

    /**
     * Fires [OnPageChangeListener.onPageScrollStateChanged] on all registered listeners if
     * [newState] differs from the current [scrollState], then updates [scrollState].
     */
    private fun dispatchStateChanged(newState: Int) {
        if (scrollState != newState) {
            scrollState = newState
            pageChangeListeners.forEach {
                it.onPageScrollStateChanged(newState)
            }
        }
    }

    private val gestureDetector = GestureDetector(context, this)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /** Whether the user is currently performing a drag gesture. */
    private var isBeingDragged = false

    /** X coordinate of the last processed [MotionEvent], updated every [MotionEvent.ACTION_MOVE]. */
    private var lastMotionX = 0f

    /**
     * X coordinate recorded at [MotionEvent.ACTION_DOWN]. Used to measure cumulative
     * displacement so that slow drags (whose per-event delta never exceeds the touch slop)
     * still register once the total travel crosses the threshold.
     */
    private var initialMotionX = 0f

    /** Value of [scrollPx] at the moment the current drag gesture started. */
    private var dragStartScrollPx = 0f
    private var velocityTracker: VelocityTracker? = null

    /**
     * Y coordinate recorded at [MotionEvent.ACTION_DOWN]. Used together with [initialMotionX]
     * to determine the dominant swipe direction before committing to a horizontal or vertical drag.
     */
    private var initialMotionY = 0f

    /** Whether the current touch sequence has been identified as a primarily vertical drag. */
    private var isVerticalDrag = false

    /**
     * The currently registered [OnVerticalDragListener], or `null` if none is set.
     * Assign via [setOnVerticalDragListener].
     */
    private var verticalDragListener: OnVerticalDragListener? = null

    /**
     * Registers [listener] to receive vertical drag callbacks whenever the user swipes
     * primarily downward (or upward) on this pager. Pass `null` to remove any existing listener.
     *
     * @param listener The [OnVerticalDragListener] to register, or `null` to unregister.
     */
    fun setOnVerticalDragListener(listener: OnVerticalDragListener?) {
        verticalDragListener = listener
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialMotionX = ev.x
                initialMotionY = ev.y
                lastMotionX = ev.x
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - initialMotionX)
                val dy = abs(ev.y - initialMotionY)
                // Only intercept when the gesture is clearly horizontal, so that a
                // primarily-vertical swipe is never stolen from a parent swipe-to-close handler.
                if (dx > touchSlop * 0.6f && dx > dy) return true
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelAnimation()
                initialMotionX = event.x
                initialMotionY = event.y
                lastMotionX = event.x
                dragStartScrollPx = scrollPx
                isVerticalDrag = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
                // Do not call requestDisallowInterceptTouchEvent(true) here — wait until
                // the gesture direction is confirmed as horizontal. This allows a parent
                // swipe-to-close view to intercept a vertical drag before this pager locks it.
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.x - lastMotionX
                val totalDx = abs(event.x - initialMotionX)
                val totalDy = event.y - initialMotionY // signed: positive = downward

                if (!isBeingDragged && !isVerticalDrag) {
                    when {
                        // Primarily vertical — delegate to the vertical drag listener.
                        abs(totalDy) > touchSlop * 0.6f && abs(totalDy) > totalDx -> {
                            isVerticalDrag = true
                            // Allow ancestors to intercept this gesture sequence.
                            parent?.requestDisallowInterceptTouchEvent(false)
                            verticalDragListener?.onVerticalDragBegin()
                            verticalDragListener?.onVerticalDrag(totalDy, event)
                        }
                        // Primarily horizontal — commit to paging and lock the event.
                        totalDx > touchSlop * 0.6f -> {
                            isBeingDragged = true
                            dispatchStateChanged(SCROLL_STATE_DRAGGING)
                            parent?.requestDisallowInterceptTouchEvent(true)
                            performDrag(-dx)
                        }
                    }
                } else if (isVerticalDrag) {
                    // Keep notifying while the finger is still moving vertically.
                    verticalDragListener?.onVerticalDrag(totalDy, event)
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else if (isBeingDragged) {
                    performDrag(-dx)
                }
                lastMotionX = event.x
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val vx = velocityTracker?.xVelocity ?: 0f
                val vy = velocityTracker?.yVelocity ?: 0f
                val totalDy = event.y - initialMotionY

                if (isVerticalDrag) {
                    verticalDragListener?.onVerticalDragEnd(totalDy, vy, event)
                } else if (isBeingDragged) {
                    finishDrag(vx)
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    performClick()
                }
                velocityTracker?.recycle()
                velocityTracker = null
                isBeingDragged = false
                isVerticalDrag = false
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    /**
     * Translates [scrollPx] by [deltaPixels] (positive = scroll right / forward),
     * then repaints all active pages and notifies listeners.
     */
    private fun performDrag(deltaPixels: Float) {
        scrollPx = (scrollPx + deltaPixels).coerceIn(0f, maxScrollPx())
        applyTranslations()
        ensurePages()
        dispatchScrolled()
    }

    /**
     * Called when the user lifts their finger. Decides whether to fling to a distant page
     * (when [velocityX] exceeds [minFlingVelocity]) or to snap to the nearest page using
     * the [advanceThreshold] rule, then kicks off a settle animation.
     */
    private fun finishDrag(velocityX: Float) {
        val w = width.takeIf { it > 0 } ?: return
        val dragDeltaPages = (scrollPx - dragStartScrollPx) / w
        val forward = dragDeltaPages > 0f

        if (abs(velocityX) > minFlingVelocity) {
            val vPagesPerSec = abs(velocityX) / w
            val windowSec = 0.18f
            val pages = max(1, (vPagesPerSec * windowSec).roundToInt().coerceAtMost(3))
            val dir = if (velocityX < 0) +1 else -1
            val floorPage = (scrollPx / w).toInt().coerceIn(0, maxLastPage())
            val ceilPage = (floorPage + 1).coerceAtMost(maxLastPage())
            val base = if (dir > 0) ceilPage else floorPage
            val targetPage = (base + (pages - 1) * dir).coerceIn(0, maxLastPage())
            val distPages = abs(targetPage - scrollPx / w)
            val durationMs = (if (vPagesPerSec > 0f) (distPages / vPagesPerSec) * 1000f * 0.95f else 420f)
                .coerceIn(200f, 900f).toLong()
            smoothScrollTo(targetPage * w.toFloat(), durationOverrideMs = durationMs, fromUser = true)
        } else {
            val snapStart = (dragStartScrollPx / w).roundToInt().coerceIn(0, maxLastPage())
            val target = if (abs(dragDeltaPages) > advanceThreshold) {
                if (forward) (snapStart + 1).coerceAtMost(maxLastPage())
                else (snapStart - 1).coerceAtLeast(0)
            } else snapStart
            val distPages = abs(target - scrollPx / w)
            val durationMs = (300f + 180f * distPages).coerceIn(200f, 700f).toLong()
            smoothScrollTo(target * w.toFloat(), durationOverrideMs = durationMs, fromUser = true)
        }
        isBeingDragged = false
    }

    override fun onDown(e: MotionEvent): Boolean = true
    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean {
        performClick(); return true
    }
    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean = false
    override fun onLongPress(e: MotionEvent) {}

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        if (scrollState == SCROLL_STATE_DRAGGING) return false
        val w = width.takeIf { it > 0 } ?: return false
        val vPagesPerSec = abs(velocityX) / w
        val windowSec = 0.18f
        val pages = max(1, (vPagesPerSec * windowSec).roundToInt().coerceAtMost(3))
        val dir = if (velocityX < 0) +1 else -1
        val floorPage = (scrollPx / w).toInt().coerceIn(0, maxLastPage())
        val ceilPage = (floorPage + 1).coerceAtMost(maxLastPage())
        val base = if (dir > 0) ceilPage else floorPage
        val targetPage = (base + (pages - 1) * dir).coerceIn(0, maxLastPage())
        val distPages = abs(targetPage - scrollPx / w)
        val durationMs = (if (vPagesPerSec > 0f) (distPages / vPagesPerSec) * 1000f * 0.95f else 420f)
            .coerceIn(200f, 900f).toLong()
        smoothScrollTo(targetPage * w.toFloat(), durationOverrideMs = durationMs, fromUser = true)
        return true
    }

    private var animating = false
    private var animStartTime = -1L   // -1 = latch on first vsync frame
    private var animDuration = 0L
    private var animFrom = 0f
    private var animTo = 0f
    private var animFromUser = false
    private var animPosted = false
    private val choreographer: Choreographer by lazy { Choreographer.getInstance() }

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        animPosted = false
        advanceAnimation(frameTimeNanos / 1_000_000L)
    }

    /**
     * Starts or retargets a smooth-scroll animation toward [targetPx].
     *
     * If an animation is already running and the target has changed, the animation is pivoted
     * in-flight from the current [scrollPx] to [targetPx] without restarting from scratch.
     * Duration is recalculated proportionally to the remaining distance so apparent speed
     * stays consistent with no jerk or sudden acceleration.
     *
     * @param targetPx       Destination scroll position in pixels.
     * @param durationOverrideMs Override the default [animationDurationMs]; `null` uses the default.
     * @param fromUser       `true` when the scroll was initiated by a user gesture.
     */
    private fun smoothScrollTo(targetPx: Float, durationOverrideMs: Long?, fromUser: Boolean) {
        animFromUser = fromUser
        val clamped = targetPx.coerceIn(0f, maxScrollPx())
        if (scrollPx == clamped && !animating) {
            dispatchPageSelected(pageForPx(clamped), fromUser)
            dispatchStateChanged(SCROLL_STATE_IDLE)
            return
        }
        dispatchStateChanged(SCROLL_STATE_SETTLING)

        if (animating && clamped != animTo) {
            // Pivot the in-flight animation toward the new target without restarting.
            val distPx = abs(clamped - scrollPx)
            val pagesAway = distPx / width.toFloat().coerceAtLeast(1f)
            val baseDuration = durationOverrideMs ?: animationDurationMs
            val newDuration = (baseDuration * pagesAway.coerceAtLeast(0.5f))
                .toLong().coerceIn(150L, 900L)
            animFrom = scrollPx
            animTo = clamped
            animDuration = newDuration
            animStartTime = -1L   // latch fresh on next vsync frame
            // Animation loop is already running — no need to re-queue.
            return
        }

        animDuration = (durationOverrideMs ?: animationDurationMs).coerceAtLeast(0L)
        animFrom = scrollPx
        animTo = clamped
        animStartTime = -1L
        animating = true
        queueFrame()
    }

    /**
     * Posts [frameCallback] to [choreographer] if it is not already queued.
     * Calling this when a frame is already pending is safe and is a no-op.
     */
    private fun queueFrame() {
        if (!animPosted) {
            animPosted = true
            choreographer.postFrameCallback(frameCallback)
        }
    }

    /**
     * Advances the settle animation by one frame.
     *
     * The start timestamp is latched on the first call (when [animStartTime] == -1) to
     * avoid clock-source jitter between [System.currentTimeMillis] and the vsync clock.
     * When `t` reaches 1.0 the animation is finalized, [scrollPx] is snapped to [animTo],
     * and [dispatchPageSelected] / [dispatchStateChanged] are fired.
     */
    private fun advanceAnimation(nowMs: Long) {
        if (!animating) return
        if (animStartTime == -1L) animStartTime = nowMs
        val elapsed = (nowMs - animStartTime).coerceAtLeast(0L)
        val tRaw = if (animDuration > 0L) (elapsed.toFloat() / animDuration).coerceIn(0f, 1f) else 1f
        scrollPx = animFrom + (animTo - animFrom) * easeOutCubic(tRaw)
        applyTranslations()
        ensurePages()
        dispatchScrolled()
        if (tRaw < 1f) {
            queueFrame()
        } else {
            animating = false
            scrollPx = animTo
            applyTranslations()
            ensurePages()
            dispatchScrolled()
            dispatchPageSelected(pageForPx(scrollPx), animFromUser)
            dispatchStateChanged(SCROLL_STATE_IDLE)
        }
    }

    /**
     * Cancels any running settle animation and immediately transitions the scroll state
     * to [SCROLL_STATE_IDLE]. The pager stays at its current [scrollPx].
     * Also cancels any in-progress wrap-around animation.
     */
    private fun cancelAnimation() {
        if (animating) {
            animating = false
            if (animPosted) choreographer.removeFrameCallback(frameCallback)
            animPosted = false
            dispatchStateChanged(SCROLL_STATE_IDLE)
        }
        cancelWrapAnimation()
    }

    /**
     * Cubic ease-out interpolator: starts fast and decelerates toward `t = 1`.
     * Returns values in `[0, 1]` for inputs in `[0, 1]`.
     */
    private fun easeOutCubic(t: Float): Float {
        val p = t - 1f; return p * p * p + 1f
    }

    /**
     * Converts a scroll position in pixels to the nearest integer page index,
     * clamped to `[0, maxLastPage()]`.
     */
    private fun pageForPx(px: Float): Int =
        (px / width.coerceAtLeast(1)).roundToInt().coerceIn(0, maxLastPage())

    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoSlideInterval = 0L
    private var autoSlideLoop = true

    private val autoSlideRunnable = object : Runnable {
        override fun run() {
            val count = pageCount()
            if (autoSlideInterval > 0 && count > 1 && scrollState != SCROLL_STATE_DRAGGING) {
                if (autoSlideLoop && currentPage >= count - 1) {
                    // Smooth wrap-around: scroll *forward* past last page to a virtual
                    // page-0 copy (train passing effect), then silently snap back once done.
                    smoothScrollToWrap()
                } else {
                    val next = if (autoSlideLoop) currentPage + 1 else (currentPage + 1).coerceAtMost(count - 1)
                    if (next != currentPage) setCurrentItem(next, smoothScroll = true)
                }
                mainHandler.postDelayed(this, autoSlideInterval)
            }
        }
    }

    /**
     * Scrolls forward from the last page to a virtual copy of page 0 placed immediately
     * after the last real page. When the animation completes the scroll position is
     * silently reset to 0 so the illusion of a circular tape is seamless.
     *
     * Visual effect: the images appear to slide forward in a continuous strip (train effect)
     * rather than cutting back abruptly to the start.
     */
    private fun smoothScrollToWrap() {
        if (width == 0) return
        val count = pageCount()
        if (count <= 1) return

        // Preload page 0 so it is visible as soon as we scroll past the last page.
        // Position it at scrollPx = count * width (one page beyond the last).
        val wrapPos = count   // virtual index of the page-0 clone
        val wrapPx = wrapPos * width.toFloat()

        // Load the real page 0 and position it at the wrap slot.
        adapter?.let { ad ->
            if (!activePages.containsKey(WRAP_PAGE_KEY)) {
                val v = recyclePool.removeLastOrNull()
                    ?.also { ad.onBindView(0, it) }
                    ?: ad.onCreateView(0, this).also { ad.onBindView(0, it) }
                activePages[WRAP_PAGE_KEY] = v
                addView(v)
                if (width > 0 && height > 0) {
                    val cw = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
                    val ch = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
                    v.measure(cw, ch)
                    v.layout(0, 0, width, height)
                }
                v.translationX = wrapPx - scrollPx
            }
        }

        // Animate scrollPx from its current position (≈ last page) to wrapPx.
        val duration = (animationDurationMs * 1.1f).toLong().coerceIn(300L, 1200L)

        // Drive a manual animation so we can intercept the completion and reset.
        wrapAnimFrom = scrollPx
        wrapAnimTo = wrapPx
        wrapAnimDuration = duration
        wrapAnimStartMs = -1L
        wrapAnimating = true
        queueWrapFrame()
    }

    // Sentinel key for the virtual wrap-around page-0 clone in activePages.
    private val WRAP_PAGE_KEY = Int.MAX_VALUE

    private var wrapAnimating = false
    private var wrapAnimFrom = 0f
    private var wrapAnimTo = 0f
    private var wrapAnimDuration = 0L
    private var wrapAnimStartMs = -1L
    private var wrapAnimPosted = false

    private val wrapFrameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        wrapAnimPosted = false
        advanceWrapAnimation(frameTimeNanos / 1_000_000L)
    }

    private fun queueWrapFrame() {
        if (!wrapAnimPosted) {
            wrapAnimPosted = true
            choreographer.postFrameCallback(wrapFrameCallback)
        }
    }

    private fun advanceWrapAnimation(nowMs: Long) {
        if (!wrapAnimating) return
        if (wrapAnimStartMs == -1L) wrapAnimStartMs = nowMs
        val elapsed = (nowMs - wrapAnimStartMs).coerceAtLeast(0L)
        val tRaw = if (wrapAnimDuration > 0L) (elapsed.toFloat() / wrapAnimDuration).coerceIn(0f, 1f) else 1f

        scrollPx = wrapAnimFrom + (wrapAnimTo - wrapAnimFrom) * easeOutCubic(tRaw)

        // Update translations for real pages + the wrap clone.
        applyTranslations()
        activePages[WRAP_PAGE_KEY]?.translationX = wrapAnimTo - scrollPx

        dispatchScrolled()

        if (tRaw < 1f) {
            queueWrapFrame()
        } else {
            // Animation complete — silently teleport back to page 0.
            wrapAnimating = false
            // Remove the clone
            activePages.remove(WRAP_PAGE_KEY)?.let { v ->
                adapter?.onRecycleView(0, v)
                recyclePool.addLast(v)
                removeView(v)
            }
            // Reset scroll to page 0 without any visual change (page 0 is already in view).
            scrollPx = 0f
            currentPage = -1   // force dispatchPageSelected to fire
            applyTranslations()
            ensurePages()
            dispatchScrolled()
            dispatchPageSelected(0, fromUser = false)
            dispatchStateChanged(SCROLL_STATE_IDLE)
        }
    }

    private fun cancelWrapAnimation() {
        if (wrapAnimating) {
            wrapAnimating = false
            if (wrapAnimPosted) choreographer.removeFrameCallback(wrapFrameCallback)
            wrapAnimPosted = false
            // Clean up the clone view if present.
            activePages.remove(WRAP_PAGE_KEY)?.let { v ->
                adapter?.onRecycleView(0, v)
                recyclePool.addLast(v)
                removeView(v)
            }
        }
    }

    /**
     * Starts automatic page advancement, switching to the next page every [intervalMs]
     * milliseconds. If [loop] is `true` (default) the pager wraps from the last page back
     * to page 0; otherwise it stops at the last page.
     *
     * Call [stopAutoSlide] to cancel.
     */
    fun startAutoSlide(intervalMs: Long, loop: Boolean = true) {
        autoSlideInterval = intervalMs
        autoSlideLoop = loop
        mainHandler.removeCallbacks(autoSlideRunnable)
        if (intervalMs > 0) mainHandler.postDelayed(autoSlideRunnable, intervalMs)
    }

    /**
     * Stops automatic page advancement started by [startAutoSlide].
     */
    fun stopAutoSlide() {
        autoSlideInterval = 0
        mainHandler.removeCallbacks(autoSlideRunnable)
        cancelWrapAnimation()
    }


    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (width > 0) {
            ensurePages()
            applyTranslations()
        } else {
            post {
                if (width > 0) {
                    ensurePages()
                    applyTranslations()
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAutoSlide()
        cancelAnimation()
        cancelWrapAnimation()
        if (isActivityAlive()) {
            recycleAllPages()
        } else {
            // Activity is destroyed or finishing; skip adapter callbacks to avoid
            // triggering Glide (or any other loader) against a dead context. Just
            // wipe internal state — the system will release the views anyway.
            activePages.clear()
            recyclePool.clear()
            removeAllViews()
        }
    }

    /**
     * Returns `true` when the host [Activity] is still in a usable state, i.e. it has not
     * been destroyed or is not in the process of finishing. Walks up the [ContextWrapper]
     * chain so that themed or wrapped contexts are handled correctly.
     *
     * When the [context] is not backed by an [Activity] at all (e.g. an application context
     * used in tests), the method conservatively returns `true`.
     */
    private fun isActivityAlive(): Boolean {
        var ctx: Context = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                return !ctx.isDestroyed && !ctx.isFinishing
            }
            ctx = ctx.baseContext
        }
        return true
    }

    fun getCurrentImageView(): ImageView {
        val currentView = activePages[currentPage]
        return currentView as? ImageView ?: ImageView(context)
    }
}
