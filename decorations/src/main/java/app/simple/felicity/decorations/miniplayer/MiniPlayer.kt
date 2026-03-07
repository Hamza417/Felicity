package app.simple.felicity.decorations.miniplayer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.decorations.typeface.TypeFace
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme
import com.google.android.material.math.MathUtils.lerp
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A fully self-contained, flat mini-player [View].
 *
 * **Zero view hierarchy** — everything is drawn directly onto the [Canvas]:
 *  - Rounded card background (drawn with [Paint], corner radius matches theme)
 *  - Album-art bitmaps (one per page, clipped to the art slot)
 *  - Title + artist text (canvas drawText, app-font typeface, ellipsized)
 *  - Play/Pause morphing icon (same geometry as [app.simple.felicity.decorations.views.FlipPlayPauseView])
 *
 * **Scroll engine** is a verbatim port of [FelicityPager]:
 *  - [Choreographer]-driven vsync animation
 *  - `easeOutCubic` physics
 *  - Velocity-based fling with multipage advance
 *  - Advance-threshold snap on slow drag
 *
 * Layout per page (left → right):
 * ```
 * ┌──────────────────────────────────────────────────┐
 * │ [art] │ title (bold)                  │ [▶/⏸]   │
 * │       │ artist (regular)              │          │
 * └──────────────────────────────────────────────────┘
 * ```
 *
 * Wire up [callbacks] for art loading, playback control, and navigation.
 *
 * @author Hamza417
 */
class MiniPlayer @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GestureDetector.OnGestureListener, ThemeChangedListener {


    // ═══════════════════════════════════════════════════════════════════════════
    // Callbacks / public API
    // ═══════════════════════════════════════════════════════════════════════════

    interface Callbacks {
        /** User swiped to (or programmatic jump reached) [position]. */
        fun onPageSelected(position: Int) {}

        /**
         * Deliver a [Bitmap] for this page by calling [setBitmap].
         * The [payload] is the opaque object from [MiniPlayerItem].
         * The [position] lets callers guard against stale callbacks.
         */
        fun onLoadArt(position: Int, payload: Any?, setBitmap: (Bitmap?) -> Unit) {}

        /** Play/pause button tapped. */
        fun onPlayPauseClick() {}

        /** Content area tapped. */
        fun onItemClick(position: Int) {}

        /** Content area long-pressed. */
        fun onItemLongClick(position: Int) {}
    }

    var callbacks: Callbacks? = null

    // ═══════════════════════════════════════════════════════════════════════════
    // Central configuration — all tunable values live here
    // ═══════════════════════════════════════════════════════════════════════════

    /** Title text size in SP. Change via [setTitleTextSize] or [setTextSizes]. */
    private var titleTextSizeSp: Float = TITLE_TEXT_SIZE_SP

    /** Artist text size in SP. Change via [setArtistTextSize] or [setTextSizes]. */
    private var artistTextSizeSp: Float = ARTIST_TEXT_SIZE_SP

    // ── Layout proportions / spacing constants ────────────────────────────────
    /** Button zone height factor relative to view height (square = btnSz × btnSz). */
    private val btnHeightFactor = BTN_HEIGHT_FACTOR

    /** Horizontal padding on each side of the button zone in dp. */
    private val btnHorizPaddingDp = BTN_HORIZ_PADDING_DP

    /** Padding between art and text in dp. */
    private val textPaddingDp = TEXT_PADDING_DP

    /** Gap between title and artist lines in dp. */
    private val textLineGapDp = TEXT_LINE_GAP_DP

    /** Edge-fade width as a fraction of view width (clamped to a min dp). */
    private val edgeFadeWidthFraction = EDGE_FADE_WIDTH_FRACTION

    /** Minimum edge-fade width in dp. */
    private val edgeFadeMinWidthDp = EDGE_FADE_MIN_WIDTH_DP

    // ═══════════════════════════════════════════════════════════════════════════
    // Text size setters (public)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Override the title text size. Pass sp value, e.g. 14f. */
    fun setTitleTextSize(spValue: Float) {
        titleTextSizeSp = spValue
        titlePaint.textSize = sp(spValue)
        invalidate()
    }

    /** Override the artist text size. Pass sp value, e.g. 12f. */
    fun setArtistTextSize(spValue: Float) {
        artistTextSizeSp = spValue
        artistPaint.textSize = sp(spValue)
        invalidate()
    }

    /** Set both title and artist sizes in one call. */
    fun setTextSizes(titleSp: Float, artistSp: Float) {
        titleTextSizeSp = titleSp
        artistTextSizeSp = artistSp
        titlePaint.textSize = sp(titleSp)
        artistPaint.textSize = sp(artistSp)
        invalidate()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Data
    // ═══════════════════════════════════════════════════════════════════════════

    private var items: List<MiniPlayerItem> = emptyList()

    /**
     * Per-position bitmap cache.  Keyed by adapter position.
     * Entries outside the active window are cleared when pages are recycled.
     */
    private val bitmapCache = HashMap<Int, Bitmap?>()

    fun setItems(newItems: List<MiniPlayerItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun setCurrentItem(position: Int, smoothScroll: Boolean = true) {
        if (items.isEmpty()) return
        val bounded = position.coerceIn(0, items.lastIndex)
        if (width == 0) {
            post { setCurrentItem(bounded, smoothScroll) }
            return
        }
        if (!smoothScroll) {
            cancelPageAnimation()
            scrollPx = bounded * width.toFloat()
            currentPage = bounded
            ensurePageBitmaps()
            invalidate()
            dispatchPageSelected(bounded, fromUser = false)
            dispatchScrollState(SCROLL_STATE_IDLE)
        } else {
            smoothScrollTo(bounded * width.toFloat(), durationOverrideMs = null, fromUser = false)
        }
    }

    val currentItem: Int get() = currentPage.coerceAtLeast(0)

    // ═══════════════════════════════════════════════════════════════════════════
    // Scroll state  (identical model to FelicityPager)
    // ═══════════════════════════════════════════════════════════════════════════

    private var scrollPx = 0f
    private var currentPage = 0
    private var scrollState = SCROLL_STATE_IDLE

    private fun pageCount() = items.size
    private fun maxLastPage() = (pageCount() - 1).coerceAtLeast(0)
    private fun maxScrollPx() = maxLastPage() * width.toFloat()

    private fun scrollPageIndex(): Int {
        val w = width.takeIf { it > 0 } ?: return currentPage.coerceAtLeast(0)
        return (scrollPx / w).roundToInt().coerceIn(0, maxLastPage())
    }

    private fun pageForPx(px: Float): Int =
        (px / width.coerceAtLeast(1)).roundToInt().coerceIn(0, maxLastPage())

    // ═══════════════════════════════════════════════════════════════════════════
    // Page change listeners  (same interface as FelicityPager)
    // ═══════════════════════════════════════════════════════════════════════════

    interface OnPageChangeListener {
        fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
        fun onPageSelected(position: Int) {}
        fun onPageSelected(position: Int, fromUser: Boolean) {}
        fun onPageScrollStateChanged(state: Int) {}
    }

    private val pageChangeListeners = CopyOnWriteArrayList<OnPageChangeListener>()
    fun addOnPageChangeListener(l: OnPageChangeListener) = pageChangeListeners.add(l)
    fun removeOnPageChangeListener(l: OnPageChangeListener) = pageChangeListeners.remove(l)
    fun clearOnPageChangeListeners() = pageChangeListeners.clear()

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
            callbacks?.onPageSelected(position)
            pageChangeListeners.forEach { l ->
                l.onPageSelected(position, fromUser)
                l.onPageSelected(position)
            }
        }
    }

    private fun dispatchScrollState(newState: Int) {
        if (scrollState != newState) {
            scrollState = newState
            pageChangeListeners.forEach { it.onPageScrollStateChanged(newState) }
        }
    }

    private fun notifyDataSetChanged() {
        cancelPageAnimation()
        bitmapCache.clear()
        if (scrollPx > maxScrollPx()) scrollPx = maxScrollPx()
        if (currentPage > maxLastPage()) currentPage = maxLastPage()
        if (width > 0) ensurePageBitmaps()
        invalidate()
        dispatchScrolled()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Bitmap management  (radius-based window, same philosophy as FelicityPager)
    // ═══════════════════════════════════════════════════════════════════════════

    private val pageRadius = 2

    /**
     * Ensures bitmaps are requested for pages within the active window, and that
     * bitmaps outside the window are cleared to free memory.
     */
    private fun ensurePageBitmaps() {
        val count = pageCount()
        if (count == 0) return
        val center = if (width > 0) scrollPageIndex() else currentPage.coerceAtLeast(0)
        val lo = max(0, center - pageRadius)
        val hi = min(count - 1, center + pageRadius)

        // Load missing pages in window
        for (i in lo..hi) {
            if (!bitmapCache.containsKey(i)) {
                bitmapCache[i] = null  // mark as pending
                val item = items[i]
                val capturedPos = i
                callbacks?.onLoadArt(capturedPos, item.payload) { bmp ->
                    bitmapCache[capturedPos] = bmp
                    invalidate()
                }
            }
        }

        // Evict pages outside window
        val toRemove = bitmapCache.keys.filter { it !in lo..hi }
        toRemove.forEach { bitmapCache.remove(it) }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Choreographer animation engine  (verbatim port of FelicityPager)
    // ═══════════════════════════════════════════════════════════════════════════

    private val animationDurationMs: Long = 620L
    private val advanceThreshold = 0.25f
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 1.65f

    private var animating = false
    private var animStartTime = -1L
    private var animDurationPage = 0L
    private var animFrom = 0f
    private var animTo = 0f
    private var animFromUser = false
    private var animPosted = false
    private val choreographer: Choreographer by lazy { Choreographer.getInstance() }

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        animPosted = false
        advancePageAnimation(frameTimeNanos / 1_000_000L)
    }

    private fun queueFrame() {
        if (!animPosted) {
            animPosted = true
            choreographer.postFrameCallback(frameCallback)
        }
    }

    private fun smoothScrollTo(targetPx: Float, durationOverrideMs: Long?, fromUser: Boolean) {
        animFromUser = fromUser
        val clamped = targetPx.coerceIn(0f, maxScrollPx())
        if (scrollPx == clamped && !animating) {
            dispatchPageSelected(pageForPx(clamped), fromUser)
            dispatchScrollState(SCROLL_STATE_IDLE)
            return
        }
        dispatchScrollState(SCROLL_STATE_SETTLING)

        if (animating && clamped != animTo) {
            val distPx = abs(clamped - scrollPx)
            val pagesAway = distPx / width.toFloat().coerceAtLeast(1f)
            val baseDuration = durationOverrideMs ?: animationDurationMs
            val newDuration = (baseDuration * pagesAway.coerceAtLeast(0.5f))
                .toLong().coerceIn(150L, 900L)
            animFrom = scrollPx
            animTo = clamped
            animDurationPage = newDuration
            animStartTime = -1L
            return
        }

        animDurationPage = (durationOverrideMs ?: animationDurationMs).coerceAtLeast(0L)
        animFrom = scrollPx
        animTo = clamped
        animStartTime = -1L
        animating = true
        queueFrame()
    }

    private fun advancePageAnimation(nowMs: Long) {
        if (!animating) return
        if (animStartTime == -1L) animStartTime = nowMs
        val elapsed = (nowMs - animStartTime).coerceAtLeast(0L)
        val tRaw = if (animDurationPage > 0L) (elapsed.toFloat() / animDurationPage).coerceIn(0f, 1f) else 1f
        scrollPx = animFrom + (animTo - animFrom) * easeOutCubic(tRaw)
        ensurePageBitmaps()
        dispatchScrolled()
        invalidate()
        if (tRaw < 1f) {
            queueFrame()
        } else {
            animating = false
            scrollPx = animTo
            ensurePageBitmaps()
            dispatchScrolled()
            dispatchPageSelected(pageForPx(scrollPx), animFromUser)
            dispatchScrollState(SCROLL_STATE_IDLE)
            invalidate()
        }
    }

    private fun cancelPageAnimation() {
        if (animating) {
            animating = false
            if (animPosted) choreographer.removeFrameCallback(frameCallback)
            animPosted = false
            dispatchScrollState(SCROLL_STATE_IDLE)
        }
    }

    private fun easeOutCubic(t: Float): Float {
        val p = t - 1f; return p * p * p + 1f
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Touch handling  (verbatim port of FelicityPager)
    // ═══════════════════════════════════════════════════════════════════════════

    private val gestureDetector = GestureDetector(context, this)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var isBeingDragged = false
    private var lastMotionX = 0f
    private var dragStartScrollPx = 0f
    private var velocityTracker: VelocityTracker? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Route play/pause area taps separately
        if (event.actionMasked == MotionEvent.ACTION_UP && !isBeingDragged) {
            if (isInPlayPauseZone(event.x, event.y)) {
                performPlayPauseClick()
                return true
            }
        }

        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelPageAnimation()
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
                    dispatchScrollState(SCROLL_STATE_DRAGGING)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    // Animate edge fades in and slide play button out
                    animateEdgeFade(true)
                    animatePpSlide(true)
                }
                if (isBeingDragged) {
                    scrollPx = (scrollPx - dx).coerceIn(0f, maxScrollPx())
                    ensurePageBitmaps()
                    dispatchScrolled()
                    invalidate()
                }
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
                // Fade edges back out and slide play button back in
                animateEdgeFade(false)
                animatePpSlide(false)
            }
        }
        return true
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

    // GestureDetector.OnGestureListener
    override fun onDown(e: MotionEvent): Boolean = true
    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean {
        if (isInPlayPauseZone(e.x, e.y)) {
            performPlayPauseClick()
        } else {
            val w = width.takeIf { it > 0 } ?: return true
            val pageW = w - btnZoneWidth
            if (e.x < pageW) callbacks?.onItemClick(currentPage.coerceAtLeast(0))
        }
        return true
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean = false
    override fun onLongPress(e: MotionEvent) {
        if (!isInPlayPauseZone(e.x, e.y)) {
            callbacks?.onItemLongClick(currentPage.coerceAtLeast(0))
        }
    }

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

    override fun performClick(): Boolean = super.performClick()

    private fun performPlayPauseClick() {
        togglePlayPause()
        callbacks?.onPlayPauseClick()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Geometry — pre-computed in onSizeChanged, used every draw frame
    // ═══════════════════════════════════════════════════════════════════════════

    /** Width of the play/pause button zone on the right edge. */
    private var btnZoneWidth = 0f

    /** Horizontal start of the play/pause button center. */
    private var btnCentreX = 0f

    /** Vertical center of the card. */
    private var btnCentreY = 0f

    /** Side of the art-square slot. */
    private var artSize = 0f

    /** Left of the text block (after art). */
    private var textLeft = 0f

    /** Right of the text block (before button zone). */
    private var textRight = 0f

    /** Card corner radius in px. */
    private var cornerRadiusPx = 0f

    private val artRect = RectF()
    private val artClipRect = RectF()
    private val cardRect = RectF()
    private val strokeRect = RectF()
    private val artDstRect = RectF()
    private val artSrcRect = Rect()
    private val cardClipPath = Path()

    // ── Edge-fade ─────────────────────────────────────────────────────────────
    /** 0f = invisible, 1f = fully opaque fades on both sides. */
    private var edgeFadeAlpha = 0f
    private var edgeFadeAnimator: ValueAnimator? = null
    private var edgeFadeWidth = 0f

    // Pre-allocated rects for the gradient overlay (left / right)
    private val edgeFadeLeftRect = RectF()
    private val edgeFadeRightRect = RectF()

    /** DST_OUT paint — erases content below the gradient mask, producing a soft-fade edge. */
    private val edgeFadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    private fun rebuildEdgeShaders(w: Float, h: Float) {
        edgeFadeLeftRect.set(0f, 0f, edgeFadeWidth, h)
        edgeFadeRightRect.set(w - edgeFadeWidth, 0f, w, h)
    }

    private fun animateEdgeFade(show: Boolean) {
        val target = if (show) 1f else 0f
        if (edgeFadeAlpha == target && edgeFadeAnimator == null) return
        edgeFadeAnimator?.cancel()
        edgeFadeAnimator = ValueAnimator.ofFloat(edgeFadeAlpha, target).apply {
            duration = if (show) FADE_IN_MS else FADE_OUT_MS
            interpolator = if (show) AccelerateInterpolator() else DecelerateInterpolator()
            addUpdateListener {
                edgeFadeAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // ── Play/pause slide-out during drag ──────────────────────────────────────
    /** 0f = normal position, 1f = fully slid off to the right + invisible. */
    private var ppSlideOut = 0f
    private var ppSlideAnimator: ValueAnimator? = null

    private fun animatePpSlide(slideOut: Boolean) {
        val target = if (slideOut) 1f else 0f
        if (ppSlideOut == target && ppSlideAnimator == null) return
        ppSlideAnimator?.cancel()
        ppSlideAnimator = ValueAnimator.ofFloat(ppSlideOut, target).apply {
            duration = if (slideOut) PP_SLIDE_OUT_MS else PP_SLIDE_IN_MS
            interpolator = if (slideOut) AccelerateInterpolator() else DecelerateInterpolator()
            addUpdateListener {
                ppSlideOut = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // ── Animated elevation ────────────────────────────────────────────────────
    private var elevationAnimator: ValueAnimator? = null

    /**
     * Smoothly animate elevation to [targetDp] dp.
     * @param animated false = instant set.
     */
    fun animateElevation(targetDp: Float, durationMs: Long = ELEV_ANIM_MS, animated: Boolean = true) {
        elevationAnimator?.cancel()
        val targetPx = dp(targetDp)
        if (!animated || elevation == targetPx) {
            elevation = targetPx
            return
        }
        val fromPx = elevation
        elevationAnimator = ValueAnimator.ofFloat(fromPx, targetPx).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { elevation = it.animatedValue as Float }
            start()
        }
    }

    /**
     * Tint the drop-shadow color.
     */
    fun setElevationColor(@ColorInt color: Int) {
        outlineAmbientShadowColor = color
        outlineSpotShadowColor = color
    }

    private fun isInPlayPauseZone(x: Float, @Suppress("UNUSED_PARAMETER") y: Float): Boolean =
        x >= width - btnZoneWidth


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyConfig(w.toFloat(), h.toFloat())

        // Re-anchor vertical hide offset after a size change
        if (h > 0 && oldh > 0 && translationY > 0f) {
            val bm = (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
            val oldHide = oldh.toFloat() + bm
            if (oldHide > 0f) {
                val fraction = (translationY / oldHide).coerceIn(0f, 1f)
                translationY = (fraction * hideDistance).coerceIn(0f, hideDistance)
            }
        }

        if (w > 0) ensurePageBitmaps()
        applyPendingTy()
    }

    /**
     * Single authoritative place that (re)computes ALL geometry and paint state.
     * Called from the `init` block (with w=0, h=0 — only paints are configured),
     * from [onSizeChanged] (full geometry + paints), and from [onThemeChanged]
     * (colours + corner radius only, geometry left unchanged if size did not change).
     *
     * Passing `w <= 0 || h <= 0` skips the geometry recalculation so it is safe
     * to call before the view has been laid out.
     */
    private fun applyConfig(w: Float = width.toFloat(), h: Float = height.toFloat()) {
        // ── Colours from theme ─────────────────────────────────────────────
        if (!isTransparent) {
            cardColor = ThemeManager.theme.viewGroupTheme.backgroundColor
            titleColor = ThemeManager.theme.textViewTheme.primaryTextColor
            artistColor = ThemeManager.theme.textViewTheme.secondaryTextColor
            iconColor = ThemeManager.theme.iconTheme.regularIconColor
        }
        bgPaint.color = cardColor
        titlePaint.color = titleColor
        artistPaint.color = artistColor
        ppPaint.color = iconColor

        // ── Text sizes ─────────────────────────────────────────────────────
        titlePaint.textSize = sp(titleTextSizeSp)
        artistPaint.textSize = sp(artistTextSizeSp)

        // ── Typefaces ──────────────────────────────────────────────────────
        val font = AppearancePreferences.getAppFont()
        titlePaint.typeface = TypeFace.getTypeFace(font, 3 /* BOLD */, context)
        artistPaint.typeface = TypeFace.getTypeFace(font, 1 /* REGULAR */, context)

        // ── Corner radius ──────────────────────────────────────────────────
        val newRadius = AppearancePreferences.getCornerRadius()
        if (newRadius != cornerRadiusPx || cardRect.width() != w) {
            cornerRadiusPx = newRadius
            if (w > 0f && h > 0f) {
                cardClipPath.rewind()
                cardClipPath.addRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
            }
            invalidateOutline()
        }

        // ── Geometry (only when the view has a real size) ──────────────────
        if (w <= 0f || h <= 0f) return

        // Button zone: square of side btnSz, padded horizontally
        val btnSz = h * btnHeightFactor
        val btnHorizPad = dp(btnHorizPaddingDp)
        btnZoneWidth = btnSz + btnHorizPad * 2f
        btnCentreX = w - btnZoneWidth / 2f
        btnCentreY = h / 2f

        // Art slot: full-height square, left-aligned
        artSize = h
        artRect.set(0f, 0f, artSize, artSize)
        artClipRect.set(0f, 0f, artSize, artSize)

        // Text block
        val textPad = dp(textPaddingDp)
        textLeft = artSize + textPad
        textRight = w - btnZoneWidth - textPad

        // Card rects and clip path
        cardRect.set(0f, 0f, w, h)
        cardClipPath.rewind()
        cardClipPath.addRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)

        // Edge-fade width
        edgeFadeWidth = (w * edgeFadeWidthFraction).coerceAtLeast(dp(edgeFadeMinWidthDp))
        rebuildEdgeShaders(w, h)

        // Play/pause morphing geometry
        updatePlayPauseGeometry(btnSz)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Paint objects — allocated once, reused every frame
    // ═══════════════════════════════════════════════════════════════════════════

    private var cardColor: Int = ThemeManager.theme.viewGroupTheme.backgroundColor
    private var titleColor: Int = ThemeManager.theme.textViewTheme.primaryTextColor
    private var artistColor: Int = ThemeManager.theme.textViewTheme.secondaryTextColor
    private var iconColor: Int = ThemeManager.theme.iconTheme.regularIconColor

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = cardColor
    }

    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = titleColor
        // textSize is set in applyConfig() — do NOT set it here
    }

    private val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = artistColor
        // textSize is set in applyConfig() — do NOT set it here
    }

    // Placeholder art paint (when bitmap is null/loading)
    private val artPlaceholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, 128, 128, 128)
    }

    // ── Stroke ───────────────────────────────────────────────────────────────
    private var strokeEnabled = false
    private var strokeWidthPx = 0f

    @ColorInt
    private var strokeColor: Int = Color.argb(80, 128, 128, 128)

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = strokeColor
    }

    /** Enable / disable the border stroke around the mini-player. */
    fun setStrokeEnabled(enabled: Boolean) {
        strokeEnabled = enabled
        invalidate()
    }

    /** Set the stroke width in dp. */
    fun setStrokeWidth(dp: Float) {
        strokeWidthPx = this.dp(dp)
        strokePaint.strokeWidth = strokeWidthPx
        invalidate()
    }

    /** Set the stroke color. */
    fun setStrokeColor(@ColorInt color: Int) {
        strokeColor = color
        strokePaint.color = strokeColor
        invalidate()
    }

    /** One-shot convenience: enable stroke with given color and width (dp). */
    fun setStroke(enabled: Boolean, @ColorInt color: Int, widthDp: Float = 1f) {
        strokeEnabled = enabled
        strokeColor = color
        strokePaint.color = strokeColor
        strokeWidthPx = dp(widthDp)
        strokePaint.strokeWidth = strokeWidthPx
        invalidate()
    }

    // ── Play/Pause icon geometry ──────────────────────────────────────────────

    private val ppPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = iconColor
        pathEffect = CornerPathEffect(10f)
    }

    private val ppLeftPath = Path()
    private val ppRightPath = Path()

    /** 0f = playing (triangle), 1f = paused (bars) — same convention as FlipPlayPauseView. */
    private var ppProgress = 1f   // default: paused
    private var ppIsPlaying = false
    private var ppAnimator: ValueAnimator? = null

    /** Half-height of the icon shape in px. */
    private var ppH = 0f
    private var ppBarWidth = 0f
    private var ppGap = 0f
    private var ppTriHeight = 0f

    private fun updatePlayPauseGeometry(btnSz: Float) {
        ppH = btnSz * 0.5f
        ppBarWidth = ppH / 2.5f
        ppGap = ppBarWidth / 1.5f
        ppTriHeight = (sqrt(3.0) / 2.0 * ppH).toFloat()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Playback state (public)
    // ═══════════════════════════════════════════════════════════════════════════

    fun setPlaying(playing: Boolean, animate: Boolean = true) {
        if (ppIsPlaying == playing) return
        ppIsPlaying = playing
        val target = if (playing) 0f else 1f   // 0=play triangle, 1=pause bars
        ppAnimator?.cancel()
        if (animate) {
            ppAnimator = ValueAnimator.ofFloat(ppProgress, target).apply {
                duration = 300L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    ppProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            ppProgress = target
            invalidate()
        }
    }

    private fun togglePlayPause() {
        setPlaying(!ppIsPlaying)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Transparency
    // ═══════════════════════════════════════════════════════════════════════════

    private var isTransparent = false
    private var opaqueCardColor: Int = cardColor
    private var bgColorAnimator: ValueAnimator? = null

    // ═══════════════════════════════════════════════════════════════════════════
    // init — single startup entry-point, runs after ALL properties are initialized
    // ═══════════════════════════════════════════════════════════════════════════

    init {
        // Configure paint colours, typefaces, and text sizes from the central
        // applyConfig() method. Geometry is deferred to onSizeChanged because
        // width/height are 0 at this point.
        applyConfig()
    }

    fun makeTransparent(animated: Boolean = true) {
        if (isTransparent) return
        isTransparent = true
        opaqueCardColor = cardColor
        // Elevation → 0 first, then fade background out
        animateElevation(0f, ANIM_DURATION_MS, animated)
        animateBgColor(cardColor, Color.TRANSPARENT, animated)
        animateTextIcon(Color.WHITE, Color.WHITE, Color.WHITE, animated)
    }

    fun makeOpaque(animated: Boolean = true) {
        if (!isTransparent) return
        isTransparent = false
        val targetBg = if (opaqueCardColor != Color.TRANSPARENT) opaqueCardColor
        else ThemeManager.theme.viewGroupTheme.backgroundColor
        // Step 1: animate background back in; Step 2: once bg is restored, bring elevation back
        animateBgColor(Color.TRANSPARENT, targetBg, animated, onEnd = {
            animateElevation(DEFAULT_ELEVATION_DP, ELEV_ANIM_MS, animated)
        })
        animateTextIcon(
                ThemeManager.theme.textViewTheme.primaryTextColor,
                ThemeManager.theme.textViewTheme.secondaryTextColor,
                ThemeManager.theme.iconTheme.regularIconColor,
                animated)
    }

    private fun animateBgColor(from: Int, to: Int, animated: Boolean, onEnd: (() -> Unit)? = null) {
        bgColorAnimator?.cancel()
        if (!animated || from == to) {
            cardColor = to
            bgPaint.color = to
            invalidate()
            onEnd?.invoke()
            return
        }
        bgColorAnimator = ValueAnimator.ofArgb(from, to).apply {
            duration = ANIM_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                cardColor = it.animatedValue as Int
                bgPaint.color = cardColor
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd?.invoke()
                }
            })
            start()
        }
    }

    private fun animateTextIcon(newTitle: Int, newArtist: Int, newIcon: Int, animated: Boolean) {
        if (!animated) {
            titleColor = newTitle; titlePaint.color = newTitle
            artistColor = newArtist; artistPaint.color = newArtist
            iconColor = newIcon; ppPaint.color = newIcon
            invalidate()
            return
        }
        val oldTitle = titleColor
        val oldArtist = artistColor
        val oldIcon = iconColor
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIM_DURATION_MS
            addUpdateListener { va ->
                val f = va.animatedFraction
                titleColor = blend(oldTitle, newTitle, f); titlePaint.color = titleColor
                artistColor = blend(oldArtist, newArtist, f); artistPaint.color = artistColor
                iconColor = blend(oldIcon, newIcon, f); ppPaint.color = iconColor
                invalidate()
            }
            start()
        }
    }

    private fun blend(from: Int, to: Int, f: Float): Int {
        val t = f.coerceIn(0f, 1f)
        return Color.argb(
                (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t).toInt(),
                (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
                (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
                (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt()
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Drawing
    // ═══════════════════════════════════════════════════════════════════════════

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // ── 1. Card background ─────────────────────────────────────────────
        bgPaint.color = cardColor
        canvas.drawRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, bgPaint)

        // ── 2. Stroke border (optional) ────────────────────────────────────
        if (strokeEnabled && strokeWidthPx > 0f) {
            val inset = strokeWidthPx / 2f
            strokeRect.set(
                    cardRect.left + inset,
                    cardRect.top + inset,
                    cardRect.right - inset,
                    cardRect.bottom - inset
            )
            canvas.drawRoundRect(strokeRect, (cornerRadiusPx - inset).coerceAtLeast(0f),
                                 (cornerRadiusPx - inset).coerceAtLeast(0f), strokePaint)
        }

        // ── 3. Pages + edge fades ──────────────────────────────────────────
        val pageW = w.toInt()
        if (pageW > 0 && items.isNotEmpty()) {
            if (edgeFadeAlpha > 0f) {
                // Isolate onto its own layer so DST_OUT only erases content,
                // never the card background that was already drawn below.
                canvas.saveLayer(cardRect, null)
                canvas.clipPath(cardClipPath)
                drawPages(canvas, pageW, h)
                drawEdgeFades(canvas)
                canvas.restore()
            } else {
                canvas.withClip(cardClipPath) {
                    drawPages(this, pageW, h)
                }
            }
        }

        // ── 4. Play/Pause button (slides out during drag) ──────────────────
        drawPlayPause(canvas)
    }

    private fun drawPages(canvas: Canvas, pageW: Int, h: Float) {
        val count = items.size
        val scrollF = scrollPx / pageW   // fractional page position
        val centerPage = scrollF.toInt().coerceIn(0, count - 1)

        // Draw the two pages that may be simultaneously visible
        // (the current one and the one it's sliding to/from)
        val lo = max(0, centerPage - 1)
        val hi = min(count - 1, centerPage + 1)

        for (i in lo..hi) {
            // translationX for page i: page 0 lives at 0, page 1 at pageW, etc.
            val tx = i * pageW.toFloat() - scrollPx
            if (tx >= pageW || tx <= -pageW.toFloat()) continue   // off-screen

            canvas.save()
            canvas.clipRect(tx, 0f, tx + pageW, h)

            val item = items[i]
            val bmp = bitmapCache[i]

            // ── Art ─────────────────────────────────────────────────────────
            canvas.save()
            canvas.clipRect(tx, 0f, tx + artSize, h)
            if (bmp != null && !bmp.isRecycled) {
                artSrcRect.set(0, 0, bmp.width, bmp.height)
                artDstRect.set(tx, 0f, tx + artSize, h)
                canvas.drawBitmap(bmp, artSrcRect, artDstRect, bmpPaint)
            } else {
                artDstRect.set(tx, 0f, tx + artSize, h)
                canvas.drawRect(artDstRect, artPlaceholderPaint)
            }
            canvas.restore()

            // ── Text ─────────────────────────────────────────────────────────
            val tLeft = tx + textLeft
            val tRight = tx + textRight
            if (tRight > tLeft) {
                drawPageText(canvas, item.title, item.artist, tLeft, tRight, h)
            }

            canvas.restore()
        }
    }

    private fun drawPageText(
            canvas: Canvas,
            title: String,
            artist: String,
            left: Float,
            right: Float,
            h: Float
    ) {
        val maxW = right - left
        val tm = titlePaint.fontMetrics
        val am = artistPaint.fontMetrics

        // Height of each text line = descent - ascent  (ascent is negative)
        val titleLineH = tm.descent - tm.ascent
        val artistLineH = am.descent - am.ascent
        val gap = dp(textLineGapDp)

        // Total height of the two-line block (tight, like a vertical LinearLayout wrap_content)
        val blockH = titleLineH + gap + artistLineH

        // Top of the block so that it is vertically centered in the view
        val blockTop = (h - blockH) / 2f

        // Baseline of each line = top-of-line + (-ascent)
        val titleBaseline = blockTop + (-tm.ascent)
        val artistBaseline = blockTop + titleLineH + gap + (-am.ascent)

        canvas.drawText(ellipsize(title, titlePaint, maxW), left, titleBaseline, titlePaint)
        canvas.drawText(ellipsize(artist, artistPaint, maxW), left, artistBaseline, artistPaint)
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (text.isEmpty() || paint.measureText(text) <= maxWidth) return text
        val ellipsis = "\u2026"
        val ew = paint.measureText(ellipsis)
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + ew > maxWidth) end--
        return text.substring(0, end) + ellipsis
    }

    /**
     * Draws the morphing play/pause icon into the button zone,
     * replicating the exact geometry of [app.simple.felicity.decorations.views.FlipPlayPauseView].
     * While [ppSlideOut] > 0 the icon translates right and fades out.
     */
    private fun drawPlayPause(canvas: Canvas) {
        val h = ppH
        val barWidth = ppBarWidth
        val gap = ppGap
        val triHeight = ppTriHeight
        val progress = ppProgress

        ppLeftPath.rewind()
        ppRightPath.rewind()

        // Right pause bar
        val rightBarX = barWidth + gap
        ppRightPath.moveTo(rightBarX, 0f); ppRightPath.lineTo(rightBarX + barWidth, 0f)
        ppRightPath.lineTo(rightBarX + barWidth, h); ppRightPath.lineTo(rightBarX, h)
        ppRightPath.close()

        // Left morphing shape
        if (progress >= 0.9f) {
            ppLeftPath.moveTo(0f, 0f)
            ppLeftPath.lineTo(triHeight, h / 2f)
            ppLeftPath.lineTo(0f, h)
            ppLeftPath.close()
        } else {
            val tipX = lerp(barWidth, triHeight, progress)
            val topY = lerp(0f, h / 2f, progress)
            val bottomY = lerp(h, h / 2f, progress)
            ppLeftPath.moveTo(0f, 0f)
            ppLeftPath.lineTo(tipX, topY); ppLeftPath.lineTo(tipX, bottomY)
            ppLeftPath.lineTo(0f, h); ppLeftPath.close()
        }

        // Slide-out: shift right by ppSlideOut * btnZoneWidth, fade to 0
        val slideOffsetPx = ppSlideOut * btnZoneWidth
        val buttonAlpha = ((1f - ppSlideOut) * 255f).toInt().coerceIn(0, 255)
        if (buttonAlpha == 0) return   // fully gone — skip draw

        canvas.withTranslation(btnCentreX + slideOffsetPx, btnCentreY) {
            val totalPauseWidth = barWidth * 2 + gap
            val totalPlayWidth = triHeight
            val offsetPause = -totalPauseWidth / 2f
            val offsetPlay = -totalPlayWidth / 2f + barWidth * 0.1f
            val offsetX = lerp(offsetPause, offsetPlay, progress)

            translate(offsetX, -h / 2f)
            ppPaint.color = iconColor
            ppPaint.alpha = buttonAlpha
            drawPath(ppLeftPath, ppPaint)
            if (progress < 1f) {
                ppPaint.alpha = ((1f - progress) * buttonAlpha).toInt().coerceIn(0, 255)
                drawPath(ppRightPath, ppPaint)
            }
            ppPaint.alpha = 255
        }
    }

    /**
     * Draws left and/or right horizontal fade masks using DST_OUT blending.
     * Must be called inside a [Canvas.saveLayer] block.
     * Only draws a side when there is content scrollable in that direction.
     */
    private fun drawEdgeFades(canvas: Canvas) {
        if (edgeFadeAlpha <= 0f) return
        val alpha = (edgeFadeAlpha * 255f).toInt().coerceIn(1, 255)

        val canScrollLeft = scrollPx > 0.5f
        val canScrollRight = scrollPx < maxScrollPx() - 0.5f

        if (canScrollLeft) {
            edgeFadePaint.shader = LinearGradient(
                    edgeFadeLeftRect.left, 0f, edgeFadeLeftRect.right, 0f,
                    intArrayOf(Color.argb(alpha, 0, 0, 0), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP)
            canvas.drawRect(edgeFadeLeftRect, edgeFadePaint)
        }

        if (canScrollRight) {
            edgeFadePaint.shader = LinearGradient(
                    edgeFadeRightRect.left, 0f, edgeFadeRightRect.right, 0f,
                    intArrayOf(Color.TRANSPARENT, Color.argb(alpha, 0, 0, 0)),
                    null, Shader.TileMode.CLAMP)
            canvas.drawRect(edgeFadeRightRect, edgeFadePaint)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Show / Hide / RecyclerView scroll-hide
    // (identical logic to the old MiniPlayer, preserved completely)
    // ═══════════════════════════════════════════════════════════════════════════

    private val attached: MutableMap<RecyclerView, RecyclerView.OnScrollListener> = mutableMapOf()
    private val showInterpolator = DecelerateInterpolator()
    private val hideInterpolator = AccelerateInterpolator()
    private val slideInterpolator = AccelerateDecelerateInterpolator()
    private val epsilon = 1f
    private var baseSideMarginPx: Int = 0
    private var navBarInsetPx: Int = 0
    private var pendingRestoreTranslationY: Float? = null
    private var pendingRestoreFraction: Float? = null
    private var suppressAutoFromRecyclerUntilIdle = false
    private var isManuallyControlled = false
    private var hadImmersiveDrag = false
    private val resetManualHandler = Handler(Looper.getMainLooper())
    private val resetManualRunnable = Runnable { isManuallyControlled = false }

    private val hideDistance: Float
        get() {
            val bm = (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
            return height.toFloat() + bm
        }

    private fun isFullyShown() = translationY <= epsilon
    private fun isFullyHidden() = abs(translationY - hideDistance) <= epsilon

    fun show(animated: Boolean = true) {
        animate().cancel()
        visibility = VISIBLE
        alpha = 1f
        pendingRestoreFraction = null
        pendingRestoreTranslationY = null
        suppressAutoFromRecyclerUntilIdle = true
        isManuallyControlled = true
        if (height == 0) {
            addOnLayoutChangeListener(object : OnLayoutChangeListener {
                override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or2: Int, ob: Int) {
                    removeOnLayoutChangeListener(this); show(animated)
                }
            }); return
        }
        animateTy(0f, animated)
    }

    fun hide(animated: Boolean = true) {
        animate().cancel()
        visibility = VISIBLE
        pendingRestoreFraction = null
        pendingRestoreTranslationY = null
        suppressAutoFromRecyclerUntilIdle = true
        isManuallyControlled = true
        if (height == 0) {
            addOnLayoutChangeListener(object : OnLayoutChangeListener {
                override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or2: Int, ob: Int) {
                    removeOnLayoutChangeListener(this); hide(animated)
                }
            }); return
        }
        animateTy(hideDistance, animated)
    }

    @Suppress("unused")
    fun offsetBy(dy: Int) = updateForScrollDelta(dy)

    @Suppress("unused")
    fun snapToShown(animated: Boolean = true) = animateTy(0f, animated)

    @Suppress("unused")
    fun snapToHidden(animated: Boolean = true) = animateTy(hideDistance, animated)

    private fun animateTy(target: Float, animated: Boolean) {
        if (!animated) {
            translationY = target
            suppressAutoFromRecyclerUntilIdle = false
            resetManualHandler.removeCallbacks(resetManualRunnable)
            resetManualHandler.postDelayed(resetManualRunnable, 500)
            return
        }
        animate().translationY(target).setDuration(ANIM_DURATION_MS)
            .setInterpolator(slideInterpolator)
            .withEndAction {
                suppressAutoFromRecyclerUntilIdle = false
                resetManualHandler.removeCallbacks(resetManualRunnable)
                resetManualHandler.postDelayed(resetManualRunnable, 500)
            }.start()
    }

    private fun updateForScrollDelta(dy: Int) {
        if (height == 0 || suppressAutoFromRecyclerUntilIdle || isManuallyControlled) return
        animate().cancel()
        val target = (translationY + dy).coerceIn(0f, hideDistance)
        if (target != translationY) translationY = target
    }

    private fun applyPendingTy() {
        if (height <= 0) {
            addOnLayoutChangeListener(object : OnLayoutChangeListener {
                override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or2: Int, ob: Int) {
                    removeOnLayoutChangeListener(this); applyPendingTy()
                }
            }); return
        }
        var applied = false
        pendingRestoreFraction?.let { f ->
            animate().cancel()
            translationY = when {
                f >= 0.995f -> hideDistance; f <= 0.005f -> 0f
                else -> (f * hideDistance).coerceIn(0f, hideDistance)
            }
            applied = true
        }
        if (!applied) pendingRestoreTranslationY?.let { animate().cancel(); translationY = it.coerceIn(0f, hideDistance) }
        pendingRestoreFraction = null
        pendingRestoreTranslationY = null
    }

    private fun isRvScrollable(rv: RecyclerView): Boolean {
        if (rv.canScrollVertically(1) || rv.canScrollVertically(-1)) return true
        return try {
            rv.computeVerticalScrollRange() > rv.computeVerticalScrollExtent()
        } catch (_: Exception) {
            false
        }
    }

    fun attachToRecyclerView(recyclerView: RecyclerView) {
        if (attached.containsKey(recyclerView)) return
        val listener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (!isRvScrollable(rv)) return
                updateForScrollDelta(dy)
            }

            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (isManuallyControlled) return
                val scrollable = isRvScrollable(rv)
                if (!scrollable) {
                    when (newState) {
                        RecyclerView.SCROLL_STATE_DRAGGING -> {
                            hadImmersiveDrag = true
                            if (!isFullyHidden()) animate().translationY(hideDistance).setDuration(250).setInterpolator(hideInterpolator).start()
                        }
                        RecyclerView.SCROLL_STATE_SETTLING, RecyclerView.SCROLL_STATE_IDLE -> {
                            if (hadImmersiveDrag && !isFullyShown()) {
                                animate().translationY(0f).setDuration(250).setInterpolator(showInterpolator).withEndAction { hadImmersiveDrag = false }.start()
                            } else if (newState == RecyclerView.SCROLL_STATE_IDLE) hadImmersiveDrag = false
                        }
                    }
                    suppressAutoFromRecyclerUntilIdle = false; return
                }
                if (suppressAutoFromRecyclerUntilIdle && newState == RecyclerView.SCROLL_STATE_DRAGGING) suppressAutoFromRecyclerUntilIdle = false
                if (suppressAutoFromRecyclerUntilIdle) return
                when (newState) {
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (isFullyShown() || isFullyHidden()) return
                        if (translationY <= hideDistance / 2f) animate().translationY(0f).setDuration(250).setInterpolator(showInterpolator).start()
                        else animate().translationY(hideDistance).setDuration(250).setInterpolator(hideInterpolator).start()
                    }
                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        if (isFullyShown()) animate().translationY(hideDistance).setDuration(250).setInterpolator(hideInterpolator).start()
                    }
                }
            }
        }
        recyclerView.addOnScrollListener(listener)
        attached[recyclerView] = listener
        suppressAutoFromRecyclerUntilIdle = false
    }

    @Suppress("unused")
    fun attachToRecyclerViews(vararg rvs: RecyclerView) = rvs.forEach { attachToRecyclerView(it) }

    fun detachFromRecyclerView(rv: RecyclerView) = attached.remove(rv)?.let { rv.removeOnScrollListener(it) }

    @Suppress("unused")
    fun detachFromRecyclerViews(vararg rvs: RecyclerView) = rvs.forEach { detachFromRecyclerView(it) }

    fun detachFromAllRecyclerViews() {
        attached.forEach { (rv, l) -> rv.removeOnScrollListener(l) }; attached.clear()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Theme
    // ═══════════════════════════════════════════════════════════════════════════

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        if (isTransparent) return   // stay white until makeOpaque()
        applyConfig()
        setCustomizations()
        invalidate()
    }

    override fun onAccentChanged(accent: Accent) {
        super.onAccentChanged(accent)
        setCustomizations()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ThemeManager.addListener(this)
        suppressAutoFromRecyclerUntilIdle = false
        resetManualHandler.removeCallbacks(resetManualRunnable)
        isManuallyControlled = false
        hadImmersiveDrag = false

        // Elevation + rounded shadow outline
        elevation = dp(DEFAULT_ELEVATION_DP)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
            }
        }
        clipToOutline = false   // we clip art ourselves; don't clip the shadow

        setCustomizations()

        // Margin / inset wiring
        post {
            val lp = layoutParams as? ViewGroup.MarginLayoutParams ?: return@post
            val m = dp(15f).toInt()
            baseSideMarginPx = m
            lp.setMargins(m, m, m, m + navBarInsetPx)
            layoutParams = lp
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            navBarInsetPx = nav.bottom
            val lp = v.layoutParams as? ViewGroup.MarginLayoutParams ?: return@setOnApplyWindowInsetsListener insets
            lp.bottomMargin = baseSideMarginPx + navBarInsetPx
            v.layoutParams = lp
            insets
        }
    }

    override fun onDetachedFromWindow() {
        ThemeManager.removeListener(this)
        detachFromAllRecyclerViews()
        cancelPageAnimation()
        ppAnimator?.cancel()
        ppSlideAnimator?.cancel()
        bgColorAnimator?.cancel()
        elevationAnimator?.cancel()
        edgeFadeAnimator?.cancel()
        resetManualHandler.removeCallbacks(resetManualRunnable)
        isManuallyControlled = false
        hadImmersiveDrag = false
        super.onDetachedFromWindow()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // State save / restore
    // ═══════════════════════════════════════════════════════════════════════════

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val fraction = if (hideDistance > 0f) (translationY / hideDistance).coerceIn(0f, 1f) else 0f
        return SavedState(superState).also {
            it.savedTranslationY = translationY
            it.fraction = fraction
            it.isTransparent = isTransparent
            it.isPlaying = ppIsPlaying
            it.currentPage = this.currentPage
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            pendingRestoreFraction = state.fraction.takeIf { it in 0f..1f }
            if (pendingRestoreFraction == null) pendingRestoreTranslationY = state.savedTranslationY
            if (state.isTransparent) makeTransparent(animated = false)
            currentPage = state.currentPage.coerceAtLeast(0)
            ppIsPlaying = state.isPlaying
            ppProgress = if (ppIsPlaying) 0f else 1f
            applyPendingTy()
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    internal class SavedState : BaseSavedState {
        var savedTranslationY: Float = 0f
        var fraction: Float = -1f
        var isTransparent: Boolean = false
        var isPlaying: Boolean = false
        var currentPage: Int = 0

        constructor(superState: Parcelable?) : super(superState)
        constructor(source: Parcel) : super(source) {
            savedTranslationY = source.readFloat()
            fraction = source.readFloat()
            isTransparent = source.readInt() != 0
            isPlaying = source.readInt() != 0
            currentPage = source.readInt()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeFloat(savedTranslationY)
            out.writeFloat(fraction)
            out.writeInt(if (isTransparent) 1 else 0)
            out.writeInt(if (isPlaying) 1 else 0)
            out.writeInt(currentPage)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(source: Parcel) = SavedState(source)
            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }

    private fun setCustomizations() {
        if (isInEditMode.not()) {
            if (AppearancePreferences.isStrokeAroundMiniplayerOn()) {
                setStroke(
                        enabled = true,
                        color = ThemeManager.theme.textViewTheme.tertiaryTextColor,
                        widthDp = 1f
                )
            } else {
                setStrokeEnabled(false)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private fun dp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    companion object {
        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SETTLING = 2
        private const val ANIM_DURATION_MS = 180L

        /** Fast fade-in when drag starts (edges appear quickly). */
        private const val FADE_IN_MS = 240L

        /** Slower fade-out after drag ends (edges dissolve gently). */
        private const val FADE_OUT_MS = 540L

        /** Play button slides out during drag. */
        private const val PP_SLIDE_OUT_MS = 130L

        /** Play button slides back in after drag. */
        private const val PP_SLIDE_IN_MS = 280L

        /** Default elevation of the card in dp. */
        private const val DEFAULT_ELEVATION_DP = 24f

        /** Duration for elevation animation. */
        private const val ELEV_ANIM_MS = 220L

        // ── Layout constants (all in one place — change here to tune the look) ──

        /** Title text size in SP. */
        private const val TITLE_TEXT_SIZE_SP = 18f

        /** Artist text size in SP. */
        private const val ARTIST_TEXT_SIZE_SP = 12f

        /** Button zone height as a fraction of the view height. */
        private const val BTN_HEIGHT_FACTOR = 0.55f

        /** Horizontal padding on each side of the button zone, in dp. */
        private const val BTN_HORIZ_PADDING_DP = 12f

        /** Horizontal padding between the art square and the text block, in dp. */
        private const val TEXT_PADDING_DP = 8f

        /** Gap between the title and artist text lines, in dp. */
        private const val TEXT_LINE_GAP_DP = 3f

        /** Edge-fade width as a fraction of the view width. */
        private const val EDGE_FADE_WIDTH_FRACTION = 0.15f

        /** Minimum edge-fade width in dp (clamp floor). */
        private const val EDGE_FADE_MIN_WIDTH_DP = 48f
    }
}


