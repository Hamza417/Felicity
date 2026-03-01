package app.simple.felicity.decorations.pager

import android.content.Context
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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * FelicityPager — raw horizontal pager ViewGroup.
 *
 * This class is intentionally free of any image-loading or Glide dependency.
 * It is a pure scroll/layout/touch engine:
 *  - Pages are arbitrary [View]s produced and recycled entirely by [PageAdapter].
 *  - Positions are driven by translationX on each child view.
 *  - All drag, fling, snap and auto-slide logic lives here.
 *
 * To display images use [ImagePageAdapter] (a ready-made subclass that accepts a
 * [ImageBitmapProvider] + [ImageBitmapCanceller] pair) or write your own [PageAdapter].
 *
 * Behavior summary
 * ──────────────────
 * • Page N is centred when `scrollPx == N * width`.
 * • Drag:   ACTION_MOVE shifts scrollPx continuously; bounds-clamped to [0, (count-1)*width].
 * • Fling:  velocity → pages-to-advance (vPagesPerSec × windowSec, capped at 3).
 * • Slow release: advance if |drag| > advanceThreshold (0.25) × width, else snap back.
 * • Settlement: Choreographer + easeOutCubic, start-time latched on the first vsync
 *   frame to avoid uptime/vsync clock-source jitter.
 * • [OnPageChangeListener]: DRAGGING → SETTLING → IDLE, onPageScrolled every frame,
 *   onPageSelected only after a settle completes (or immediately for instant jumps).
 * • fromUser flag distinguishes user swipes from programmatic [setCurrentItem].
 * • Auto-slide: [startAutoSlide] / [stopAutoSlide].
 *
 * Adapter contract ([PageAdapter])
 * ──────────────────────────────────
 * • getCount()               — total pages.
 * • getItemId(position)      — stable id (default = position).
 * • onCreateView(position, parent)  — inflate/create the page View; must not attach it.
 * • onBindView(position, view)      — bind data into an existing view (called on create
 *                                     and on re-use after recycle).
 * • onRecycleView(position, view)   — release resources (cancel loads, clear images, …).
 *
 * @author Hamza417
 */
class FelicityPager @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : ViewGroup(context, attrs), GestureDetector.OnGestureListener {

    // ── PageAdapter ───────────────────────────────────────────────────────────────

    /**
     * Base adapter for [FelicityPager].  Implement this directly for fully custom pages,
     * or use [ImagePageAdapter] for the common image-display use-case.
     */
    interface PageAdapter {
        /** Total number of pages. */
        fun getCount(): Int

        /**
         * Stable, unique id for this position.
         * Used to avoid re-binding a view that already shows the right content.
         * Default: position as Long.
         */
        fun getItemId(position: Int): Long = position.toLong()

        /**
         * Create a brand-new page view for [position].
         * **Do not** add it to any parent — [FelicityPager] will do that.
         */
        fun onCreateView(position: Int, parent: ViewGroup): View

        /**
         * Bind data for [position] into [view].
         * Called both when a view is freshly created and when a recycled view is re-used.
         */
        fun onBindView(position: Int, view: View)

        /**
         * The pager is about to remove [view] from the window.
         * Release any async resources (cancel image loads, clear bitmaps, …).
         * The view is then placed in the recycle pool and may be re-bound later via [onBindView].
         */
        fun onRecycleView(position: Int, view: View)
    }

    // ── OnPageChangeListener ──────────────────────────────────────────────────────

    interface OnPageChangeListener {
        fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
        fun onPageSelected(position: Int) {}

        /** Overload with fromUser flag — backward-compatible default no-op. */
        fun onPageSelected(position: Int, fromUser: Boolean) {}
        fun onPageScrollStateChanged(state: Int) {}
    }

    companion object {
        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SETTLING = 2
    }

    // ── Listeners ────────────────────────────────────────────────────────────────

    private val pageChangeListeners = CopyOnWriteArrayList<OnPageChangeListener>()

    fun addOnPageChangeListener(l: OnPageChangeListener) {
        pageChangeListeners.add(l)
    }

    fun removeOnPageChangeListener(l: OnPageChangeListener) {
        pageChangeListeners.remove(l)
    }

    fun clearOnPageChangeListeners() {
        pageChangeListeners.clear()
    }

    // ── Adapter ──────────────────────────────────────────────────────────────────

    private var adapter: PageAdapter? = null

    fun setAdapter(adapter: PageAdapter?) {
        this.adapter = adapter
        cancelAnimation()
        scrollPx = 0f
        currentPage = -1   // reset so dispatchPageSelected(0) always fires
        recycleAllPages()
        if (width > 0) {
            ensurePages()
            applyTranslations()
        } else {
            // Width is 0 — the view hasn't been laid out yet.
            // Defer the initial page load until the first layout pass completes.
            post {
                if (this.adapter === adapter && width > 0) {
                    ensurePages()
                    applyTranslations()
                    dispatchScrolled()
                }
            }
        }
        dispatchScrolled()
        dispatchPageSelected(0, fromUser = false)
        dispatchStateChanged(SCROLL_STATE_IDLE)
    }

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

    // ── Configuration ─────────────────────────────────────────────────────────────

    /** Duration (ms) for programmatic smooth-scrolls via [setCurrentItem]. */
    var animationDurationMs: Long = 420L
        set(v) {
            field = v.coerceAtLeast(0L)
        }

    /** Fraction of page width required to advance on a slow release (no fling). */
    private val advanceThreshold = 0.25f

    private val minFlingVelocity =
        ViewConfiguration.get(context).scaledMinimumFlingVelocity * 1.65f

    /** Number of pages to keep loaded on each side of the visible page. */
    private val pageRadius = 2

    // ── Page pool ────────────────────────────────────────────────────────────────

    /** Active pages currently attached to this ViewGroup, keyed by adapter position. */
    private val activePages = HashMap<Int, View>()

    /** Views waiting to be re-bound — avoids inflation churn. */
    private val recyclePool = ArrayDeque<View>(8)

    private fun obtainView(position: Int): View {
        val ad = adapter!!
        return recyclePool.removeLastOrNull()?.also { ad.onBindView(position, it) }
            ?: ad.onCreateView(position, this).also { ad.onBindView(position, it) }
    }

    private fun recyclePage(position: Int) {
        val v = activePages.remove(position) ?: return
        adapter?.onRecycleView(position, v)
        recyclePool.addLast(v)
        removeView(v)
    }

    private fun recycleAllPages() {
        activePages.keys.toList().forEach { recyclePage(it) }
    }

    private fun loadPage(position: Int) {
        val ad = adapter ?: return
        if (position !in 0 until ad.getCount()) return
        if (activePages.containsKey(position)) return
        val v = obtainView(position)
        activePages[position] = v
        addView(v)
        applyTranslationTo(v, position)
    }

    /** Ensure pages in [center ± pageRadius] are loaded; recycle those outside. */
    private fun ensurePages() {
        val count = adapter?.getCount() ?: return
        val center = scrollPageIndex()
        val lo = max(0, center - pageRadius)
        val hi = minOf(count - 1, center + pageRadius)
        for (i in lo..hi) loadPage(i)
        activePages.keys.filter { it < lo || it > hi }.forEach { recyclePage(it) }
    }

    // ── Scroll state ──────────────────────────────────────────────────────────────

    /**
     * Continuous horizontal scroll in pixels.
     * Page N is centred when `scrollPx == N * width`.
     */
    private var scrollPx = 0f
    private var currentPage = 0
    private var scrollState = SCROLL_STATE_IDLE

    private fun pageCount() = adapter?.getCount() ?: 0
    private fun maxLastPage() = (pageCount() - 1).coerceAtLeast(0)
    private fun maxScrollPx() = maxLastPage() * width.toFloat()

    /** Integer page index closest to the current scroll position. */
    private fun scrollPageIndex(): Int {
        val w = width.takeIf { it > 0 } ?: return currentPage
        return (scrollPx / w).roundToInt().coerceIn(0, maxLastPage())
    }

    fun getCurrentItem(): Int = currentPage

    fun setCurrentItem(item: Int, smoothScroll: Boolean = false) {
        if (width == 0) {
            // Layout hasn't happened yet — defer until after the first layout pass.
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

    // ── Layout ────────────────────────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // Every page fills the pager exactly
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
                // Re-anchor scroll so the current page stays centred after a size change
                scrollPx = currentPage * w.toFloat()
            }
            // Always ensure pages are loaded — covers the case where the adapter was
            // set before the first layout pass (width was 0 at that time).
            applyTranslations()
            ensurePages()
        }
    }

    private fun applyTranslations() {
        val w = width.takeIf { it > 0 } ?: return
        for ((pos, view) in activePages) applyTranslationTo(view, pos, w)
    }

    private fun applyTranslationTo(view: View, position: Int, w: Int = width) {
        view.translationX = position * w.toFloat() - scrollPx
    }

    // ── Dispatch helpers ──────────────────────────────────────────────────────────

    private fun dispatchScrolled() {
        val w = width.takeIf { it > 0 } ?: return
        val posF = scrollPx / w
        val pos = posF.toInt().coerceIn(0, maxLastPage())
        val offset = (posF - pos).coerceIn(0f, 1f)
        val px = (offset * w).toInt()
        pageChangeListeners.forEach { it.onPageScrolled(pos, offset, px) }
    }

    private fun dispatchPageSelected(position: Int, fromUser: Boolean) {
        if (position != currentPage) {
            currentPage = position
            pageChangeListeners.forEach { l ->
                l.onPageSelected(position, fromUser)
                l.onPageSelected(position)
            }
        }
    }

    private fun dispatchStateChanged(newState: Int) {
        if (scrollState != newState) {
            scrollState = newState
            pageChangeListeners.forEach { it.onPageScrollStateChanged(newState) }
        }
    }

    // ── Touch / gesture ───────────────────────────────────────────────────────────

    private val gestureDetector = GestureDetector(context, this)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var isBeingDragged = false
    private var lastMotionX = 0f
    private var dragStartScrollPx = 0f
    private var velocityTracker: VelocityTracker? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> lastMotionX = ev.x
            MotionEvent.ACTION_MOVE ->
                if (abs(ev.x - lastMotionX) > touchSlop * 0.6f) return true
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelAnimation()
                lastMotionX = event.x
                dragStartScrollPx = scrollPx
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.x - lastMotionX
                if (!isBeingDragged && abs(dx) > touchSlop * 0.6f) {
                    isBeingDragged = true
                    dispatchStateChanged(SCROLL_STATE_DRAGGING)
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (isBeingDragged) performDrag(-dx)
                lastMotionX = event.x
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val vx = velocityTracker?.xVelocity ?: 0f
                if (isBeingDragged) {
                    finishDrag(vx)
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    performClick()
                }
                velocityTracker?.recycle()
                velocityTracker = null
                isBeingDragged = false
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun performDrag(deltaPixels: Float) {
        scrollPx = (scrollPx + deltaPixels).coerceIn(0f, maxScrollPx())
        applyTranslations()
        ensurePages()
        dispatchScrolled()
    }

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

    // ── GestureDetector ───────────────────────────────────────────────────────────

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

    // ── Settle animation ──────────────────────────────────────────────────────────

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

    private fun smoothScrollTo(targetPx: Float, durationOverrideMs: Long?, fromUser: Boolean) {
        animFromUser = fromUser
        val clamped = targetPx.coerceIn(0f, maxScrollPx())
        if (scrollPx == clamped) {
            dispatchPageSelected(pageForPx(clamped), fromUser)
            dispatchStateChanged(SCROLL_STATE_IDLE)
            return
        }
        dispatchStateChanged(SCROLL_STATE_SETTLING)
        animDuration = (durationOverrideMs ?: animationDurationMs).coerceAtLeast(0L)
        animFrom = scrollPx
        animTo = clamped
        animStartTime = -1L
        animating = true
        queueFrame()
    }

    private fun queueFrame() {
        if (!animPosted) {
            animPosted = true
            choreographer.postFrameCallback(frameCallback)
        }
    }

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

    private fun cancelAnimation() {
        if (animating) {
            animating = false
            if (animPosted) choreographer.removeFrameCallback(frameCallback)
            animPosted = false
            dispatchStateChanged(SCROLL_STATE_IDLE)
        }
    }

    private fun easeOutCubic(t: Float): Float {
        val p = t - 1f; return p * p * p + 1f
    }

    private fun pageForPx(px: Float): Int =
        (px / width.coerceAtLeast(1)).roundToInt().coerceIn(0, maxLastPage())

    // ── Auto-slide ────────────────────────────────────────────────────────────────

    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoSlideInterval = 0L
    private var autoSlideLoop = true

    private val autoSlideRunnable = object : Runnable {
        override fun run() {
            val count = pageCount()
            if (autoSlideInterval > 0 && count > 1 && scrollState != SCROLL_STATE_DRAGGING) {
                if (autoSlideLoop) {
                    if (currentPage >= count - 1) setCurrentItem(0, smoothScroll = false)
                    else setCurrentItem(currentPage + 1, smoothScroll = true)
                } else {
                    val next = (currentPage + 1).coerceAtMost(count - 1)
                    if (next != currentPage) setCurrentItem(next, smoothScroll = true)
                }
                mainHandler.postDelayed(this, autoSlideInterval)
            }
        }
    }

    fun startAutoSlide(intervalMs: Long, loop: Boolean = true) {
        autoSlideInterval = intervalMs
        autoSlideLoop = loop
        mainHandler.removeCallbacks(autoSlideRunnable)
        if (intervalMs > 0) mainHandler.postDelayed(autoSlideRunnable, intervalMs)
    }

    fun stopAutoSlide() {
        autoSlideInterval = 0
        mainHandler.removeCallbacks(autoSlideRunnable)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────────

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
        recycleAllPages()
    }
}
