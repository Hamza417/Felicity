package app.simple.felicity.decorations.miniplayer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.os.VibrationEffect
import android.util.AttributeSet
import android.util.Log
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginBottom
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.decorations.itemdecorations.FooterSpacingItemDecoration
import app.simple.felicity.decorations.miniplayer.MiniPlayer.Companion.ARTIST_TEXT_SIZE_SP
import app.simple.felicity.decorations.miniplayer.MiniPlayer.Companion.DEFAULT_ELEVATION_DP
import app.simple.felicity.decorations.miniplayer.MiniPlayer.Companion.PAINT_SHADOW_DY_DP
import app.simple.felicity.decorations.miniplayer.MiniPlayer.Companion.PAINT_SHADOW_RADIUS_DP
import app.simple.felicity.decorations.miniplayer.MiniPlayer.Companion.TITLE_TEXT_SIZE_SP
import app.simple.felicity.decorations.ripple.FelicityRippleDrawable
import app.simple.felicity.decorations.typeface.TypeFace
import app.simple.felicity.decorations.utils.VibrateUtils.vibrateEffect
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.AccessibilityPreferences
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.preferences.BehaviourPreferences
import app.simple.felicity.preferences.UserInterfacePreferences
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A fully self-contained, flat mini-player [View].
 *
 * Everything is drawn directly onto the [Canvas] — no child views:
 *  - Rounded card background with optional stroke border
 *  - Album-art bitmaps clipped to a square slot on the left
 *  - Two-line title + artist text with automatic ellipsis
 *  - Morphing play/pause icon on the right (via [MiniPlayerPlayPauseDrawer])
 *  - Optional edge-fade gradient that appears during swipe gestures
 *
 * Paging is handled by [MiniPlayerScrollEngine], which provides the same
 * vsync-driven easeOutCubic physics as FelicityPager.
 *
 * Wire up [callbacks] for art loading, playback control, and navigation.
 *
 * @see MiniPlayerItem
 * @see MiniPlayerScrollEngine
 * @see MiniPlayerPlayPauseDrawer
 * @author Hamza417
 */
class MiniPlayer @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr),
    GestureDetector.OnGestureListener,
    ThemeChangedListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    // -------------------------------------------------------------------------
    // Public callbacks
    // -------------------------------------------------------------------------

    /** Event callbacks for art loading, playback control, and navigation. */
    interface Callbacks {
        /**
         * The visible page settled at [position].
         *
         * @param position The new page index.
         * @param fromUser `true` when the change was caused by a real user swipe or
         *                 gesture; `false` when it was triggered programmatically
         *                 (e.g. [setCurrentItem]).  Callers that drive external
         *                 playback state (e.g. MediaManager) should ignore
         *                 events where [fromUser] is `false` to avoid feedback loops.
         */
        fun onPageSelected(position: Int, fromUser: Boolean) {}

        /**
         * Request artwork for [position]. Call [setBitmap] with the result (maybe null).
         * [payload] is the opaque object from [MiniPlayerItem]; [position] lets you guard
         * against stale responses.
         */
        fun onLoadArt(position: Int, payload: Any?, setBitmap: (Bitmap?) -> Unit) {}

        /** The play/pause button was tapped. */
        fun onPlayPauseClick() {}

        /** The content area of [position] was tapped. */
        fun onItemClick(position: Int) {}

        /** The content area of [position] was long-pressed. */
        fun onItemLongClick(position: Int) {}
    }

    var callbacks: Callbacks? = null

    // -------------------------------------------------------------------------
    // Page-change listener (same interface contract as FelicityPager)
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Scroll state constants (mirrors RecyclerView / ViewPager naming)
    // -------------------------------------------------------------------------

    companion object {
        private const val TAG = "MiniPlayer"

        const val SCROLL_STATE_IDLE = MiniPlayerScrollEngine.IDLE
        const val SCROLL_STATE_DRAGGING = MiniPlayerScrollEngine.DRAGGING
        const val SCROLL_STATE_SETTLING = MiniPlayerScrollEngine.SETTLING

        /** Default card elevation in dp. */
        private const val DEFAULT_ELEVATION_DP = 24f

        /** Duration for show/hide slide animation in ms. */
        private const val ANIM_DURATION_MS = 180L

        /**
         * How long after [MotionEvent.ACTION_DOWN] the ripple is automatically released.
         * Keeping this short makes the ripple a non-blocking cherry-on-top effect that
         * plays out its own expand+fade without any gesture state cancelling it.
         *
         * @author Hamza417
         */
        private const val RIPPLE_AUTO_RELEASE_MS = 80L

        /** Duration for the elevation change animation in ms. */
        private const val ELEV_ANIM_MS = 220L

        /** Edge-fade appears quickly when a drag starts. */
        private const val EDGE_FADE_IN_MS = 240L

        /** Edge-fade dissolves slowly after drag ends. */
        private const val EDGE_FADE_OUT_MS = 540L

        /** Play button slides off-screen fast. */
        private const val PP_SLIDE_OUT_MS = 130L

        /** Play button slides back in leisurely. */
        private const val PP_SLIDE_IN_MS = 280L

        // Layout constants — change these to retune the look without touching logic

        /** Default title text size in SP. */
        private const val TITLE_TEXT_SIZE_SP = 16f

        /** Default artist text size in SP. */
        private const val ARTIST_TEXT_SIZE_SP = 12f

        /** Button zone square side as a fraction of the view height. */
        private const val BTN_HEIGHT_FACTOR = 0.55f

        /** Horizontal padding on each side of the button zone, in dp. */
        private const val BTN_HORIZ_PADDING_DP = 12f

        /** Padding between the art slot and the text block, in dp. */
        private const val TEXT_PADDING_DP = 8f

        /** Gap between the title and artist text lines, in dp. */
        private const val TEXT_LINE_GAP_DP = 3f

        /** Edge-fade width as a fraction of the view width. */
        private const val EDGE_FADE_WIDTH_FRACTION = 0.15f

        /** Minimum edge-fade width in dp (clamp floor). */
        private const val EDGE_FADE_MIN_WIDTH_DP = 48f

        /** Margin applied around the view on all sides when attached to a window, in dp. */
        private const val SIDE_MARGIN_DP = 15f

        /**
         * How long (ms) to wait after the last [NestedScrollView] scroll event before
         * snapping the player to fully shown or fully hidden, mimicking the
         * [RecyclerView.SCROLL_STATE_IDLE] snap that the RecyclerView path uses.
         */
        private const val SCROLL_SNAP_DELAY_MS = 150L

        /** Blur radius in dp used by the paint-shadow card background. */
        private const val PAINT_SHADOW_RADIUS_DP = 10f

        /** Downward Y offset in dp for the paint-shadow card background. */
        private const val PAINT_SHADOW_DY_DP = 0f
    }

    // -------------------------------------------------------------------------
    // Data / items
    // -------------------------------------------------------------------------

    private var items: List<MiniPlayerItem> = emptyList()

    /**
     * Bitmap cache keyed by adapter position.
     * Entries outside the ±[PAGE_RADIUS] window are evicted to free memory.
     */
    private val bitmapCache = HashMap<Int, Bitmap?>()
    private val PAGE_RADIUS = 2

    /** Replace the full data set and reset the scroll position. */
    fun setItems(newItems: List<MiniPlayerItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    /**
     * Scroll to [position], optionally animated.
     * If the view has not been laid out yet the call is deferred via [post].
     */
    fun setCurrentItem(position: Int, smoothScroll: Boolean = true) {
        if (items.isEmpty()) return
        val bounded = position.coerceIn(0, items.lastIndex)
        if (width == 0) {
            post { setCurrentItem(bounded, smoothScroll) }
            return
        }
        if (!smoothScroll) {
            scrollEngine.jumpToPage(bounded)
            ensurePageBitmaps()
            invalidate()
            dispatchPageSelected(bounded, fromUser = false)
        } else {
            scrollEngine.smoothScrollTo(bounded * width.toFloat(), fromUser = false)
        }
    }

    /** The currently visible page index (0-based). */
    val currentItem: Int get() = scrollEngine.currentPage.coerceAtLeast(0)

    // -------------------------------------------------------------------------
    // Scroll engine
    // -------------------------------------------------------------------------

    private val scrollEngine = MiniPlayerScrollEngine(context).apply {
        listener = object : MiniPlayerScrollEngine.Listener {
            override fun onScrollChanged(scrollPx: Float) {
                ensurePageBitmaps()
                dispatchScrolled()
                invalidate()
            }

            override fun onPageSettled(page: Int, fromUser: Boolean) {
                dispatchPageSelected(page, fromUser)
                // Cancel any in-flight progress tween and reset to 0 so the freshly
                // settled page always starts clean.  The caller will push the real
                // seek position shortly via setProgress(), animating from 0 if needed.
                progressValueAnimator?.cancel()
                progress = 0f
                // Now fade the progress bar back in
                if (!isInSeekMode) animateProgressAlpha(1f)
            }

            override fun onScrollStateChanged(state: Int) {
                dispatchScrollState(state)
                // Fade the progress bar out the moment the user starts swiping so it
                // doesn't appear to lag behind or look disconnected from the motion
                if (state == SCROLL_STATE_DRAGGING && !isInSeekMode) {
                    animateProgressAlpha(0f)
                }
            }
        }
    }

    private fun dispatchScrolled() {
        val w = width.takeIf { it > 0 } ?: return
        val posF = scrollEngine.scrollPx / w
        val pos = posF.toInt().coerceIn(0, maxLastPage())
        val offset = (posF - pos).coerceIn(0f, 1f)
        val px = (offset * w).toInt()
        pageChangeListeners.forEach { it.onPageScrolled(pos, offset, px) }
    }

    private fun dispatchPageSelected(position: Int, fromUser: Boolean) {
        callbacks?.onPageSelected(position, fromUser)
        pageChangeListeners.forEach { l ->
            l.onPageSelected(position, fromUser)
            l.onPageSelected(position)
        }
    }

    private fun dispatchScrollState(newState: Int) {
        pageChangeListeners.forEach { it.onPageScrollStateChanged(newState) }
    }

    private fun notifyDataSetChanged() {
        scrollEngine.cancelAnimation()
        bitmapCache.clear()
        scrollEngine.pageCount = items.size
        scrollEngine.clampScrollPx()
        scrollEngine.clampCurrentPage()
        if (width > 0) ensurePageBitmaps()
        // Snap progress to 0 — a fresh queue has no known seek position yet
        progressValueAnimator?.cancel()
        progress = 0f
        invalidate()
        dispatchScrolled()
    }

    private fun pageCount() = items.size
    private fun maxLastPage() = (pageCount() - 1).coerceAtLeast(0)

    // -------------------------------------------------------------------------
    // Bitmap management
    // -------------------------------------------------------------------------

    /**
     * Ensures bitmaps are requested for pages in the ±[PAGE_RADIUS] window
     * centred on the current scroll position, and evicts everything outside.
     */
    private fun ensurePageBitmaps() {
        val count = pageCount()
        if (count == 0) return
        val center = if (width > 0) scrollEngine.scrollPageIndex()
        else scrollEngine.currentPage.coerceAtLeast(0)
        val lo = max(0, center - PAGE_RADIUS)
        val hi = min(count - 1, center + PAGE_RADIUS)

        for (i in lo..hi) {
            if (!bitmapCache.containsKey(i)) {
                bitmapCache[i] = null // placeholder while loading
                val capturedPos = i
                callbacks?.onLoadArt(capturedPos, items[i].payload) { bmp ->
                    bitmapCache[capturedPos] = bmp
                    invalidate()
                }
            }
        }

        bitmapCache.keys.filter { it !in lo..hi }.forEach { bitmapCache.remove(it) }
    }

    // -------------------------------------------------------------------------
    // Touch handling
    // -------------------------------------------------------------------------

    private val gestureDetector = GestureDetector(context, this)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var isBeingDragged = false
    private var lastMotionX = 0f
    private var dragStartScrollPx = 0f
    private var velocityTracker: VelocityTracker? = null

    /**
     * Finger X and progress value captured at the moment seek mode is entered.
     * Used to compute a relative seek delta so the progress bar never jumps
     * to an absolute finger position.
     *
     * @author Hamza417
     */
    private var seekStartX = 0f
    private var seekStartProgress = 0f

    /**
     * Handler that auto-releases the ripple a short time after it is armed on
     * [MotionEvent.ACTION_DOWN].  This makes the ripple a "fire-and-forget"
     * cherry-on-top visual that plays out its own expand+fade animation without
     * being canceled mid-way by any subsequent gesture state.
     *
     * @author Hamza417
     */
    private val rippleAutoReleaseHandler = Handler(Looper.getMainLooper())
    private val rippleAutoReleaseRunnable = Runnable { releaseAllRipples() }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && !isBeingDragged && !isInSeekMode) {
            if (isInPlayPauseZone(event.x)) {
                performPlayPauseClick()
                return true
            }
        }

        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scrollEngine.cancelAnimation()
                lastMotionX = event.x
                dragStartScrollPx = scrollEngine.scrollPx
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
                parent?.requestDisallowInterceptTouchEvent(true)

                // Fire the ripple as a one-shot touch indicator.  The auto-release
                // schedules the expand+fade immediately so no other gesture path can
                // cancel or fight it — the ripple simply plays out on its own.
                fullRipple.setHotspot(event.x, event.y)
                fullRipple.setState(intArrayOf(android.R.attr.state_pressed))
                rippleAutoReleaseHandler.removeCallbacks(rippleAutoReleaseRunnable)
                rippleAutoReleaseHandler.postDelayed(rippleAutoReleaseRunnable, RIPPLE_AUTO_RELEASE_MS)
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.x - lastMotionX
                if (isInSeekMode) {
                    // Seek mode: compute a relative delta from the anchor position captured
                    // when seek mode started.  This avoids the abrupt jump to absolute
                    // finger position that absolute mapping produces.
                    val seekAreaWidth = (width.toFloat() - artSize).coerceAtLeast(1f)
                    val seekDelta = (event.x - seekStartX) / seekAreaWidth
                    val seekFraction = (seekStartProgress + seekDelta).coerceIn(0f, 1f)
                    progress = seekFraction
                    invalidate()
                    seekListener?.invoke(seekFraction)
                } else {
                    if (!isBeingDragged && abs(dx) > touchSlop * 0.6f) {
                        isBeingDragged = true
                        scrollEngine.notifyScrollState(SCROLL_STATE_DRAGGING)
                        parent?.requestDisallowInterceptTouchEvent(true)
                        animateEdgeFade(show = true)
                        animatePlayPauseSlide(slideOut = true)
                        // Drag canceled the tap — fade progress bar out
                        animateProgressAlpha(0f)
                    }
                    if (isBeingDragged) {
                        scrollEngine.applyDragDelta(dx)
                    }
                }
                lastMotionX = event.x
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val vx = velocityTracker?.xVelocity ?: 0f
                if (isInSeekMode) {
                    // Seek mode ends — restore progress bar, do NOT trigger a page change
                    isInSeekMode = false
                    animateProgressAlpha(1f)
                    animateSeekThumb(show = false)
                    animateSeekFlat(flat = false)
                } else if (isBeingDragged) {
                    scrollEngine.finishDrag(vx, dragStartScrollPx)
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    performClick()
                }
                velocityTracker?.recycle()
                velocityTracker = null
                isBeingDragged = false
                animateEdgeFade(show = false)
                animatePlayPauseSlide(slideOut = false)
                // Safety catch: cancel any pending auto-release and release immediately
                // in case the finger lifts before the 80 ms timer fires.
                rippleAutoReleaseHandler.removeCallbacks(rippleAutoReleaseRunnable)
                releaseAllRipples()
            }
        }
        return true
    }

    override fun onDown(e: MotionEvent): Boolean = true
    override fun onShowPress(e: MotionEvent) = Unit
    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean = false

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        if (isInPlayPauseZone(e.x)) {
            performPlayPauseClick()
        } else {
            val w = width.takeIf { it > 0 } ?: return true
            if (e.x < w - btnZoneWidth) {
                // If the scroll is at an intermediate position from a cancelled drag
                // (e.g., the user swiped halfway then tapped quickly to open the player),
                // snap to the nearest page so the miniplayer is not left visually stuck
                // between two pages when the user returns from the player screen.
                val vw = scrollEngine.viewWidth.takeIf { it > 0 }
                if (vw != null) {
                    val nearestPage = scrollEngine.scrollPageIndex()
                    if (abs(scrollEngine.scrollPx - nearestPage * vw.toFloat()) > 0.5f) {
                        scrollEngine.jumpToPage(nearestPage)
                    }
                }
                callbacks?.onItemClick(scrollEngine.currentPage.coerceAtLeast(0))
            }
        }
        return true
    }

    override fun onLongPress(e: MotionEvent) {
        if (!isInPlayPauseZone(e.x)) {
            if (!isBeingDragged) {
                // Enter seek mode: cancel any page animation, give haptic feedback once,
                // then subsequent drags will seek the track rather than change pages.
                // Record the anchor so that seek movement is offset-based, not absolute.
                isInSeekMode = true
                isBeingDragged = false
                seekStartX = e.x
                seekStartProgress = progress
                scrollEngine.cancelAnimation()
                animateEdgeFade(show = false)
                animateSeekThumb(show = true)
                animateSeekFlat(flat = true)
                context.vibrateEffect(VibrationEffect.EFFECT_CLICK, TAG)
            } else {
                callbacks?.onItemLongClick(scrollEngine.currentPage.coerceAtLeast(0))
            }
        }
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        if (scrollEngine.scrollState == SCROLL_STATE_DRAGGING) return false
        scrollEngine.finishDrag(velocityX, dragStartScrollPx)
        return true
    }

    private fun performPlayPauseClick() {
        togglePlayPause()
        callbacks?.onPlayPauseClick()
    }

    private fun isInPlayPauseZone(x: Float): Boolean = x >= width - btnZoneWidth

    // -------------------------------------------------------------------------
    // Geometry — recomputed in applyConfig() on every size change
    // -------------------------------------------------------------------------

    private var btnZoneWidth = 0f
    private var artSize = 0f
    private var textLeft = 0f
    private var textRight = 0f
    private var cornerRadiusPx = 0f

    /**
     * The corner radius sourced directly from theme preferences, before any flat-mode
     * override is applied. Updated every time [applyConfig] runs so theme changes are
     * reflected even while the view is in flat mode.
     *
     * @author Hamza417
     */
    private var baseCornerRadiusPx = 0f

    /**
     * Whether the miniplayer is currently in flat (marginless) mode.
     * Toggled by [UserInterfacePreferences.MARGIN_AROUND_MINIPLAYER] and drives both
     * the margin reset and the corner-radius change in [applyMarginMode].
     *
     * @author Hamza417
     */
    private var isFlatMode = false

    private val cardRect = RectF()
    private val strokeRect = RectF()
    private val artDstRect = RectF()
    private val artSrcRect = Rect()
    private val cardClipPath = Path()

    // -------------------------------------------------------------------------
    // Touch-feedback ripple — single full-card drawable
    // -------------------------------------------------------------------------

    /**
     * One ripple that spans the entire card surface so taps anywhere produce
     * consistent, correctly-bounded feedback.  The drawable is drawn on top of
     * every other layer but is suppressed while a swipe or seek is in progress
     * so it never visually fights the scrolling or progress content.
     *
     * @author Hamza417
     */
    private val fullRipple = FelicityRippleDrawable(
            ThemeManager.theme.iconTheme.regularIconColor).apply {
        setCornerRadius(AppearancePreferences.getCornerRadius())
        setStartColor(ThemeManager.theme.viewGroupTheme.highlightColor)
        callback = object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) = invalidate()
            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = Unit
            override fun unscheduleDrawable(who: Drawable, what: Runnable) = Unit
        }
    }

    /** Sync ripple accent color, highlight color, and corner radius from the current theme. */
    private fun refreshRippleTheme() {
        val accent = ThemeManager.accent.primaryAccentColor
        val highlight = ThemeManager.theme.viewGroupTheme.highlightColor
        val radius = if (isFlatMode) 0f else AppearancePreferences.getCornerRadius()
        fullRipple.setRippleColor(accent)
        fullRipple.setStartColor(highlight)
        fullRipple.setCornerRadius(radius)
    }

    /** Size the ripple to fill the whole card. Called from [applyConfig]. */
    private fun updateRippleBounds(w: Int, h: Int) {
        fullRipple.bounds = Rect(0, 0, w, h)
    }

    /**
     * Cancel the ripple press state so the animation plays out or stops immediately.
     * Called on drag-start, long-press enter, and finger-up in all gesture paths.
     */
    private fun releaseAllRipples() {
        fullRipple.setState(intArrayOf())
    }

    /**
     * Rebuilds [cardClipPath] using the current [cardRect] and [cornerRadiusPx].
     * Must be called after either value changes so that clipping and drawing stay in sync.
     *
     * @author Hamza417
     */
    private fun rebuildCardClipPath() {
        cardClipPath.rewind()
        cardClipPath.addRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
    }

    /**
     * Reads [UserInterfacePreferences.isMarginAroundMiniplayer] and instantly switches the
     * view between its normal (margined, rounded) and flat (marginless, sharp-cornered) layouts.
     *
     * All four margins are applied at once with no animation so the change is imperceptible
     * in the Preferences screen where the miniplayer is not visible.  Both the absolute
     * left/right margins and the RTL-aware [ViewGroup.MarginLayoutParams.marginStart] /
     * [ViewGroup.MarginLayoutParams.marginEnd] fields are updated so the correct value is
     * used regardless of layout direction.
     *
     * The translationY value is re-anchored after the new layout pass so the view stays
     * in its current show/hide state even though the margins (and therefore
     * [hideDistance]) have changed.
     *
     * @author Hamza417
     */
    private fun applyMarginMode() {
        isFlatMode = !UserInterfacePreferences.isMarginAroundMiniplayer()
        val targetSideMargin = if (isFlatMode) 0 else baseSideMarginPx
        val targetBottomMargin = if (isFlatMode) 0 else baseSideMarginPx + navBarInsetPx
        val targetRadius = if (isFlatMode) 0f else baseCornerRadiusPx

        val lp = layoutParams as? ViewGroup.MarginLayoutParams ?: run {
            cornerRadiusPx = targetRadius
            if (width > 0 && height > 0) {
                rebuildCardClipPath()
                invalidateOutline()
                invalidate()
            }
            return
        }

        // Capture fraction BEFORE altering margins so the re-anchor below is correct.
        val priorHide = hideDistance
        val savedFraction = if (priorHide > 0f) (translationY / priorHide).coerceIn(0f, 1f) else 0f

        lp.setMargins(targetSideMargin, targetSideMargin, targetSideMargin, targetBottomMargin)
        // Also update start/end so RTL-priority fields never override the zeroed left/right.
        lp.marginStart = targetSideMargin
        lp.marginEnd = targetSideMargin
        layoutParams = lp

        cornerRadiusPx = targetRadius
        rebuildCardClipPath()
        invalidateOutline()
        refreshRippleTheme()
        updateFooterDecorations()
        invalidate()

        // After the layout system has recalculated the view's position with the new margins,
        // restore the translationY to the equivalent show/hide fraction.
        post {
            val newHide = hideDistance
            if (newHide > 0f) {
                translationY = when {
                    savedFraction >= 0.95f -> newHide
                    savedFraction <= 0.05f -> 0f
                    else -> (savedFraction * newHide).coerceIn(0f, newHide)
                }
            }
        }
    }

    /**
     * Returns the total bottom-spacing height that [RecyclerView] items need to avoid
     * being obscured by the floating miniplayer.
     *
     * The value equals the view's measured height plus its current bottom margin so that
     * the last list item is always fully visible above the player's bottom edge.
     *
     * @return spacing in pixels, or 0 when the view has not been laid out yet
     * @author Hamza417
     */
    private fun getRequiredFooterSpacing(): Int {
        if (height <= 0) return 0
        val bm = (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
        return height + bm
    }

    /**
     * Pushes the current [getRequiredFooterSpacing] value to every [FooterSpacingItemDecoration]
     * that was registered when a [RecyclerView] was attached via [attachToRecyclerView].
     *
     * Safe to call at any time; no-ops when [footerDecorations] is empty.
     *
     * @author Hamza417
     */
    private fun updateFooterDecorations() {
        if (footerDecorations.isEmpty()) return
        val spacing = getRequiredFooterSpacing()
        footerDecorations.values.forEach { it.updateFooterHeight(spacing) }
    }

    // -------------------------------------------------------------------------
    // Playback progress bar
    // -------------------------------------------------------------------------

    /**
     * Current playback progress fraction in [0, 1].
     * Drawn per-page inside [drawPageProgress] so it scrolls with the album art and text.
     */
    private var progress: Float = 0f

    /**
     * Visibility alpha of the progress bar in [0, 1].
     * Animated to 1 on the first [setProgress] call and to 0 / back during page swipes.
     */
    private var progressBarAlpha: Float = 0f

    /**
     * RGB of the accent color; alpha is computed dynamically each frame so that
     * [progressBarAlpha] changes are reflected without re-calling [refreshProgressColors].
     */
    private var progressAccentColor: Int = Color.GRAY

    private val progressTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val progressFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var progressBarAlphaAnimator: ValueAnimator? = null

    /**
     * Current alpha of the seek thumb line indicator, in [0, 1].
     * Animated to 1 when seek mode is entered and back to 0 when it is exited.
     */
    private var seekThumbAlpha: Float = 0f

    /** Animator driving [seekThumbAlpha] for the fade-in and fade-out transitions. */
    private var seekThumbAlphaAnimator: ValueAnimator? = null

    /**
     * Morph fraction between the trailing-edge gradient look and a fully flat solid fill.
     * 0 = gradient (normal playback appearance), 1 = flat solid (seek mode appearance).
     * Animated via [animateSeekFlat] so the transition is smooth rather than an abrupt switch.
     */
    private var seekFlatFraction: Float = 0f

    /** Animator driving [seekFlatFraction] during seek mode entry and exit. */
    private var seekFlatAnimator: ValueAnimator? = null

    /**
     * Thin solid line drawn at the current progress position during seek mode.
     * [Paint.Cap.BUTT] ensures no rounded terminations on either end.
     * A [Paint.setShadowLayer] is applied per-frame to produce the bleeding glow.
     * Requires `LAYER_TYPE_SOFTWARE` (active when [usePaintShadow] is `true`) or
     * API 28+ hardware acceleration for the shadow to render on non-text draws.
     */
    private val seekThumbLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    /**
     * Animator used to tween [progressAccentColor] smoothly when the accent changes
     * via [onAccentChanged]. Using [ValueAnimator.ofArgb] keeps all accent-tinted
     * drawing (progress bar, ripple) in lock-step during the color transition.
     */
    private var accentColorAnimator: ValueAnimator? = null

    /**
     * Animator used to tween [progress] for large jumps (first value, seek scrub, song change).
     * Canceled immediately for tiny real-time increments to avoid visible lag.
     */
    private var progressValueAnimator: ValueAnimator? = null

    /**
     * Whether the player is currently in seek mode (triggered by long-press).
     * In this mode horizontal drags seek the track instead of changing pages.
     * Exposed so callers can skip pushing seek-position updates while the user
     * is actively scrubbing — avoids the external value fighting the touch position.
     *
     * @author Hamza417
     */
    var isSeeking: Boolean
        get() = isInSeekMode
        private set(value) {
            isInSeekMode = value
        }

    private var isInSeekMode = false

    /**
     * Set the current playback progress.
     *
     * When [animate] is `true`, large deltas are smoothly tweened (covers first-load
     * and state-restore scenarios). When [animate] is `false` the value is applied
     * immediately without any animation — use this for real-time playback ticks to
     * keep the bar perfectly in sync with the audio engine.
     *
     * Calls during an active seek drag ([isSeeking] = true) are silently ignored;
     * the touch handler owns progress while the user is scrubbing.
     *
     * @param fraction value in [0, 1] where 0 = track start and 1 = track end
     * @param animate  `true` to tween large jumps, `false` to snap immediately
     */
    fun setProgress(fraction: Float, animate: Boolean = false) {
        // External callers must not fight the seek-drag touch handler
        if (isInSeekMode) return

        val target = fraction.coerceIn(0f, 1f)

        // Fade the bar in the first time it receives a real value
        if (progressBarAlpha < 0.5f && !isBeingDragged) {
            animateProgressAlpha(1f)
        }

        if (!animate) {
            progressValueAnimator?.cancel()
            progress = target
            invalidate()
            return
        }

        val delta = abs(target - progress)
        when {
            delta < 0.005f -> {
                // Sub-half-percent increment: stay in sync without an animator
                progressValueAnimator?.cancel()
                progress = target
                invalidate()
            }
            else -> {
                // Large jump (first value, state restore): smooth tween
                progressValueAnimator?.cancel()
                val from = progress
                progressValueAnimator = ValueAnimator.ofFloat(from, target).apply {
                    duration = (delta * 1800L).toLong().coerceIn(120L, 350L)
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        progress = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            }
        }
    }

    /**
     * Callback invoked while the user is seeking via long-press drag.
     * Receives a fraction in [0, 1] representing the desired playback position.
     */
    var seekListener: ((fraction: Float) -> Unit)? = null

    /**
     * Animate the visibility of the progress bar to [target].
     *
     * @param target 0 = hidden, 1 = fully visible
     */
    private fun animateProgressAlpha(target: Float) {
        val clamped = target.coerceIn(0f, 1f)
        if (progressBarAlpha == clamped && progressBarAlphaAnimator?.isRunning != true) return
        progressBarAlphaAnimator?.cancel()
        progressBarAlphaAnimator = ValueAnimator.ofFloat(progressBarAlpha, clamped).apply {
            duration = if (clamped > 0f) 280L else 180L
            interpolator = if (clamped > 0f) DecelerateInterpolator() else AccelerateInterpolator()
            addUpdateListener {
                progressBarAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * Caches the current accent RGB so that [drawPageProgress] can compose the correct
     * ARGB colors per-frame without allocating new color objects.
     */
    private fun refreshProgressColors() {
        progressAccentColor = ThemeManager.accent.primaryAccentColor
        invalidate()
    }

    /**
     * Draws the progress track and fill for a single page.
     *
     * Because this is called inside [drawPages] with the page translation already applied,
     * the progress bar scrolls naturally with the album art and text during a swipe.
     * Only the currently settled page renders the real [progress] value; neighboring
     * pages render an empty track so they slide in cleanly without a stale fill.
     *
     * When the player is **not** in seek mode the fill is rendered with a three-stop
     * [LinearGradient]: the bulk of the bar is a solid flat fill and, as the current
     * progress position is approached, the color fades to fully transparent. This gives
     * the trailing edge a soft, blended look against whatever is behind it rather than
     * terminating with a hard clip. The fade zone spans [h] pixels (the view height),
     * clamped so it never exceeds the actual filled width.
     *
     * During an active seek drag the gradient is replaced with a plain solid fill so
     * the scrubber thumb position remains crisp and unambiguous.
     *
     * @param canvas the canvas, already clipped to this page's horizontal rect
     * @param tx     left edge of this page in canvas coordinates (negative when sliding in from right)
     * @param pageW  page width in pixels (equals view width)
     * @param h      view height in pixels
     * @param fill   fill fraction in [0, 1] to render for this page
     */
    private fun drawPageProgress(canvas: Canvas, tx: Float, pageW: Float, h: Float, fill: Float) {
        if (progressBarAlpha <= 0f) return
        val r = Color.red(progressAccentColor)
        val g = Color.green(progressAccentColor)
        val b = Color.blue(progressAccentColor)
        val trackAlpha = (35 * progressBarAlpha).toInt().coerceIn(0, 255)
        val fillAlpha = (80 * progressBarAlpha).toInt().coerceIn(0, 255)

        val progressLeft = tx + artSize
        val progressRight = tx + pageW

        // Track (unfilled background)
        progressTrackPaint.color = Color.argb(trackAlpha, r, g, b)
        canvas.drawRect(progressLeft, 0f, progressRight, h, progressTrackPaint)

        // Filled portion
        if (fill > 0f && fillAlpha > 0) {
            val fillRight = progressLeft + (progressRight - progressLeft) * fill
            val fillColor = Color.argb(fillAlpha, r, g, b)

            // Compute the natural gradient zone: the fade-to-transparent tail is [h] pixels
            // wide, clamped so it never exceeds the actual filled width.
            val fillWidth = fillRight - progressLeft
            val gradientWidth = minOf(h, fillWidth)
            val blendStart = fillRight - gradientWidth
            val naturalSolidFraction = if (fillWidth > 0f) {
                ((blendStart - progressLeft) / fillWidth).coerceIn(0f, 1f)
            } else 0f

            // Interpolate toward a flat solid fill as seekFlatFraction approaches 1.
            // solidFraction reaches 1 (no gradient tail) and endAlpha reaches fillAlpha
            // (opaque end stop) so the LinearGradient collapses to a uniform solid color.
            val effectiveSolidFraction = naturalSolidFraction + (1f - naturalSolidFraction) * seekFlatFraction
            val effectiveEndAlpha = (fillAlpha * seekFlatFraction).toInt().coerceIn(0, 255)

            progressFillPaint.shader = LinearGradient(
                    progressLeft, 0f, fillRight, 0f,
                    intArrayOf(fillColor, fillColor, Color.argb(effectiveEndAlpha, r, g, b)),
                    floatArrayOf(0f, effectiveSolidFraction, 1f),
                    Shader.TileMode.CLAMP
            )

            canvas.drawRect(progressLeft, 0f, fillRight, h, progressFillPaint)
        } else {
            // No fill visible — release any shader so the paint is not left in a dirty state.
            progressFillPaint.shader = null
        }
    }

    /**
     * Smoothly fades the seek thumb indicator in or out.
     *
     * @param show `true` to fade in (seek mode entered), `false` to fade out (seek mode exited)
     */
    private fun animateSeekThumb(show: Boolean) {
        val target = if (show) 1f else 0f
        if (seekThumbAlpha == target && seekThumbAlphaAnimator?.isRunning != true) return
        seekThumbAlphaAnimator?.cancel()
        seekThumbAlphaAnimator = ValueAnimator.ofFloat(seekThumbAlpha, target).apply {
            duration = if (show) 180L else 350L
            interpolator = if (show) DecelerateInterpolator() else AccelerateInterpolator()
            addUpdateListener {
                seekThumbAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * Animates [seekFlatFraction] between the gradient look (0) and the flat solid fill (1).
     *
     * When [flat] is `true` the trailing-edge gradient collapses into a solid fill so
     * the exact scrubber position reads clearly. When [flat] is `false` it expands back
     * into the soft blended gradient used during normal playback.
     *
     * @param flat `true` to animate toward solid fill, `false` to animate back to gradient
     */
    private fun animateSeekFlat(flat: Boolean) {
        val target = if (flat) 1f else 0f
        if (seekFlatFraction == target && seekFlatAnimator?.isRunning != true) return
        seekFlatAnimator?.cancel()
        seekFlatAnimator = ValueAnimator.ofFloat(seekFlatFraction, target).apply {
            duration = if (flat) 220L else 380L
            interpolator = if (flat) AccelerateDecelerateInterpolator() else DecelerateInterpolator()
            addUpdateListener {
                seekFlatFraction = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * Draws the vertical seek thumb indicator at the current [progress] position.
     *
     * A single [Paint.setShadowLayer] call on [seekThumbLinePaint] produces the
     * bleeding glow effect — no separate glow paint is needed.  The shadow radius
     * is intentionally large relative to the stroke width so the halo spreads
     * visibly around the thin line.
     *
     * [Paint.Cap.BUTT] is already set on [seekThumbLinePaint] so both ends of the
     * line are sharp with no rounded caps.  All alpha values are multiplied by
     * [seekThumbAlpha] so the whole indicator fades uniformly on entry and exit.
     *
     * @param canvas the canvas to draw onto (already clipped to the card shape)
     */
    private fun drawSeekThumb(canvas: Canvas) {
        if (seekThumbAlpha <= 0f) return
        val h = height.toFloat()
        val thumbX = artSize + (width.toFloat() - artSize) * progress

        val r = Color.red(progressAccentColor)
        val g = Color.green(progressAccentColor)
        val b = Color.blue(progressAccentColor)

        val lineAlpha = (220 * seekThumbAlpha).toInt().coerceIn(0, 255)
        val shadowAlpha = (200 * seekThumbAlpha).toInt().coerceIn(0, 255)

        // setShadowLayer provides the bleeding glow — works for drawLine on API 28+ hardware
        // canvas and on any API when the view uses LAYER_TYPE_SOFTWARE (i.e. usePaintShadow mode).
        seekThumbLinePaint.setShadowLayer(dp(10f), 0f, 0f, Color.argb(shadowAlpha, r, g, b))
        seekThumbLinePaint.color = Color.argb(lineAlpha, r, g, b)
        canvas.drawLine(thumbX, 0f, thumbX, h, seekThumbLinePaint)
    }

    // -------------------------------------------------------------------------

    /** Whether the edge-fade effect is enabled. Toggle with [setEdgeFadeEnabled]. */
    private var edgeFadeEnabled = false

    /** Current opacity of the edge fades; 0 = hidden, 1 = fully visible. */
    private var edgeFadeAlpha = 0f
    private var edgeFadeAnimator: ValueAnimator? = null
    private var edgeFadeWidth = 0f

    private val edgeFadeLeftRect = RectF()
    private val edgeFadeRightRect = RectF()

    /** DST_OUT paint erases content under the gradient, creating a soft scroll-hint fade. */
    private val edgeFadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    /**
     * Enable or disable the edge-fade gradients that appear during a swipe.
     * When disabled, any in-progress fade animation is canceled immediately.
     */
    fun setEdgeFadeEnabled(enabled: Boolean) {
        edgeFadeEnabled = enabled
        if (!enabled) {
            edgeFadeAnimator?.cancel()
            edgeFadeAlpha = 0f
            invalidate()
        }
    }

    private fun animateEdgeFade(show: Boolean) {
        if (!edgeFadeEnabled) return
        val target = if (show) 1f else 0f
        if (edgeFadeAlpha == target && edgeFadeAnimator == null) return
        edgeFadeAnimator?.cancel()
        edgeFadeAnimator = ValueAnimator.ofFloat(edgeFadeAlpha, target).apply {
            duration = if (show) EDGE_FADE_IN_MS else EDGE_FADE_OUT_MS
            interpolator = if (show) AccelerateInterpolator() else DecelerateInterpolator()
            addUpdateListener {
                edgeFadeAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun rebuildEdgeFadeRects(w: Float, h: Float) {
        edgeFadeLeftRect.set(0f, 0f, edgeFadeWidth, h)
        edgeFadeRightRect.set(w - edgeFadeWidth, 0f, w, h)
    }

    // -------------------------------------------------------------------------
    // Play/pause button slide animation
    // -------------------------------------------------------------------------

    private var ppSlideOut = 0f
    private var ppSlideAnimator: ValueAnimator? = null

    private fun animatePlayPauseSlide(slideOut: Boolean) {
        val target = if (slideOut) 1f else 0f
        if (ppSlideOut == target && ppSlideAnimator == null) return
        ppSlideAnimator?.cancel()
        ppSlideAnimator = ValueAnimator.ofFloat(ppSlideOut, target).apply {
            duration = if (slideOut) PP_SLIDE_OUT_MS else PP_SLIDE_IN_MS
            interpolator = if (slideOut) AccelerateInterpolator() else DecelerateInterpolator()
            addUpdateListener {
                ppSlideOut = it.animatedValue as Float
                playPauseDrawer.slideOut = ppSlideOut
                invalidate()
            }
            start()
        }
    }

    // -------------------------------------------------------------------------
    // Elevation
    // -------------------------------------------------------------------------

    private var elevationAnimator: ValueAnimator? = null

    /**
     * Smoothly animate the card's drop-shadow elevation to [targetDp] dp.
     * Pass `animated = false` for an instant change.
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

    /** Tint both the ambient and spot drop shadows with [color]. */
    fun setElevationColor(@ColorInt color: Int) {
        outlineAmbientShadowColor = color
        outlineSpotShadowColor = color
    }

    // -------------------------------------------------------------------------
    // Paint-shadow mode — alternative to system elevation
    // -------------------------------------------------------------------------

    /**
     * When `true` the card background is shadowed via [Paint.setShadowLayer] on [bgPaint]
     * instead of Android's system elevation.  The view's layer type is switched to
     * `LAYER_TYPE_SOFTWARE` so that [Paint.setShadowLayer] works for all drawing operations
     * (including `Canvas.drawRoundRect` and `Canvas.drawLine`).
     *
     * Driven by [AccessibilityPreferences.isDarkerMiniplayerShadow]; toggle with [setUsePaintShadow].
     *
     * @author Hamza417
     */
    private var usePaintShadow: Boolean = false

    /** Shadow blur radius in pixels — computed from [PAINT_SHADOW_RADIUS_DP] in [applyConfig]. */
    private var paintShadowRadiusPx: Float = 0f

    /** Downward Y offset of the card paint shadow in pixels — from [PAINT_SHADOW_DY_DP]. */
    private var paintShadowDyPx: Float = 0f

    /**
     * Current shadow color composed as ~40% alpha of the primary accent color.
     * Animated in lockstep with [progressAccentColor] via [refreshPaintShadowColor]
     * so album-art-driven accent changes transition smoothly.
     */
    private var paintShadowColor: Int = Color.TRANSPARENT

    /**
     * Recomputes [paintShadowColor] from the current [progressAccentColor] at ~40% alpha
     * and re-applies it to [bgPaint]'s shadow layer when paint-shadow mode is active.
     *
     * Called from [refreshProgressColors] (initial setup and theme change) and from the
     * [accentColorAnimator] update listener so the shadow color animates frame-by-frame
     * in lockstep with the progress bar color during album-art accent transitions.
     *
     * @author Hamza417
     */
    private fun refreshPaintShadowColor() {
        val r = Color.red(progressAccentColor)
        val g = Color.green(progressAccentColor)
        val b = Color.blue(progressAccentColor)
        // ~40% opacity of accent — visible but not overwhelming
        paintShadowColor = Color.argb(102, r, g, b)
        if (usePaintShadow && !isTransparent) {
            bgPaint.setShadowLayer(paintShadowRadiusPx, 0f, paintShadowDyPx, paintShadowColor)
        }
    }

    /**
     * Switches between Android system elevation shadows and a [Paint.setShadowLayer]
     * shadow drawn directly onto the canvas.
     *
     * When [enabled] is `true`:
     *  - View elevation is zeroed out so the OS ambient/spot shadow disappears.
     *  - The layer type is switched to `LAYER_TYPE_SOFTWARE` so that
     *    [Paint.setShadowLayer] works for all canvas drawing, including
     *    `Canvas.drawRoundRect` and `Canvas.drawLine` (the seek thumb glow).
     *  - `bgPaint.setShadowLayer` is configured with [paintShadowRadiusPx] and
     *    [paintShadowDyPx] so the card background draws its own controlled shadow.
     *
     * When [enabled] is `false`:
     *  - `bgPaint.clearShadowLayer` removes the paint shadow.
     *  - The layer type is restored to `LAYER_TYPE_HARDWARE` for GPU-accelerated drawing.
     *  - Elevation is restored to [DEFAULT_ELEVATION_DP] (unless transparent mode is on).
     *
     * @param enabled `true` to use paint shadow, `false` to use system elevation
     * @author Hamza417
     */
    fun setUsePaintShadow(enabled: Boolean) {
        if (usePaintShadow == enabled) return
        usePaintShadow = enabled
        if (enabled) {
            elevation = 0f
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            if (!isTransparent) {
                bgPaint.setShadowLayer(paintShadowRadiusPx, 0f, paintShadowDyPx, paintShadowColor)
            }
        } else {
            bgPaint.clearShadowLayer()
            setLayerType(LAYER_TYPE_HARDWARE, null)
            if (!isTransparent) elevation = dp(DEFAULT_ELEVATION_DP)
        }
        invalidate()
    }

    // -------------------------------------------------------------------------
    // applyConfig — single source of truth for all layout and paint state
    // -------------------------------------------------------------------------

    /**
     * Recomputes all paint colors, typefaces, text sizes, corner radius, and
     * (when a real size is available) all geometry in one place.
     *
     * Called from the `init` block (w/h = 0, geometry skipped), [onSizeChanged],
     * and [onThemeChanged]. Safe to call multiple times — geometry is only
     * recomputed when `w > 0 && h > 0`.
     */
    private fun applyConfig(w: Float = width.toFloat(), h: Float = height.toFloat()) {
        if (!isTransparent) {
            cardColor = ThemeManager.theme.viewGroupTheme.backgroundColor
            titleColor = ThemeManager.theme.textViewTheme.primaryTextColor
            artistColor = ThemeManager.theme.textViewTheme.secondaryTextColor
            iconColor = ThemeManager.theme.iconTheme.regularIconColor
        }

        bgPaint.color = cardColor
        titlePaint.color = titleColor
        artistPaint.color = artistColor
        playPauseDrawer.color = iconColor
        refreshRippleTheme()
        refreshProgressColors()
        seekThumbLinePaint.strokeWidth = dp(2f)
        paintShadowRadiusPx = dp(PAINT_SHADOW_RADIUS_DP)
        paintShadowDyPx = dp(PAINT_SHADOW_DY_DP)
        // Recompute accent-based shadow color and re-apply to bgPaint when active.
        refreshPaintShadowColor()

        titlePaint.textSize = sp(titleTextSizeSp)
        artistPaint.textSize = sp(artistTextSizeSp)

        val font = AppearancePreferences.getAppFont()
        titlePaint.typeface = TypeFace.getTypeFace(font, 3 /* BOLD */, context)
        artistPaint.typeface = TypeFace.getTypeFace(font, 1 /* REGULAR */, context)

        val newRadius = AppearancePreferences.getCornerRadius()
        baseCornerRadiusPx = newRadius
        // In flat mode the corner radius is driven by the applyMarginMode animator;
        // overwriting it here would fight the animation and cause visual glitches.
        // In normal mode snap to the preference value and rebuild the outline if it changed.
        if (!isFlatMode && newRadius != cornerRadiusPx) {
            cornerRadiusPx = newRadius
            invalidateOutline()
        }

        if (w <= 0f || h <= 0f) return

        val btnSz = h * BTN_HEIGHT_FACTOR
        val btnHorizPad = dp(BTN_HORIZ_PADDING_DP)
        btnZoneWidth = btnSz + btnHorizPad * 2f

        val textPad = dp(TEXT_PADDING_DP)
        artSize = h
        textLeft = artSize + textPad
        textRight = w - btnZoneWidth - textPad

        cardRect.set(0f, 0f, w, h)
        rebuildCardClipPath()

        edgeFadeWidth = (w * EDGE_FADE_WIDTH_FRACTION).coerceAtLeast(dp(EDGE_FADE_MIN_WIDTH_DP))
        rebuildEdgeFadeRects(w, h)

        playPauseDrawer.btnZoneWidth = btnZoneWidth
        playPauseDrawer.centerX = w - btnZoneWidth / 2f
        playPauseDrawer.centerY = h / 2f
        playPauseDrawer.updateGeometry(btnSz)

        // Re-anchor the scroll position to the current page whenever the view width
        // changes (e.g., during the margin animation).  Without this, the absolute
        // scrollPx value—originally page * oldWidth—maps to a fractional page index
        // with the new width, causing the miniplayer to show the wrong song mid-animation.
        val newViewWidth = w.toInt()
        if (newViewWidth != scrollEngine.viewWidth && scrollEngine.viewWidth > 0) {
            val savedPage = scrollEngine.currentPage
            scrollEngine.viewWidth = newViewWidth
            scrollEngine.jumpToPage(savedPage)
        } else {
            scrollEngine.viewWidth = newViewWidth
        }

        // Keep ripple bounds in sync with the new geometry
        updateRippleBounds(w.toInt(), h.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyConfig(w.toFloat(), h.toFloat())
        updateFooterDecorations()
        updateScrollViewPaddings()

        // Re-anchor the vertical hide offset whenever the view geometry changes
        // (size change, margin change, or nav-bar inset update).
        // We intentionally use [prevHideDistance] — captured at the END of the previous
        // onSizeChanged — as the denominator so the fraction is computed against the actual
        // old hideDistance rather than the approximation "oldh + newBm" which gives a wrong
        // result when bottom margin changes between calls (e.g. nav-bar inset applied after
        // the first layout pass or flat-mode toggled).
        if (h > 0 && oldh > 0 && translationY > 0f && prevHideDistance > 0f) {
            val fraction = (translationY / prevHideDistance).coerceIn(0f, 1f)
            translationY = when {
                fraction >= 0.95f -> hideDistance          // was fully hidden → snap fully hidden
                fraction <= 0.05f -> 0f                   // was fully shown → snap fully shown
                else -> (fraction * hideDistance).coerceIn(0f, hideDistance)
            }
        }

        // Always keep prevHideDistance in sync BEFORE applyPendingTy so a subsequent
        // onSizeChanged from an insets change gets the correct denominator.
        prevHideDistance = hideDistance

        if (w > 0) ensurePageBitmaps()
        applyPendingTy()
    }

    // -------------------------------------------------------------------------
    // Paint objects
    // -------------------------------------------------------------------------

    private var cardColor: Int = ThemeManager.theme.viewGroupTheme.backgroundColor
    private var titleColor: Int = ThemeManager.theme.textViewTheme.primaryTextColor
    private var artistColor: Int = ThemeManager.theme.textViewTheme.secondaryTextColor
    private var iconColor: Int = ThemeManager.theme.iconTheme.regularIconColor

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = cardColor
    }

    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** Title text paint — text size and typeface are set by [applyConfig]. */
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = titleColor
    }

    /** Artist text paint — text size and typeface are set by [applyConfig]. */
    private val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = artistColor
    }

    /** Placeholder shown while album art is loading. */
    private val artPlaceholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, 128, 128, 128)
    }

    // -------------------------------------------------------------------------
    // Stroke border
    // -------------------------------------------------------------------------

    private var strokeEnabled = false
    private var strokeWidthPx = 0f

    @ColorInt
    private var strokeColor: Int = Color.argb(80, 128, 128, 128)

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = strokeColor
    }

    /** Toggle the stroke border drawn around the card edge. */
    fun setStrokeEnabled(enabled: Boolean) {
        strokeEnabled = enabled
        invalidate()
    }

    /** Set the stroke border width in dp. */
    fun setStrokeWidth(widthDp: Float) {
        strokeWidthPx = dp(widthDp)
        strokePaint.strokeWidth = strokeWidthPx
        invalidate()
    }

    /** Set the stroke border color. */
    fun setStrokeColor(@ColorInt color: Int) {
        strokeColor = color
        strokePaint.color = color
        invalidate()
    }

    /** One-shot convenience to configure the stroke in a single call. */
    fun setStroke(enabled: Boolean, @ColorInt color: Int, widthDp: Float = 1f) {
        strokeEnabled = enabled
        strokeColor = color
        strokeWidthPx = dp(widthDp)
        strokePaint.color = color
        strokePaint.strokeWidth = strokeWidthPx
        invalidate()
    }

    // -------------------------------------------------------------------------
    // Play/pause state
    // -------------------------------------------------------------------------

    private val playPauseDrawer = MiniPlayerPlayPauseDrawer()
    private var ppIsPlaying = false
    private var ppAnimator: ValueAnimator? = null

    /**
     * Reflect the current playback state in the icon.
     * Pass `animate = false` to jump immediately (e.g., on state restore).
     */
    fun setPlaying(playing: Boolean, animate: Boolean = true) {
        if (ppIsPlaying == playing) return
        ppIsPlaying = playing
        val target = if (playing) 0f else 1f
        ppAnimator?.cancel()
        if (animate) {
            ppAnimator = ValueAnimator.ofFloat(playPauseDrawer.progress, target).apply {
                duration = 300L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    playPauseDrawer.progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            playPauseDrawer.progress = target
            invalidate()
        }
    }

    private fun togglePlayPause() = setPlaying(!ppIsPlaying)

    // -------------------------------------------------------------------------
    // Text size overrides
    // -------------------------------------------------------------------------

    /** Current title text size in SP (default [TITLE_TEXT_SIZE_SP]). */
    private var titleTextSizeSp: Float = TITLE_TEXT_SIZE_SP

    /** Current artist text size in SP (default [ARTIST_TEXT_SIZE_SP]). */
    private var artistTextSizeSp: Float = ARTIST_TEXT_SIZE_SP

    /** Override the title text size. Value is in SP, e.g. `14f`. */
    fun setTitleTextSize(spValue: Float) {
        titleTextSizeSp = spValue
        titlePaint.textSize = sp(spValue)
        invalidate()
    }

    /** Override the artist text size. Value is in SP, e.g. `12f`. */
    fun setArtistTextSize(spValue: Float) {
        artistTextSizeSp = spValue
        artistPaint.textSize = sp(spValue)
        invalidate()
    }

    /** Set both text sizes in one call. Values are in SP. */
    fun setTextSizes(titleSp: Float, artistSp: Float) {
        titleTextSizeSp = titleSp
        artistTextSizeSp = artistSp
        titlePaint.textSize = sp(titleSp)
        artistPaint.textSize = sp(artistSp)
        invalidate()
    }

    // -------------------------------------------------------------------------
    // Transparency mode
    // -------------------------------------------------------------------------

    private var isTransparent = false
    private var opaqueCardColor: Int = cardColor
    private var bgColorAnimator: ValueAnimator? = null

    // -------------------------------------------------------------------------
    // init — runs after all property declarations above
    // -------------------------------------------------------------------------

    init {
        // Seed paint colors, typefaces, and text sizes. Geometry is deferred
        // to onSizeChanged because width/height are 0 at construction time.
        applyConfig()
    }

    /** Fade the card background to transparent and switch the icon/text to white. */
    fun makeTransparent(animated: Boolean = true) {
        if (isTransparent) return
        isTransparent = true
        opaqueCardColor = cardColor
        if (!usePaintShadow) {
            animateElevation(0f, ANIM_DURATION_MS, animated)
        } else {
            // In paint-shadow mode the shadow must be cleared while the card is transparent
            // so it does not bleed visibly around an invisible background.
            bgPaint.clearShadowLayer()
        }
        animateBgColor(cardColor, Color.TRANSPARENT, animated)
        animateTextIcon(Color.WHITE, Color.WHITE, Color.WHITE, animated)
    }

    /** Restore the card background and icon/text to their themed colors. */
    fun makeOpaque(animated: Boolean = true) {
        if (!isTransparent) return
        isTransparent = false
        val targetBg = if (opaqueCardColor != Color.TRANSPARENT) opaqueCardColor
        else ThemeManager.theme.viewGroupTheme.backgroundColor
        animateBgColor(Color.TRANSPARENT, targetBg, animated, onEnd = {
            if (!usePaintShadow) {
                animateElevation(DEFAULT_ELEVATION_DP, ELEV_ANIM_MS, animated)
            } else {
                // Restore the paint shadow now that the card is visible again.
                bgPaint.setShadowLayer(paintShadowRadiusPx, 0f, paintShadowDyPx, paintShadowColor)
                invalidate()
            }
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
            cardColor = to; bgPaint.color = to
            invalidate(); onEnd?.invoke()
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
                override fun onAnimationEnd(animation: Animator) = onEnd?.invoke() ?: Unit
            })
            start()
        }
    }

    private fun animateTextIcon(newTitle: Int, newArtist: Int, newIcon: Int, animated: Boolean) {
        if (!animated) {
            titleColor = newTitle; titlePaint.color = newTitle
            artistColor = newArtist; artistPaint.color = newArtist
            iconColor = newIcon; playPauseDrawer.color = newIcon
            invalidate(); return
        }
        val oldTitle = titleColor
        val oldArtist = artistColor
        val oldIcon = iconColor
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIM_DURATION_MS
            addUpdateListener { va ->
                val f = va.animatedFraction
                titleColor = blendColor(oldTitle, newTitle, f); titlePaint.color = titleColor
                artistColor = blendColor(oldArtist, newArtist, f); artistPaint.color = artistColor
                iconColor = blendColor(oldIcon, newIcon, f); playPauseDrawer.color = iconColor
                invalidate()
            }
            start()
        }
    }

    private fun blendColor(from: Int, to: Int, f: Float): Int {
        val t = f.coerceIn(0f, 1f)
        return Color.argb(
                (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t).toInt(),
                (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
                (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
                (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt()
        )
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 1. Card background
        bgPaint.color = cardColor
        canvas.drawRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, bgPaint)

        // 2. Optional stroke border
        if (strokeEnabled && strokeWidthPx > 0f) {
            val inset = strokeWidthPx / 2f
            strokeRect.set(
                    cardRect.left + inset, cardRect.top + inset,
                    cardRect.right - inset, cardRect.bottom - inset)
            canvas.drawRoundRect(strokeRect,
                                 (cornerRadiusPx - inset).coerceAtLeast(0f),
                                 (cornerRadiusPx - inset).coerceAtLeast(0f),
                                 strokePaint)
        }

        // 3. Pages (art + progress bar + text) with optional edge fades
        val pageW = w.toInt()
        if (pageW > 0 && items.isNotEmpty()) {
            if (edgeFadeEnabled && edgeFadeAlpha > 0f) {
                // saveLayer isolates DST_OUT so it only erases page content, not the card bg
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

        // 4. Seek thumb indicator — drawn above page content, clipped to the card shape
        if (seekThumbAlpha > 0f) {
            canvas.withClip(cardClipPath) {
                drawSeekThumb(this)
            }
        }

        // 5. Play/pause button (slides off during drag)
        playPauseDrawer.draw(canvas)

        // 6. Full-card ripple — always rendered; it auto-releases and fades on its own
        //    so it never visually fights scroll or seek content.
        canvas.withClip(cardClipPath) {
            fullRipple.draw(this)
        }
    }

    private fun drawPages(canvas: Canvas, pageW: Int, h: Float) {
        val count = items.size
        val scrollF = scrollEngine.scrollPx / pageW
        val centerPage = scrollF.toInt().coerceIn(0, count - 1)

        val lo = max(0, centerPage - 1)
        val hi = min(count - 1, centerPage + 1)

        for (i in lo..hi) {
            val tx = i * pageW.toFloat() - scrollEngine.scrollPx
            if (tx >= pageW || tx <= -pageW.toFloat()) continue

            canvas.save()
            canvas.clipRect(tx, 0f, tx + pageW, h)

            val item = items[i]
            val bmp = bitmapCache[i]

            // Progress bar — drawn first so it sits behind art and text.
            // Only the settled page shows the real progress; others show an empty track.
            val pageFill = if (i == scrollEngine.currentPage) progress else 0f
            drawPageProgress(canvas, tx, pageW.toFloat(), h, pageFill)

            // Art slot — square on the left edge
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

            // Text block
            val tLeft = tx + textLeft
            val tRight = tx + textRight
            if (tRight > tLeft) drawPageText(canvas, item.title, item.artist, tLeft, tRight, h)

            canvas.restore()
        }
    }

    private fun drawPageText(canvas: Canvas, title: String, artist: String,
                             left: Float, right: Float, h: Float) {
        val maxW = right - left
        val tm = titlePaint.fontMetrics
        val am = artistPaint.fontMetrics
        val titleLineH = tm.descent - tm.ascent
        val artistLineH = am.descent - am.ascent
        val gap = dp(TEXT_LINE_GAP_DP)
        val blockH = titleLineH + gap + artistLineH
        val blockTop = (h - blockH) / 2f
        val titleBaseline = blockTop + (-tm.ascent)
        val artistBaseline = blockTop + titleLineH + gap + (-am.ascent)
        canvas.drawText(ellipsize(title, titlePaint, maxW), left, titleBaseline, titlePaint)
        canvas.drawText(ellipsize(artist, artistPaint, maxW), left, artistBaseline, artistPaint)
    }

    /**
     * Draws left and/or right gradient masks using DST_OUT blending.
     * Must be called inside a [Canvas.saveLayer] block.
     */
    private fun drawEdgeFades(canvas: Canvas) {
        if (edgeFadeAlpha <= 0f) return
        val alpha = (edgeFadeAlpha * 255f).toInt().coerceIn(1, 255)
        val canScrollLeft = scrollEngine.scrollPx > 0.5f
        val maxScroll = maxLastPage() * width.toFloat()
        val canScrollRight = scrollEngine.scrollPx < maxScroll - 0.5f

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

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (text.isEmpty() || paint.measureText(text) <= maxWidth) return text
        val ellipsis = "\u2026"
        val ew = paint.measureText(ellipsis)
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + ew > maxWidth) end--
        return text.substring(0, end) + ellipsis
    }

    // -------------------------------------------------------------------------
    // Show / Hide / RecyclerView scroll-hide
    // -------------------------------------------------------------------------

    private val attached: MutableMap<RecyclerView, RecyclerView.OnScrollListener> = mutableMapOf()

    /**
     * Footer spacing decorations keyed by [RecyclerView].
     * Each decoration reserves space at the bottom of the list equal to the player's
     * visual footprint (height + bottom margin) so the last item is never obscured.
     *
     * @author Hamza417
     */
    private val footerDecorations: MutableMap<RecyclerView, FooterSpacingItemDecoration> = mutableMapOf()

    /**
     * Decorations that were released via [FooterSpacingItemDecoration.release] but not yet
     * physically removed from their [RecyclerView]. Tracked so that [attachToRecyclerView]
     * can remove the stale decoration before adding a fresh one, preventing doubled footer
     * spacing that would otherwise accumulate on each predictive-back cancel cycle.
     *
     * @author Hamza417
     */
    private val pendingReleaseDecorations: MutableMap<RecyclerView, FooterSpacingItemDecoration> = mutableMapOf()

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

    /**
     * Mirrors [BehaviourPreferences.isMiniplayerAlwaysVisible]. When `true` all
     * scroll-driven hide gestures are suppressed so the player remains pinned to its
     * shown position. Explicit [hide] calls from panels are unaffected.
     */
    private var isAlwaysVisible = BehaviourPreferences.isMiniplayerAlwaysVisible()
    private val resetManualHandler = Handler(Looper.getMainLooper())
    private val resetManualRunnable = Runnable { isManuallyControlled = false }

    /**
     * The single in-flight [ValueAnimator] that drives every [translationY] transition —
     * show, hide, snap, and all scroll-driven slides share this one instance so they
     * can never fight each other.
     *
     * Using [ValueAnimator] instead of [ViewPropertyAnimator] ensures that the start
     * value is captured **synchronously** at call time (via [slideTo]) rather than on
     * the next Choreographer frame, which eliminates the race where the framework's
     * internal shadow of [translationY] disagrees with the real value and causes the
     * animation to end instantly without any visible movement.
     *
     * @author Hamza417
     */
    private var tyAnimator: ValueAnimator? = null

    /**
     * The [hideDistance] value captured at the end of the most recent [onSizeChanged] call.
     * Used by the next [onSizeChanged] to compute the correct translationY fraction when the
     * view size or its margins change (e.g. on configuration change or nav-bar inset update).
     * Using the stale new margin as oldHide would produce a wrong fraction and leave a few
     * pixels of the player visible after rotation.
     *
     * @author Hamza417
     */
    private var prevHideDistance: Float = 0f

    private val hideDistance: Float
        get() {
            val bm = (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
            return height.toFloat() + bm
        }

    private fun isFullyShown() = translationY <= epsilon
    private fun isFullyHidden() = abs(translationY - hideDistance) <= epsilon

    /**
     * Cancels any in-flight [tyAnimator] and starts a new [ValueAnimator] that moves
     * [translationY] from its **current value at call time** to [target].
     *
     * Unlike [ViewPropertyAnimator], the start value is captured synchronously here so
     * there is no one-frame window in which external code can change [translationY] and
     * cause the framework to silently skip the animation.
     *
     * [onEnd] is invoked only on natural completion; it is suppressed when the animator
     * is canceled (e.g., by a subsequent call to [slideTo], [show], or [hide]).
     *
     * When [from] already equals [target] the animator is not started and [onEnd] is
     * called immediately so the caller's post-animation bookkeeping always runs.
     *
     * @param target       destination [translationY] in pixels
     * @param duration     animation length in milliseconds
     * @param interpolator easing curve to apply
     * @param onEnd        optional block called on natural completion
     * @author Hamza417
     */
    private fun slideTo(
            target: Float,
            duration: Long,
            interpolator: android.view.animation.Interpolator,
            onEnd: (() -> Unit)? = null) {
        tyAnimator?.cancel()
        val from = translationY
        if (from == target) {
            onEnd?.invoke()
            return
        }
        tyAnimator = ValueAnimator.ofFloat(from, target).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener { translationY = it.animatedValue as Float }
            if (onEnd != null) {
                var canceled = false
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        canceled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (!canceled) onEnd()
                    }
                })
            }
            start()
        }
    }

    /** Slide the player into view. Pass `animated = false` for an instant jump. */
    fun show(animated: Boolean = true) {
        tyAnimator?.cancel()
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
        Log.d(TAG, "Showing mini player (animated=$animated)")
        animateTy(0f, animated)
    }

    /** Slide the player out of view. Pass `animated = false` for an instant jump. */
    fun hide(animated: Boolean = true) {
        tyAnimator?.cancel()
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
            tyAnimator?.cancel()
            translationY = target
            suppressAutoFromRecyclerUntilIdle = false
            resetManualHandler.removeCallbacks(resetManualRunnable)
            resetManualHandler.postDelayed(resetManualRunnable, 500)
            return
        }

        slideTo(target, ANIM_DURATION_MS, slideInterpolator) {
            suppressAutoFromRecyclerUntilIdle = false
            resetManualHandler.removeCallbacks(resetManualRunnable)
            resetManualHandler.postDelayed(resetManualRunnable, 500)
        }
    }

    private fun updateForScrollDelta(dy: Int) {
        if (height == 0 || suppressAutoFromRecyclerUntilIdle || isManuallyControlled || isAlwaysVisible) return
        tyAnimator?.cancel()
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
            tyAnimator?.cancel()
            translationY = when {
                f >= 0.995f -> hideDistance
                f <= 0.005f -> 0f
                else -> (f * hideDistance).coerceIn(0f, hideDistance)
            }
            applied = true
        }
        if (!applied) pendingRestoreTranslationY?.let {
            tyAnimator?.cancel()
            translationY = it.coerceIn(0f, hideDistance)
        }
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

    /** Attach to a [RecyclerView] so the player auto-hides on scroll. */
    fun attachToRecyclerView(recyclerView: RecyclerView) {
        if (attached.containsKey(recyclerView)) return
        val listener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (!isRvScrollable(rv)) return
                // When the list has reached its bottom end, slide the miniplayer back into
                // view so it is always reachable without scrolling back up.
                if (!rv.canScrollVertically(1)
                        && !isManuallyControlled
                        && !suppressAutoFromRecyclerUntilIdle) {
                    if (!isFullyShown()) {
                        slideTo(0f, 250, showInterpolator)
                    }
                    return
                }
                updateForScrollDelta(dy)
            }

            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (isManuallyControlled) return
                val scrollable = isRvScrollable(rv)
                if (!scrollable) {
                    when (newState) {
                        RecyclerView.SCROLL_STATE_DRAGGING -> {
                            hadImmersiveDrag = true
                            if (!isFullyHidden()) slideTo(hideDistance, 250, hideInterpolator)
                        }
                        RecyclerView.SCROLL_STATE_SETTLING, RecyclerView.SCROLL_STATE_IDLE -> {
                            if (hadImmersiveDrag && !isFullyShown()) {
                                slideTo(0f, 250, showInterpolator) { hadImmersiveDrag = false }
                            } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                hadImmersiveDrag = false
                            }
                        }
                    }
                    suppressAutoFromRecyclerUntilIdle = false
                    return
                }
                if (suppressAutoFromRecyclerUntilIdle && newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    suppressAutoFromRecyclerUntilIdle = false
                }
                if (suppressAutoFromRecyclerUntilIdle) return
                when (newState) {
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        // When at the bottom of the list the player must always be visible.
                        // This also recovers the case where DRAGGING hid the player
                        // mid-overscroll and IDLE fires with the player fully hidden.
                        if (!rv.canScrollVertically(1) && !isManuallyControlled) {
                            if (!isFullyShown()) {
                                slideTo(0f, 250, showInterpolator)
                            }
                            return
                        }
                        // Always snap to shown when the preference is on.
                        if (isAlwaysVisible) {
                            if (!isFullyShown()) {
                                slideTo(0f, 250, showInterpolator)
                            }
                            return
                        }
                        if (isFullyShown() || isFullyHidden()) return
                        if (translationY <= hideDistance / 2f)
                            slideTo(0f, 250, showInterpolator)
                        else
                            slideTo(hideDistance, 250, hideInterpolator)
                    }
                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        // Only hide on drag-start when there is more list content below and
                        // the always-visible preference is not active.
                        if (!isAlwaysVisible && isFullyShown() && rv.canScrollVertically(1)) {
                            slideTo(hideDistance, 250, hideInterpolator)
                        }
                    }
                }
            }
        }
        recyclerView.addOnScrollListener(listener)
        attached[recyclerView] = listener

        // Remove any stale decoration left behind by a previous release() call (e.g., after
        // a predictive-back cancel) before adding a fresh one. Without this, each re-attach
        // cycle stacks another FooterSpacingItemDecoration on the same RecyclerView, doubling
        // (and re-doubling) the footer spacing with every back-gesture cancel.
        pendingReleaseDecorations.remove(recyclerView)?.let { stale ->
            recyclerView.removeItemDecoration(stale)
        }

        // Add a footer spacing decoration so the last list item is never permanently
        // hidden beneath the floating miniplayer — mirrors AppHeader's header decoration.
        val footerDeco = FooterSpacingItemDecoration(getRequiredFooterSpacing())
        footerDecorations[recyclerView] = footerDeco
        recyclerView.addItemDecoration(footerDeco)

        suppressAutoFromRecyclerUntilIdle = false
    }

    @Suppress("unused")
    fun attachToRecyclerViews(vararg rvs: RecyclerView) = rvs.forEach { attachToRecyclerView(it) }

    /**
     * Detaches the scroll listener from [rv] and releases the footer spacing decoration
     * without removing it from the list.
     *
     * Using [FooterSpacingItemDecoration.release] instead of
     * [FooterSpacingItemDecoration.detach] intentionally keeps the bottom spacing in place
     * while the host fragment is being torn down, preventing a visible layout jump in the
     * [RecyclerView] during the exit transition.
     *
     * The released decoration is also added to [pendingReleaseDecorations] so that a
     * subsequent [attachToRecyclerView] call on the same [RecyclerView] (e.g., after a
     * predictive-back cancel) can remove the stale decoration before adding a fresh one.
     *
     * @param rv The [RecyclerView] to detach from.
     */
    fun detachFromRecyclerView(rv: RecyclerView) {
        attached.remove(rv)?.let { rv.removeOnScrollListener(it) }
        footerDecorations.remove(rv)?.let { deco ->
            pendingReleaseDecorations[rv] = deco
            deco.release()
        }
    }

    @Suppress("unused")
    fun detachFromRecyclerViews(vararg rvs: RecyclerView) = rvs.forEach { detachFromRecyclerView(it) }

    /**
     * Detaches scroll listeners from all attached [RecyclerView]s and releases every
     * footer spacing decoration without removing them from their lists.
     *
     * See [detachFromRecyclerView] for the rationale behind using
     * [FooterSpacingItemDecoration.release] instead of [FooterSpacingItemDecoration.detach].
     * Released decorations are also added to [pendingReleaseDecorations].
     */
    fun detachFromAllRecyclerViews() {
        attached.forEach { (rv, l) -> rv.removeOnScrollListener(l) }
        attached.clear()
        footerDecorations.forEach { (rv, deco) ->
            pendingReleaseDecorations[rv] = deco
            deco.release()
        }
        footerDecorations.clear()
    }

    // -------------------------------------------------------------------------
    // NestedScrollView scroll-hide support
    // -------------------------------------------------------------------------

    /** Attached scroll views mapped to their [NestedScrollView.OnScrollChangeListener]. */
    private val attachedScrollViews: MutableMap<NestedScrollView, NestedScrollView.OnScrollChangeListener> = mutableMapOf()

    /**
     * Original bottom padding of each attached [NestedScrollView] recorded at attach time
     * so it can be fully restored on [detachFromScrollView].
     */
    private val scrollViewOriginalPaddings: MutableMap<NestedScrollView, Int> = mutableMapOf()

    /**
     * Per-NSV [Runnable] that snaps the player to fully shown or fully hidden once
     * the scroll view comes to rest, mirroring [RecyclerView.SCROLL_STATE_IDLE] behavior.
     */
    private val scrollViewSnapRunnables: MutableMap<NestedScrollView, Runnable> = mutableMapOf()

    /** Handler used to post snap runnables from [attachedScrollViews]. */
    private val scrollSnapHandler = Handler(Looper.getMainLooper())

    /**
     * Returns whether [nsv] can scroll vertically in at least one direction.
     *
     * @param nsv The [NestedScrollView] to check.
     * @return `true` if the scroll view has scrollable content.
     */
    private fun isNsvScrollable(nsv: NestedScrollView): Boolean =
        nsv.canScrollVertically(1) || nsv.canScrollVertically(-1)

    /**
     * Applies the current footer spacing to [scrollView] by adding it on top of the
     * original bottom padding that was stored at attach time. The view's
     * [NestedScrollView.clipToPadding] is set to `false` so content is never clipped.
     *
     * @param scrollView The [NestedScrollView] to update.
     */
    private fun applyScrollViewPadding(scrollView: NestedScrollView) {
        val orig = scrollViewOriginalPaddings[scrollView] ?: return
        val spacing = getRequiredFooterSpacing()
        scrollView.setPadding(
                scrollView.paddingLeft,
                scrollView.paddingTop,
                scrollView.paddingRight,
                orig + spacing + marginBottom
        )
        scrollView.clipToPadding = false
    }

    /**
     * Pushes the current [getRequiredFooterSpacing] value to every attached
     * [NestedScrollView], called from [onSizeChanged] in the same way as
     * [updateFooterDecorations] handles [RecyclerView] footer decorations.
     */
    private fun updateScrollViewPaddings() {
        if (attachedScrollViews.isEmpty()) return
        attachedScrollViews.keys.forEach {
            applyScrollViewPadding(it)
        }
    }

    /**
     * Attaches this [MiniPlayer] to a [NestedScrollView] so the player auto-hides while
     * scrolling downward and re-appears when the bottom of the content is reached.
     *
     * Dynamically adds bottom padding equal to the player's visible footprint so the last
     * piece of content is never permanently hidden beneath the floating player.
     *
     * @param scrollView The [NestedScrollView] to observe.
     * @author Hamza417
     */
    fun attachToScrollView(scrollView: NestedScrollView) {
        if (attachedScrollViews.containsKey(scrollView)) return

        // Record the original padding before we modify it.
        scrollViewOriginalPaddings[scrollView] = scrollView.paddingBottom

        val snapRunnable = Runnable {
            if (!isManuallyControlled) {
                if (!scrollView.canScrollVertically(1) || isAlwaysVisible) {
                    if (!isFullyShown()) {
                        slideTo(0f, 250, showInterpolator)
                    }
                } else {
                    if (translationY <= hideDistance / 2f) {
                        slideTo(0f, 250, showInterpolator)
                    } else {
                        slideTo(hideDistance, 250, hideInterpolator)
                    }
                }
            }
        }
        scrollViewSnapRunnables[scrollView] = snapRunnable

        val listener = NestedScrollView.OnScrollChangeListener { nsv, _, scrollY, _, oldScrollY ->
            val dy = scrollY - oldScrollY
            if (!isNsvScrollable(nsv)) return@OnScrollChangeListener

            // At the bottom: always bring the player back into view.
            if (!nsv.canScrollVertically(1)
                    && !isManuallyControlled
                    && !suppressAutoFromRecyclerUntilIdle) {
                if (!isFullyShown()) {
                    slideTo(0f, 250, showInterpolator)
                }
                return@OnScrollChangeListener
            }

            updateForScrollDelta(dy)

            // Snap to fully shown or hidden once scrolling comes to rest.
            scrollSnapHandler.removeCallbacks(snapRunnable)
            scrollSnapHandler.postDelayed(snapRunnable, SCROLL_SNAP_DELAY_MS)
        }

        scrollView.setOnScrollChangeListener(listener)
        attachedScrollViews[scrollView] = listener
        applyScrollViewPadding(scrollView)
        suppressAutoFromRecyclerUntilIdle = false
    }

    /**
     * Detaches from a previously attached [NestedScrollView], removing its scroll listener
     * and restoring the original bottom padding.
     *
     * @param scrollView The [NestedScrollView] to detach from.
     * @author Hamza417
     */
    fun detachFromScrollView(scrollView: NestedScrollView) {
        scrollViewSnapRunnables.remove(scrollView)?.let { scrollSnapHandler.removeCallbacks(it) }
        attachedScrollViews.remove(scrollView)?.let {
            scrollView.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)
        }
        scrollViewOriginalPaddings.remove(scrollView)?.let { orig ->
            scrollView.setPadding(
                    scrollView.paddingLeft,
                    scrollView.paddingTop,
                    scrollView.paddingRight,
                    orig
            )
        }
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        if (isTransparent) return
        applyConfig()
        updateStroke()
        invalidate()
    }

    override fun onAccentChanged(accent: Accent) {
        super.onAccentChanged(accent)
        updateStroke()
        refreshRippleTheme()

        val newAccent = accent.primaryAccentColor
        val oldAccent = progressAccentColor

        if (oldAccent == newAccent) {
            invalidate()
            return
        }

        accentColorAnimator?.cancel()
        accentColorAnimator = ValueAnimator.ofArgb(oldAccent, newAccent).apply {
            duration = 300L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progressAccentColor = it.animatedValue as Int
                // Animate the paint-shadow color in lockstep so accent changes from album
                // art mode transition the card shadow smoothly alongside the progress bar.
                refreshPaintShadowColor()
                invalidate()
            }
            start()
        }
    }

    // -------------------------------------------------------------------------
    // Window attachment / detachment
    // -------------------------------------------------------------------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ThemeManager.addListener(this)
        suppressAutoFromRecyclerUntilIdle = false
        resetManualHandler.removeCallbacks(resetManualRunnable)
        isManuallyControlled = false
        hadImmersiveDrag = false

        elevation = dp(DEFAULT_ELEVATION_DP)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
            }
        }
        clipToOutline = false // art clipping is handled manually; shadow must not be clipped

        // Apply the persisted shadow mode — must come AFTER the elevation line above so
        // setUsePaintShadow(true) can correctly zero it out.
        setUsePaintShadow(AccessibilityPreferences.isDarkerMiniplayerShadow())

        updateStroke()

        // Apply margins synchronously so that hideDistance is already correct when
        // onRestoreInstanceState → applyPendingTy runs.  Using post{} caused a race where
        // applyPendingTy consumed pendingRestoreFraction with the XML margins, and then
        // post{} changed the margins → wrong hideDistance → a sliver of the player visible.
        val m = dp(SIDE_MARGIN_DP).toInt()
        baseSideMarginPx = m
        baseCornerRadiusPx = AppearancePreferences.getCornerRadius()
        isFlatMode = !UserInterfacePreferences.isMarginAroundMiniplayer()
        prevHideDistance = 0f // reset so first onSizeChanged does not try to re-anchor

        (layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            if (isFlatMode) {
                cornerRadiusPx = 0f
                lp.setMargins(0, 0, 0, 0)
                lp.marginStart = 0
                lp.marginEnd = 0
                layoutParams = lp
                rebuildCardClipPath()
                invalidateOutline()
                refreshRippleTheme()
            } else {
                lp.setMargins(m, m, m, m + navBarInsetPx)
                lp.marginStart = m
                lp.marginEnd = m
                layoutParams = lp
            }
            updateFooterDecorations()
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            navBarInsetPx = nav.bottom
            if (!isFlatMode) {
                val lp = v.layoutParams as? ViewGroup.MarginLayoutParams
                    ?: return@setOnApplyWindowInsetsListener insets
                lp.bottomMargin = baseSideMarginPx + navBarInsetPx
                lp.setMargins(baseSideMarginPx, baseSideMarginPx, baseSideMarginPx, baseSideMarginPx + navBarInsetPx)
                lp.marginStart = baseSideMarginPx
                lp.marginEnd = baseSideMarginPx
                v.layoutParams = lp
            }
            insets
        }

        if (isInEditMode.not()) {
            registerSharedPreferenceChangeListener()
        }
    }

    override fun onDetachedFromWindow() {
        ThemeManager.removeListener(this)
        detachFromAllRecyclerViews()
        scrollEngine.cancelAnimation()
        rippleAutoReleaseHandler.removeCallbacks(rippleAutoReleaseRunnable)
        releaseAllRipples()
        ppAnimator?.cancel()
        ppSlideAnimator?.cancel()
        bgColorAnimator?.cancel()
        accentColorAnimator?.cancel()
        elevationAnimator?.cancel()
        edgeFadeAnimator?.cancel()
        progressBarAlphaAnimator?.cancel()
        progressValueAnimator?.cancel()
        seekThumbAlphaAnimator?.cancel()
        tyAnimator?.cancel()
        resetManualHandler.removeCallbacks(resetManualRunnable)
        isManuallyControlled = false
        hadImmersiveDrag = false
        prevHideDistance = 0f
        super.onDetachedFromWindow()
        unregisterSharedPreferenceChangeListener()
    }

    // -------------------------------------------------------------------------
    // State save / restore
    // -------------------------------------------------------------------------

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val fraction = if (hideDistance > 0f) (translationY / hideDistance).coerceIn(0f, 1f) else 0f
        return SavedState(superState).also {
            it.savedTranslationY = translationY
            it.fraction = fraction
            it.isTransparent = isTransparent
            it.isPlaying = ppIsPlaying
            it.currentPage = scrollEngine.currentPage
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            pendingRestoreFraction = state.fraction.takeIf { it in 0f..1f }
            if (pendingRestoreFraction == null) pendingRestoreTranslationY = state.savedTranslationY
            if (state.isTransparent) makeTransparent(animated = false)
            scrollEngine.jumpToPage(state.currentPage.coerceAtLeast(0))
            ppIsPlaying = state.isPlaying
            playPauseDrawer.progress = if (ppIsPlaying) 0f else 1f
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

    // -------------------------------------------------------------------------
    // Preferences-driven customizations
    // -------------------------------------------------------------------------

    private fun updateStroke() {
        if (!isInEditMode) {
            if (AccessibilityPreferences.isStrokeAroundMiniplayerOn()) {
                setStroke(
                        enabled = true,
                        color = ThemeManager.theme.textViewTheme.tertiaryTextColor,
                        widthDp = 0.5f)
            } else {
                setStrokeEnabled(false)
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            AccessibilityPreferences.STROKE_AROUND_MINIPLAYER -> {
                updateStroke()
            }
            AccessibilityPreferences.DARKER_MINIPLAYER_SHADOW -> {
                setUsePaintShadow(AccessibilityPreferences.isDarkerMiniplayerShadow())
            }
            UserInterfacePreferences.MARGIN_AROUND_MINIPLAYER -> {
                applyMarginMode()
            }
            AppearancePreferences.APP_CORNER_RADIUS, AppearancePreferences.APP_FONT -> {
                applyConfig()
                rebuildCardClipPath()
                invalidateOutline()
                invalidate()
            }
            BehaviourPreferences.MINIPLAYER_ALWAYS_VISIBLE -> {
                isAlwaysVisible = BehaviourPreferences.isMiniplayerAlwaysVisible()
                //                // When the toggle is switched on, snap the player into view immediately
                //                // so the user sees the effect right away.
                //                if (isAlwaysVisible && !isManuallyControlled && !isFullyShown()) {
                //                    animate().cancel()
                //                    animate().translationY(0f)
                //                        .setDuration(250)
                //                        .setInterpolator(showInterpolator)
                //                        .start()
                //                }
            }
        }
    }
}

