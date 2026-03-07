package app.simple.felicity.decorations.miniplayer

import android.content.Context
import android.view.Choreographer
import android.view.ViewConfiguration
import app.simple.felicity.decorations.miniplayer.MiniPlayerScrollEngine.Companion.DRAGGING
import app.simple.felicity.decorations.miniplayer.MiniPlayerScrollEngine.Companion.IDLE
import app.simple.felicity.decorations.miniplayer.MiniPlayerScrollEngine.Companion.SETTLING
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Self-contained vsync-driven scroll/fling engine for [MiniPlayer].
 *
 * Mirrors the paging model of FelicityPager:
 *  - [Choreographer]-driven frame callbacks for smooth animation
 *  - `easeOutCubic` easing curve
 *  - Velocity-based fling with multi-page advance
 *  - Advance-threshold snap on slow drags
 *
 * The host view supplies a [Listener] to receive scroll position updates and
 * state-change events. All public methods are safe to call from the main thread.
 *
 * @param context Used only to read [ViewConfiguration] constants once at creation.
 */
internal class MiniPlayerScrollEngine(context: Context) {

    /** Scroll and state events delivered back to the host view. */
    interface Listener {
        /** Called every frame while scrolling so the host can redraw. */
        fun onScrollChanged(scrollPx: Float)

        /** Called when the settled page changes. [fromUser] is true for touch/fling. */
        fun onPageSettled(page: Int, fromUser: Boolean)

        /** Called when [scrollState] transitions between [IDLE], [DRAGGING], [SETTLING]. */
        fun onScrollStateChanged(state: Int)
    }

    companion object {
        const val IDLE = 0
        const val DRAGGING = 1
        const val SETTLING = 2

        private const val ANIMATION_DURATION_MS = 620L
        private const val ADVANCE_THRESHOLD = 0.25f
        private const val FLING_WINDOW_SEC = 0.18f
        private const val FLING_VELOCITY_FACTOR = 1.65f
    }

    var listener: Listener? = null

    var scrollPx: Float = 0f
        private set

    var currentPage: Int = 0
        private set

    var scrollState: Int = IDLE
        private set

    var pageCount: Int = 0
    var viewWidth: Int = 0

    val minFlingVelocity: Float =
        ViewConfiguration.get(context).scaledMinimumFlingVelocity * FLING_VELOCITY_FACTOR

    private fun maxLastPage() = (pageCount - 1).coerceAtLeast(0)
    private fun maxScrollPx() = maxLastPage() * viewWidth.toFloat()
    private fun pageForPx(px: Float) =
        (px / viewWidth.coerceAtLeast(1)).roundToInt().coerceIn(0, maxLastPage())

    /** Current fractional page index based on scroll position. */
    fun scrollPageIndex(): Int {
        val w = viewWidth.takeIf { it > 0 } ?: return currentPage.coerceAtLeast(0)
        return (scrollPx / w).roundToInt().coerceIn(0, maxLastPage())
    }

    fun clampScrollPx() {
        val max = maxScrollPx()
        if (scrollPx > max) scrollPx = max
    }

    fun clampCurrentPage() {
        if (currentPage > maxLastPage()) currentPage = maxLastPage()
    }

    /** Immediately jump to [page] without animation. */
    fun jumpToPage(page: Int) {
        cancelAnimation()
        scrollPx = page * viewWidth.toFloat()
        currentPage = page
        notifyScrollChanged()
        notifyScrollState(IDLE)
    }

    /** Animate to [targetPx], optionally overriding the duration. */
    fun smoothScrollTo(targetPx: Float, durationOverrideMs: Long? = null, fromUser: Boolean = false) {
        animFromUser = fromUser
        val clamped = targetPx.coerceIn(0f, maxScrollPx())

        if (scrollPx == clamped && !animating) {
            notifyPageSettled(pageForPx(clamped), fromUser)
            notifyScrollState(IDLE)
            return
        }

        notifyScrollState(SETTLING)

        if (animating && clamped != animTo) {
            val distPx = abs(clamped - scrollPx)
            val pagesAway = distPx / viewWidth.toFloat().coerceAtLeast(1f)
            val base = durationOverrideMs ?: ANIMATION_DURATION_MS
            animDurationMs = (base * pagesAway.coerceAtLeast(0.5f)).toLong().coerceIn(150L, 900L)
            animFrom = scrollPx
            animTo = clamped
            animStartTime = -1L
            return
        }

        animDurationMs = (durationOverrideMs ?: ANIMATION_DURATION_MS).coerceAtLeast(0L)
        animFrom = scrollPx
        animTo = clamped
        animStartTime = -1L
        animating = true
        queueFrame()
    }

    /** Resolve a drag-release into the correct target page and animate there. */
    fun finishDrag(velocityX: Float, dragStartScrollPx: Float) {
        val w = viewWidth.takeIf { it > 0 } ?: return
        val dragDeltaPages = (scrollPx - dragStartScrollPx) / w
        val forward = dragDeltaPages > 0f

        if (abs(velocityX) > minFlingVelocity) {
            val vPagesPerSec = abs(velocityX) / w
            val pages = max(1, (vPagesPerSec * FLING_WINDOW_SEC).roundToInt().coerceAtMost(3))
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
            val target = when {
                abs(dragDeltaPages) > ADVANCE_THRESHOLD && forward -> (snapStart + 1).coerceAtMost(maxLastPage())
                abs(dragDeltaPages) > ADVANCE_THRESHOLD -> (snapStart - 1).coerceAtLeast(0)
                else -> snapStart
            }
            val distPages = abs(target - scrollPx / w)
            val durationMs = (300f + 180f * distPages).coerceIn(200f, 700f).toLong()
            smoothScrollTo(target * w.toFloat(), durationOverrideMs = durationMs, fromUser = true)
        }
    }

    /** Apply a drag delta in pixels (positive = scroll right). */
    fun applyDragDelta(dx: Float) {
        scrollPx = (scrollPx - dx).coerceIn(0f, maxScrollPx())
        notifyScrollChanged()
    }

    /** Cancel any in-flight animation and return to IDLE. */
    fun cancelAnimation() {
        if (animating) {
            animating = false
            if (animPosted) choreographer.removeFrameCallback(frameCallback)
            animPosted = false
            notifyScrollState(IDLE)
        }
    }

    fun notifyScrollState(state: Int) {
        if (scrollState != state) {
            scrollState = state
            listener?.onScrollStateChanged(state)
        }
    }

    // Animation state

    private var animating = false
    private var animStartTime = -1L
    private var animDurationMs = 0L
    private var animFrom = 0f
    private var animTo = 0f
    private var animFromUser = false
    private var animPosted = false
    private val choreographer: Choreographer by lazy { Choreographer.getInstance() }

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        animPosted = false
        advanceAnimation(frameTimeNanos / 1_000_000L)
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
        val tRaw = if (animDurationMs > 0L) (elapsed.toFloat() / animDurationMs).coerceIn(0f, 1f) else 1f
        scrollPx = animFrom + (animTo - animFrom) * easeOutCubic(tRaw)
        notifyScrollChanged()
        if (tRaw < 1f) {
            queueFrame()
        } else {
            animating = false
            scrollPx = animTo
            notifyScrollChanged()
            notifyPageSettled(pageForPx(scrollPx), animFromUser)
            notifyScrollState(IDLE)
        }
    }

    private fun easeOutCubic(t: Float): Float {
        val p = t - 1f
        return p * p * p + 1f
    }

    private fun notifyScrollChanged() {
        listener?.onScrollChanged(scrollPx)
    }

    private fun notifyPageSettled(page: Int, fromUser: Boolean) {
        if (page != currentPage) {
            currentPage = page
            listener?.onPageSettled(page, fromUser)
        }
    }
}

