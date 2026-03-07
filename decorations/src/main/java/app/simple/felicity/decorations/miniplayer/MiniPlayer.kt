package app.simple.felicity.decorations.miniplayer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
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
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.decorations.miniplayer.MiniPlayer.Companion.ARTIST_TEXT_SIZE_SP
import app.simple.felicity.decorations.miniplayer.MiniPlayer.Companion.TITLE_TEXT_SIZE_SP
import app.simple.felicity.decorations.typeface.TypeFace
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.AccessibilityPreferences
import app.simple.felicity.preferences.AppearancePreferences
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
        /** The visible page settled at [position] (either from user swipe or [setCurrentItem]). */
        fun onPageSelected(position: Int) {}

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
        const val SCROLL_STATE_IDLE = MiniPlayerScrollEngine.IDLE
        const val SCROLL_STATE_DRAGGING = MiniPlayerScrollEngine.DRAGGING
        const val SCROLL_STATE_SETTLING = MiniPlayerScrollEngine.SETTLING

        /** Default card elevation in dp. */
        private const val DEFAULT_ELEVATION_DP = 24f

        /** Duration for show/hide slide animation in ms. */
        private const val ANIM_DURATION_MS = 180L

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
        private const val TITLE_TEXT_SIZE_SP = 13f

        /** Default artist text size in SP. */
        private const val ARTIST_TEXT_SIZE_SP = 11.5f

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
            }

            override fun onScrollStateChanged(state: Int) {
                dispatchScrollState(state)
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
        callbacks?.onPageSelected(position)
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && !isBeingDragged) {
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
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.x - lastMotionX
                if (!isBeingDragged && abs(dx) > touchSlop * 0.6f) {
                    isBeingDragged = true
                    scrollEngine.notifyScrollState(SCROLL_STATE_DRAGGING)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    animateEdgeFade(show = true)
                    animatePlayPauseSlide(slideOut = true)
                }
                if (isBeingDragged) {
                    scrollEngine.applyDragDelta(dx)
                }
                lastMotionX = event.x
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val vx = velocityTracker?.xVelocity ?: 0f
                if (isBeingDragged) {
                    scrollEngine.finishDrag(vx, dragStartScrollPx)
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    performClick()
                }
                velocityTracker?.recycle()
                velocityTracker = null
                isBeingDragged = false
                animateEdgeFade(show = false)
                animatePlayPauseSlide(slideOut = false)
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
            if (e.x < w - btnZoneWidth) callbacks?.onItemClick(scrollEngine.currentPage.coerceAtLeast(0))
        }
        return true
    }

    override fun onLongPress(e: MotionEvent) {
        if (!isInPlayPauseZone(e.x)) {
            callbacks?.onItemLongClick(scrollEngine.currentPage.coerceAtLeast(0))
        }
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        if (scrollEngine.scrollState == SCROLL_STATE_DRAGGING) return false
        scrollEngine.finishDrag(velocityX, dragStartScrollPx)
        return true
    }

    override fun performClick(): Boolean = super.performClick()

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

    private val cardRect = RectF()
    private val strokeRect = RectF()
    private val artDstRect = RectF()
    private val artSrcRect = Rect()
    private val cardClipPath = Path()

    // -------------------------------------------------------------------------
    // Edge-fade effect
    // -------------------------------------------------------------------------

    /** Whether the edge-fade effect is enabled. Toggle with [setEdgeFadeEnabled]. */
    private var edgeFadeEnabled = true

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

        titlePaint.textSize = sp(titleTextSizeSp)
        artistPaint.textSize = sp(artistTextSizeSp)

        val font = AppearancePreferences.getAppFont()
        titlePaint.typeface = TypeFace.getTypeFace(font, 3 /* BOLD */, context)
        artistPaint.typeface = TypeFace.getTypeFace(font, 1 /* REGULAR */, context)

        val newRadius = AppearancePreferences.getCornerRadius()
        if (newRadius != cornerRadiusPx || cardRect.width() != w) {
            cornerRadiusPx = newRadius
            if (w > 0f && h > 0f) {
                cardClipPath.rewind()
                cardClipPath.addRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
            }
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
        cardClipPath.rewind()
        cardClipPath.addRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)

        edgeFadeWidth = (w * EDGE_FADE_WIDTH_FRACTION).coerceAtLeast(dp(EDGE_FADE_MIN_WIDTH_DP))
        rebuildEdgeFadeRects(w, h)

        playPauseDrawer.btnZoneWidth = btnZoneWidth
        playPauseDrawer.centerX = w - btnZoneWidth / 2f
        playPauseDrawer.centerY = h / 2f
        playPauseDrawer.updateGeometry(btnSz)

        scrollEngine.viewWidth = w.toInt()
    }

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
        animateElevation(0f, ANIM_DURATION_MS, animated)
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
        val oldTitle = titleColor;
        val oldArtist = artistColor;
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

        // 3. Pages (art + text) with optional edge fades
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

        // 4. Play/pause button (slides off during drag)
        playPauseDrawer.draw(canvas)
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

    /** Slide the player into view. Pass `animated = false` for an instant jump. */
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

    /** Slide the player out of view. Pass `animated = false` for an instant jump. */
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
                f >= 0.995f -> hideDistance
                f <= 0.005f -> 0f
                else -> (f * hideDistance).coerceIn(0f, hideDistance)
            }
            applied = true
        }
        if (!applied) pendingRestoreTranslationY?.let {
            animate().cancel()
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
                                animate().translationY(0f).setDuration(250).setInterpolator(showInterpolator)
                                    .withEndAction { hadImmersiveDrag = false }.start()
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
                        if (isFullyShown() || isFullyHidden()) return
                        if (translationY <= hideDistance / 2f)
                            animate().translationY(0f).setDuration(250).setInterpolator(showInterpolator).start()
                        else
                            animate().translationY(hideDistance).setDuration(250).setInterpolator(hideInterpolator).start()
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
        attached.forEach { (rv, l) -> rv.removeOnScrollListener(l) }
        attached.clear()
    }

    // -------------------------------------------------------------------------
    // Theme callbacks
    // -------------------------------------------------------------------------

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        if (isTransparent) return
        applyConfig()
        updateStroke()
        invalidate()
    }

    override fun onAccentChanged(accent: Accent) {
        super.onAccentChanged(accent)
        updateStroke()
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

        updateStroke()

        post {
            val lp = layoutParams as? ViewGroup.MarginLayoutParams ?: return@post
            val m = dp(SIDE_MARGIN_DP).toInt()
            baseSideMarginPx = m
            lp.setMargins(m, m, m, m + navBarInsetPx)
            layoutParams = lp
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            navBarInsetPx = nav.bottom
            val lp = v.layoutParams as? ViewGroup.MarginLayoutParams
                ?: return@setOnApplyWindowInsetsListener insets
            lp.bottomMargin = baseSideMarginPx + navBarInsetPx
            v.layoutParams = lp
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
        ppAnimator?.cancel()
        ppSlideAnimator?.cancel()
        bgColorAnimator?.cancel()
        elevationAnimator?.cancel()
        edgeFadeAnimator?.cancel()
        resetManualHandler.removeCallbacks(resetManualRunnable)
        isManuallyControlled = false
        hadImmersiveDrag = false
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
                        widthDp = 1f)
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
        }
    }
}

