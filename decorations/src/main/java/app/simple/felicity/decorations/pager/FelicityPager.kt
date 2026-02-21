package app.simple.felicity.decorations.pager

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.OverScroller
import app.simple.felicity.decorations.pager.FelicityPager.Companion.SCROLL_STATE_DRAGGING
import app.simple.felicity.decorations.pager.FelicityPager.Companion.SCROLL_STATE_IDLE
import app.simple.felicity.decorations.pager.FelicityPager.Companion.SCROLL_STATE_SETTLING
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * FelicityPager
 *
 * A lightweight OpenGL-based horizontal pager that renders adapter-provided Bitmaps on textured quads
 * and supports both drag and fling navigation with predictable selection dispatching.
 *
 * Behavior summary
 * - Rendering: Each page is a full-screen quad translated horizontally in NDC; only a small window
 *   of neighboring pages is drawn for performance. Bitmaps are center-cropped to the surface aspect
 *   and uploaded as GL textures. Decoding happens off of the GL thread.
 * - Dragging: Touch MOVE updates a fractional page offset ([scrollOffset]). When the gesture ends,
 *   page settling chooses the target as follows:
 *   - If |velocityX| > [minFlingVelocity]: fling-based navigation (see below).
 *   - Else if absolute drag distance > [advanceThreshold] (fraction of a page): advance to nearest in the drag direction.
 *   - Else: snap back to the starting page.
 * - Fling-based scrolling: Target pages are chosen based on fling velocity magnitude and direction.
 *   Let widthPx be the view width; vPagesPerSec = |velocityX| / widthPx. The number of pages to advance
 *   is pages = max(1, round(vPagesPerSec * windowSec)), where windowSec = 0.40f. Direction is left for
 *   velocityX < 0 (advance forward), right otherwise (go backward). Duration is derived from distance and
 *   velocity: durationMs ≈ (distancePages / vPagesPerSec) * 1000, clamped to [200, 900] ms, and animated
 *   with an [easeOutCubic] for a decelerating feel. You can tweak the window in [finishDrag] and [onFling].
 * - Programmatic smooth scroll: [setCurrentItem] (item, smoothScroll = true) uses a fixed duration
 *   [animationDurationMs] (default 420 ms) with the same easing.
 * - Selection events: [OnPageChangeListener.onPageSelected] is dispatched only after the final settle completes (or immediately
 *   for non-animated jumps). Rapid successive swipes will not emit intermediate selections; only the final
 *   settled page fires selection, preventing chained song changes while swiping quickly.
 * - fromUser flag: An overload [OnPageChangeListener.onPageSelected] with (position, fromUser) is provided to differentiate user-initiated
 *   vs app-initiated changes. Both the new overload and the legacy [OnPageChangeListener.onPageSelected] are called for
 *   compatibility. User drags/flings pass fromUser = true; programmatic navigation passes false.
 * - State callbacks: [OnPageChangeListener.onPageScrollStateChanged] is dispatched with [SCROLL_STATE_DRAGGING], [SCROLL_STATE_SETTLING], and [SCROLL_STATE_IDLE]. Dragging starts
 *   once movement exceeds touch slop (scaledTouchSlop * 0.6). ACTION_DOWN cancels any in-flight animation so
 *   the user can take over immediately without friction.
 * - Auto slide: [startAutoSlide] (intervalMs, loop) advances pages on a timer when not dragging. With loop=true,
 *   it jumps to 0 (without animation) after the last page so the next timed advance animates from the start.
 *
 * Key configuration points (what to tweak later)
 * - [animationDurationMs]: Base duration (ms) for app-initiated smooth scrolls. Change via [setAnimationDurationMs].
 * - [advanceThreshold]: Fraction (0..1) of a page required to advance when releasing without a fling. Default 0.12f.
 * - [minFlingVelocity] sensitivity: Multiplier applied to ViewConfiguration.scaledMinimumFlingVelocity. Default 0.4f.
 *   Increase to require stronger flings; decrease to make flings easier to trigger.
 * - Fling window (windowSec): 0.40f. Controls how many pages are advanced per fling based on velocity. To tweak,
 *   update the local windowSec constants inside [finishDrag] and [onFling].
 * - Fling duration clamps: [200, 900] ms. Adjust the coerceIn bounds where durationMs is computed in [finishDrag]
 *   and [onFling] to change the feel for long or short hops.
 * - Easing: [easeOutCubic] is used for all settling animations. Replace [easeOutCubic] if a different curve is desired.
 * - Touch slop factor: Drag begins at ~60% of scaled touch slop. Adjust the 0.6f factor in ACTION_MOVE if needed.
 *
 * Adapter contract
 * - [getCount](): Int — total pages.
 * - [loadBitmap](position): Bitmap? — decode/load on a background thread; null to skip a page.
 * - [getItemId](position): Long — stable IDs help texture caching and reuse.
 *
 * Callbacks
 * - [onPageScrolled](position, positionOffset, positionOffsetPixels) — emitted continuously during drag/settle.
 * - [OnPageChangeListener.onPageSelected] (position, fromUser) — emitted only after a settle completes; legacy [OnPageChangeListener.onPageSelected]
 *   is also called for compatibility in the same moment.
 * - [onPageScrollStateChanged](state) — DRAGGING, SETTLING, IDLE.
 *
 * Limits and notes
 * - Bounds: Scrolling is clamped to [0, lastPage]. No true wrap-around; looping is achieved only by auto-slide
 *   jump to 0 at boundary.
 * - Interrupting: ACTION_DOWN cancels any running animation and returns the state to [SCROLL_STATE_IDLE] before starting drag.
 * - Rendering: [setAlpha] proxies to a uniform in the GL shader to avoid extra composition layers with SurfaceView.
 * - Resources: Textures are preloaded in a small radius around the current page and recycled when far away.
 *
 * Quick guidance
 * - Want faster programmatic slides? Call [setAnimationDurationMs] with 250.
 * - Want more pages per fling? Increase windowSec from 0.40f in [finishDrag]/[onFling].
 * - Want flings harder to trigger? Increase the 0.4f multiplier on [minFlingVelocity].
 * - Want releases to advance more easily without a fling? Raise [advanceThreshold] from 0.12f.
 *
 * @author Hamza417
 */
class FelicityPager @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), GestureDetector.OnGestureListener, Runnable {

    // ----- Public adapter + listeners -----
    interface Adapter {
        fun getCount(): Int

        /** Load (potentially heavy) bitmap for [position]. Runs off GL thread. Return null to skip. */
        fun loadBitmap(position: Int): Bitmap?

        /** Optional stable ids to improve cache retention */
        fun getItemId(position: Int): Long = position.toLong()
    }

    interface OnPageChangeListener {
        fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
        fun onPageSelected(position: Int) {}

        // Overload with fromUser flag (backward-compatible default no-op)
        fun onPageSelected(position: Int, fromUser: Boolean) {}
        fun onPageScrollStateChanged(state: Int) {}
    }

    companion object {
        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SETTLING = 2
    }

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

    // ----- Rendering / GL -----
    private val renderer: PagerRenderer

    // ----- Scrolling state -----
    @Volatile
    private var scrollOffset = 0f // 0 .. (count-1)
    private var currentPage = 0
    private var scrollState = SCROLL_STATE_IDLE
    private val scroller = OverScroller(context)
    private val gestureDetector = GestureDetector(context, this)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var isBeingDragged = false
    private var lastMotionX = 0f
    private var velocityTracker: VelocityTracker? = null
    private var dragStartOffset = 0f
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 1.65f // intentional flings only
    private val advanceThreshold = 0.25f // fraction of a page to switch without velocity

    // Animation config (consistent, non-physics)
    private var animationDurationMs: Long = 420L
    private var animFromUser: Boolean = false

    fun setAnimationDurationMs(durationMs: Long) {
        animationDurationMs = durationMs.coerceAtLeast(0L)
    }

    // Dispatch helpers
    private fun dispatchOnPageScrolled() {
        val pos = scrollOffset.toInt().coerceAtMost(maxLastPage())
        val offset = (scrollOffset - pos).coerceIn(0f, 1f)
        val px = (offset * width).toInt()
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

    private fun dispatchOnScrollStateChanged(newState: Int) {
        if (scrollState != newState) {
            scrollState = newState
            pageChangeListeners.forEach { it.onPageScrollStateChanged(newState) }
        }
    }

    // ----- Auto slide -----
    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoSlideInterval = 0L
    private var autoSlideLoop = true
    private val autoSlideRunnable = object : Runnable {
        override fun run() {
            val ad = adapter
            val count = ad?.getCount() ?: 0
            if (autoSlideInterval > 0 && count > 1 && scrollState != SCROLL_STATE_DRAGGING) {
                if (autoSlideLoop) {
                    if (currentPage >= count - 1) {
                        setCurrentItem(0, smoothScroll = false)
                    } else {
                        setCurrentItem(currentPage + 1, smoothScroll = true)
                    }
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

    // ----- Adapter -----
    private var adapter: Adapter? = null

    private fun maxLastPage(): Int = (adapter?.getCount() ?: 1) - 1
    private fun maxLastOffset(): Float = maxLastPage().toFloat()

    fun setAdapter(adapter: Adapter?) {
        queueEvent { renderer.clearAllTextures() }
        this.adapter = adapter
        scrollOffset = 0f
        currentPage = 0
        queueEvent { renderer.primeInitialLoad() }
        requestRender()
    }

    fun notifyDataSetChanged() {
        queueEvent { renderer.clearAllTextures() }
        if (scrollOffset > maxLastOffset()) scrollOffset = maxLastOffset()
        requestRender()
    }

    fun getCurrentItem(): Int = currentPage

    fun setCurrentItem(item: Int, smoothScroll: Boolean = false) {
        val bounded = item.coerceIn(0, maxLastPage())
        if (!smoothScroll) {
            scrollOffset = bounded.toFloat()
            scroller.abortAnimation()
            renderer.setScrollOffset(scrollOffset)
            dispatchOnPageScrolled()
            dispatchPageSelected(bounded, false)
            dispatchOnScrollStateChanged(SCROLL_STATE_IDLE)
            requestRender()
        } else {
            smoothScrollTo(bounded.toFloat(), durationOverrideMs = null, fromUser = false)
        }
    }

    // Scrolling helpers
    private fun performDrag(deltaPixels: Float) {
        val widthPx = width.takeIf { it > 0 } ?: return
        val pageDelta = deltaPixels / widthPx
        scrollOffset = (scrollOffset + pageDelta).coerceIn(0f, maxLastOffset())
        renderer.setScrollOffset(scrollOffset)
        dispatchOnPageScrolled()
        requestRender()
    }

    private fun finishDrag(velocityX: Float) {
        val floor = scrollOffset.toInt().coerceIn(0, maxLastPage())
        val ceil = (floor + 1).coerceAtMost(maxLastPage())
        val delta = scrollOffset - dragStartOffset
        val forward = delta > 0f
        val distance = abs(delta)
        val widthPx = width.takeIf { it > 0 } ?: 1
        if (abs(velocityX) > minFlingVelocity) {
            // Fling-based: advance proportional to velocity
            val vPagesPerSec = abs(velocityX) / widthPx
            val windowSec = 0.18f
            val pages = max(1, (vPagesPerSec * windowSec).roundToInt().coerceAtMost(3))
            val dir = if (velocityX < 0) +1 else -1
            val base = if (dir > 0) ceil else floor
            val targetIdx = (base + (pages - 1) * dir).coerceIn(0, maxLastPage())
            val distPages = abs(targetIdx - scrollOffset)
            val durationMs = (if (vPagesPerSec > 0f) (distPages / vPagesPerSec) * 1000f * 0.95f else 420f)
                .coerceIn(200f, 900f).toLong()
            smoothScrollTo(targetIdx.toFloat(), durationOverrideMs = durationMs, fromUser = true)
        } else {
            val target = if (distance > advanceThreshold) {
                if (forward) ceil else floor
            } else {
                dragStartOffset.roundToInt().coerceIn(0, maxLastPage())
            }
            val distPages = abs(target - scrollOffset)
            val durationMs = (300f + 180f * distPages).coerceIn(200f, 700f).toLong()
            smoothScrollTo(target.toFloat(), durationOverrideMs = durationMs, fromUser = true)
        }
        isBeingDragged = false
    }

    // Animation state
    private var animating = false
    private var animStartTime = 0L
    private var animDuration = 0L
    private var animFrom = 0f
    private var animTo = 0f
    private var animPosted = false
    private val choreographer: Choreographer by lazy { Choreographer.getInstance() }
    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        animPosted = false
        advanceAnimation(frameTimeNanos / 1_000_000L)
    }

    private fun smoothScrollTo(target: Float, durationOverrideMs: Long?, fromUser: Boolean) {
        val start = scrollOffset
        animFromUser = fromUser
        if (start == target) {
            dispatchPageSelected(target.toInt(), fromUser)
            dispatchOnScrollStateChanged(SCROLL_STATE_IDLE)
            return
        }
        dispatchOnScrollStateChanged(SCROLL_STATE_SETTLING)
        animDuration = (durationOverrideMs ?: animationDurationMs).coerceAtLeast(0L)
        animFrom = start
        animTo = target
        animStartTime = SystemClock.uptimeMillis()
        animating = true
        queueAnimationFrame()
    }

    private fun queueAnimationFrame() {
        if (!animPosted) {
            animPosted = true
            choreographer.postFrameCallback(frameCallback)
        }
    }

    private fun advanceAnimation(nowMs: Long) {
        if (!animating) return
        val elapsed = (nowMs - animStartTime).coerceAtLeast(0L)
        val tRaw = if (animDuration > 0L) (elapsed.toFloat() / animDuration).coerceIn(0f, 1f) else 1f
        // Decelerate towards the end for fling feel
        val fraction = easeOutCubic(tRaw)
        scrollOffset = animFrom + (animTo - animFrom) * fraction
        renderer.setScrollOffset(scrollOffset)
        dispatchOnPageScrolled()
        requestRender()
        if (tRaw < 1f) {
            queueAnimationFrame()
        } else {
            animating = false
            scrollOffset = animTo
            renderer.setScrollOffset(scrollOffset)
            dispatchOnPageScrolled()
            dispatchPageSelected(scrollOffset.toInt(), animFromUser)
            dispatchOnScrollStateChanged(SCROLL_STATE_IDLE)
            requestRender()
        }
    }

    private fun easeOutCubic(t: Float): Float {
        val p = t - 1f
        return p * p * p + 1f
    }

    override fun run() { /* no-op for frame driving now */
    }

    // ----- Init -----
    init {
        setEGLContextClientVersion(2)
        renderer = PagerRenderer()
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    // ----- Gesture handling -----
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!scroller.isFinished) scroller.abortAnimation()
                // Cancel any in-flight animation so user drag takes over immediately
                if (animating) {
                    animating = false
                    if (animPosted) choreographer.removeFrameCallback(frameCallback)
                    animPosted = false
                    dispatchOnScrollStateChanged(SCROLL_STATE_IDLE)
                }
                lastMotionX = event.x
                dragStartOffset = scrollOffset
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.x - lastMotionX
                if (!isBeingDragged && abs(dx) > touchSlop * 0.6f) {
                    isBeingDragged = true
                    dispatchOnScrollStateChanged(SCROLL_STATE_DRAGGING)
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

    // ----- GestureDetector callbacks -----
    override fun onDown(e: MotionEvent): Boolean = true
    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean {
        performClick(); return true
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean = false
    override fun onLongPress(e: MotionEvent) {}
    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        // Allow fling to advance multiple pages proportional to velocity
        if (scrollState == SCROLL_STATE_DRAGGING) return false
        val widthPx = width.takeIf { it > 0 } ?: return false
        val floor = scrollOffset.toInt().coerceIn(0, maxLastPage())
        val ceil = (floor + 1).coerceAtMost(maxLastPage())
        val vPagesPerSec = abs(velocityX) / widthPx
        val windowSec = 0.18f
        val pages = max(1, (vPagesPerSec * windowSec).roundToInt().coerceAtMost(3))
        val dir = if (velocityX < 0) +1 else -1
        val base = if (dir > 0) ceil else floor
        val targetIdx = (base + (pages - 1) * dir).coerceIn(0, maxLastPage())
        val distPages = abs(targetIdx - scrollOffset)
        val durationMs = (if (vPagesPerSec > 0f) (distPages / vPagesPerSec) * 1000f * 0.95f else 420f)
            .coerceIn(200f, 900f).toLong()
        smoothScrollTo(targetIdx.toFloat(), durationOverrideMs = durationMs, fromUser = true)
        return true
    }

    // ----- Lifecycle -----
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Re-prime textures after reattach (e.g. predictive back cancel restoring the view).
        queueEvent { renderer.reloadTextures(scrollOffset) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAutoSlide()
        if (animPosted) choreographer.removeFrameCallback(frameCallback)
        animPosted = false
        animating = false
        // Clear textures (they become invalid when the EGL context is lost), but do NOT
        // call release() / shut down the decodeExecutor here so the renderer stays usable
        // if the view is reattached (predictive back cancel / activity resume).
        queueEvent { renderer.clearAllTextures() }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            // Coming back from predictive back (or any window-level hide/show):
            // textures may have been lost – reload around the current page.
            queueEvent { renderer.reloadTextures(scrollOffset) }
            requestRender()
        }
    }

    override fun setAlpha(alpha: Float) {
        queueEvent { renderer.setPagerAlpha(alpha) }
    }

    // ----- View <-> Renderer interaction -----
    private inner class PagerRenderer : Renderer {
        private val proj = FloatArray(16)
        private val viewM = FloatArray(16)
        private val model = FloatArray(16)
        private val mvp = FloatArray(16)

        private val quadVerts = floatArrayOf(
                -1f, 1f, 0f,
                -1f, -1f, 0f,
                1f, -1f, 0f,
                1f, 1f, 0f
        )
        private val quadUV = floatArrayOf(
                0f, 0f,
                0f, 1f,
                1f, 1f,
                1f, 0f
        )
        private val quadInd = shortArrayOf(0, 1, 2, 0, 2, 3)
        private lateinit var vb: FloatBuffer
        private lateinit var tb: FloatBuffer
        private lateinit var ib: ShortBuffer

        private var program = 0
        private var aPos = 0
        private var aUV = 0
        private var uMVP = 0
        private var uTex = 0
        private var uAlpha = 0

        private val textures = ConcurrentHashMap<Long, Int>()
        private val positionToId = ConcurrentHashMap<Int, Long>()
        private val inFlight = ConcurrentHashMap<Int, Boolean>()
        private val decodeExecutor = Executors.newFixedThreadPool(2)
        private val futures = ConcurrentHashMap<Int, Future<*>>()

        private var visibleRadius = 1
        private var keepRadius = 2

        @Volatile
        private var glScrollOffset = 0f
        private var surfaceWidth = 0
        private var surfaceHeight = 0

        @Volatile
        private var globalAlpha: Float = 1f
        fun setGlobalAlpha(a: Float) {
            globalAlpha = a.coerceIn(0f, 1f)
        }

        fun setPagerAlpha(alpha: Float) {
            val a = alpha.coerceIn(0f, 1f)
            if (a == globalAlpha) return
            globalAlpha = a
            queueEvent { setGlobalAlpha(a) }
            requestRender()
        }

        fun getPagerAlpha(): Float = globalAlpha
        fun setScrollOffset(offset: Float) {
            glScrollOffset = offset
        }

        fun clearAllTextures() {
            textures.values.forEach { id -> GLES20.glDeleteTextures(1, intArrayOf(id), 0) }
            textures.clear()
            positionToId.clear()
            inFlight.clear()
            futures.values.forEach { it.cancel(true) }
            futures.clear()
        }

        fun release() {
            futures.values.forEach { it.cancel(true) }; decodeExecutor.shutdownNow()
        }

        /** Clear all stale texture handles and reload pages around [offset]. Call via queueEvent. */
        fun reloadTextures(offset: Float) {
            textures.clear()
            positionToId.clear()
            inFlight.clear()
            futures.values.forEach { it.cancel(false) }
            futures.clear()
            glScrollOffset = offset
            preloadAround(offset)
        }

        override fun onSurfaceCreated(unused: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            vb = ByteBuffer.allocateDirect(quadVerts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(quadVerts); position(0) }
            tb = ByteBuffer.allocateDirect(quadUV.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(quadUV); position(0) }
            ib = ByteBuffer.allocateDirect(quadInd.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(quadInd); position(0) }
            buildProgram()
            Matrix.setIdentityM(viewM, 0)
            // The EGL context was (re-)created – all previously uploaded textures are gone.
            // Purge the stale handles from our cache and schedule a reload so pages don't
            // stay blank after a predictive-back gesture or any other context loss event.
            textures.clear()
            positionToId.clear()
            inFlight.clear()
            futures.values.forEach { it.cancel(false) }
            futures.clear()
            primeInitialLoad()
        }

        override fun onSurfaceChanged(unused: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            val dimensionsChanged = surfaceWidth != width || surfaceHeight != height
            surfaceWidth = width
            surfaceHeight = height
            Matrix.orthoM(proj, 0, -1f, 1f, -1f, 1f, -2f, 2f)
            // If dimensions changed (including first setup), clear cached textures so they are
            // re-decoded with the correct aspect ratio. This fixes black/squeezed images that
            // were loaded before the surface dimensions were known.
            if (dimensionsChanged && width > 0 && height > 0) {
                clearAllTextures()
                primeInitialLoad()
            }
        }

        override fun onDrawFrame(unused: javax.microedition.khronos.opengles.GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            preloadAround(glScrollOffset)
            val count = adapter?.getCount() ?: 0
            if (count == 0) return
            val center = glScrollOffset
            val first = max(0, center.toInt() - visibleRadius)
            val last = min(count - 1, center.toInt() + visibleRadius + 1)
            for (i in first..last) {
                drawPage(i, center - i)
            }
            recycleFar(center)
        }

        fun primeInitialLoad() {
            preloadAround(glScrollOffset)
        }

        private fun buildProgram() {
            val vs = """
                attribute vec3 aPos;
                attribute vec2 aUV;
                uniform mat4 uMVP;
                varying vec2 vUV;
                void main() {
                    vUV = aUV;
                    gl_Position = uMVP * vec4(aPos, 1.0);
                }
            """
            val fs = """
                precision mediump float;
                varying vec2 vUV;
                uniform sampler2D uTex;
                uniform float uAlpha;
                void main() {
                    vec4 c = texture2D(uTex, vUV);
                    gl_FragColor = vec4(c.rgb, c.a * uAlpha);
                }
            """
            val vId = compileShader(GLES20.GL_VERTEX_SHADER, vs)
            val fId = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vId)
            GLES20.glAttachShader(program, fId)
            GLES20.glLinkProgram(program)
            aPos = GLES20.glGetAttribLocation(program, "aPos")
            aUV = GLES20.glGetAttribLocation(program, "aUV")
            uMVP = GLES20.glGetUniformLocation(program, "uMVP")
            uTex = GLES20.glGetUniformLocation(program, "uTex")
            uAlpha = GLES20.glGetUniformLocation(program, "uAlpha")
        }

        private fun drawPage(position: Int, offsetFromPage: Float) {
            val count = adapter?.getCount() ?: return
            if (position !in 0..<count) return
            val id = adapter?.getItemId(position) ?: position.toLong()
            val tex = textures[id] ?: return
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, -offsetFromPage * 2f, 0f, 0f)
            Matrix.multiplyMM(mvp, 0, viewM, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, proj, 0, mvp, 0)
            GLES20.glUseProgram(program)
            GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
            GLES20.glUniform1f(uAlpha, globalAlpha)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glUniform1i(uTex, 0)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glEnableVertexAttribArray(aUV)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, vb)
            GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 0, tb)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, ib)
            GLES20.glDisableVertexAttribArray(aPos)
            GLES20.glDisableVertexAttribArray(aUV)
        }

        private fun preloadAround(center: Float) {
            val count = adapter?.getCount() ?: return
            val start = max(0, center.toInt() - visibleRadius - 1)
            val end = min(count - 1, center.toInt() + visibleRadius + 1)
            for (i in start..end) maybeLoad(i)
        }

        private fun recycleFar(center: Float) {
            val count = adapter?.getCount() ?: return
            val lowKeep = max(0, center.toInt() - keepRadius)
            val highKeep = min(count - 1, center.toInt() + keepRadius)
            val it = textures.entries.iterator()
            while (it.hasNext()) {
                val e = it.next()
                val pos = positionToId.entries.firstOrNull { p -> p.value == e.key }?.key
                if (pos == null || pos < lowKeep || pos > highKeep) {
                    GLES20.glDeleteTextures(1, intArrayOf(e.value), 0)
                    it.remove()
                }
            }
        }

        private fun maybeLoad(position: Int) {
            if (inFlight[position] == true) return
            val id = adapter?.getItemId(position) ?: position.toLong()
            if (textures.containsKey(id)) return
            val ad = adapter ?: return
            inFlight[position] = true
            futures[position] = decodeExecutor.submit {
                val bmp = try {
                    ad.loadBitmap(position)
                } catch (_: Throwable) {
                    null
                }
                if (bmp != null) {
                    // Re-read surface dimensions inside the task so we always get the latest values.
                    // Fall back to view layout dimensions, then to the bitmap's own aspect so we
                    // never produce a distorted or fully-black frame.
                    val sw = surfaceWidth
                    val sh = surfaceHeight
                    val targetAspect = when {
                        sw > 0 && sh > 0 -> sw.toFloat() / sh
                        else -> {
                            val vw = this@FelicityPager.width
                            val vh = this@FelicityPager.height
                            if (vw > 0 && vh > 0) vw.toFloat() / vh
                            else bmp.width.toFloat() / bmp.height.coerceAtLeast(1) // identity: no crop
                        }
                    }
                    val processed = centerCropToAspect(bmp, targetAspect)
                    queueEvent {
                        val texId = createTexture(processed)
                        textures[id] = texId
                        positionToId[position] = id
                        requestRender()
                    }
                }
                inFlight.remove(position)
            }
        }

        private fun compileShader(type: Int, src: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, src)
            GLES20.glCompileShader(id)
            return id
        }

        private fun createTexture(bitmap: Bitmap): Int {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            val id = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            bitmap.recycle()
            return id
        }

        private fun centerCropToAspect(src: Bitmap, targetAspect: Float): Bitmap {
            if (targetAspect <= 0f) return src
            val w = src.width
            val h = src.height
            val bmpAspect = w.toFloat() / h
            return when {
                abs(bmpAspect - targetAspect) < 0.01f -> {
                    src
                }
                bmpAspect > targetAspect -> {
                    val newW = (h * targetAspect).roundToInt().coerceAtLeast(1)
                    val x = ((w - newW) / 2f).roundToInt().coerceAtLeast(0)
                    Bitmap.createBitmap(src, x, 0, min(newW, w - x), h)
                }
                else -> {
                    val newH = (w / targetAspect).roundToInt().coerceAtLeast(1)
                    val y = ((h - newH) / 2f).roundToInt().coerceAtLeast(0)
                    Bitmap.createBitmap(src, 0, y, w, min(newH, h - y))
                }
            }.also { if (it != src) src.recycle() }
        }
    }
}
