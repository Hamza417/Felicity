package app.simple.felicity.decorations.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.annotation.LayoutRes
import androidx.annotation.MainThread
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.core.utils.WindowUtil
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.theme.ThemeFrameLayout
import kotlin.math.max
import kotlin.math.min

/**
 * Generic header container that can host any custom view passed programmatically or via XML.
 * Responsibilities:
 *  - Acts as a prominent header at top of screen.
 *  - Provides scroll behaviors: PINNED, HIDE_ON_SCROLL, SCROLL_WITH_CONTENT.
 *  - Lets caller supply arbitrary content view via [setContentView] or XML attribute `headerContentLayout`.
 *  - Exposes lifecycle-style callback [onContentViewCreated] after inflating or setting the content.
 */
@Suppress("unused")
class AppHeader @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : ThemeFrameLayout(context, attrs, defStyleAttr) {

    enum class ScrollMode { PINNED, HIDE_ON_SCROLL, SCROLL_WITH_CONTENT }

    private var recyclerView: RecyclerView? = null
    private var scrollMode: ScrollMode = ScrollMode.PINNED
    private var hideThresholdPx: Int = dpToPx(10)
    private var accumulatedScroll = 0 // used as hidden offset in HIDE_ON_SCROLL (0..height)
    private var isHidden = false

    private var contentView: View? = null
    private var contentCreatedListener: ((View) -> Unit)? = null

    // Padding management
    private var originalRecyclerPaddingTop: Int = -1
    private var lastAppliedHeaderHeight: Int = -1
    private var adjustRecyclerPadding: Boolean = true
    private var manualOverride = false // when true, scroll-based behavior is suspended

    private val layoutChangeListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        maybeApplyRecyclerPadding()
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            if (dy == 0) return
            if (manualOverride) return // ignore scroll-based hiding while manually overridden
            when (scrollMode) {
                ScrollMode.PINNED -> Unit
                ScrollMode.HIDE_ON_SCROLL -> handleHideOnScroll(dy)
                ScrollMode.SCROLL_WITH_CONTENT -> handleScrollWithContent(dy)
            }
        }
    }

    init {
        // Parse attributes for initial setup
        context.obtainStyledAttributes(attrs, R.styleable.AppHeader).apply {
            val modeOrdinal = getInt(R.styleable.AppHeader_scrollMode, -1)
            val modes = ScrollMode.entries
            if (modeOrdinal in modes.indices) {
                scrollMode = modes[modeOrdinal]
            }
            hideThresholdPx = getDimensionPixelSize(R.styleable.AppHeader_hideThreshold, hideThresholdPx)
            val contentLayout = getResourceId(R.styleable.AppHeader_headerContentLayout, 0)
            recycle()
            if (contentLayout != 0) {
                inflateContent(contentLayout)
            }

            WindowUtil.getStatusBarHeightWhenAvailable(this@AppHeader) { height ->
                setPadding(paddingLeft, height + paddingTop, paddingRight, paddingBottom)
                // Header height changed due to inset; reapply list padding
                post { maybeApplyRecyclerPadding(force = true) }
            }
        }
        addOnLayoutChangeListener(layoutChangeListener)
    }

    /** Attach header to a RecyclerView so it can respond to scrolling. */
    @MainThread
    fun attachTo(rv: RecyclerView, mode: ScrollMode = scrollMode, adjustPadding: Boolean = true) {
        detach()
        recyclerView = rv
        scrollMode = mode
        adjustRecyclerPadding = adjustPadding
        rv.addOnScrollListener(scrollListener)
        // Apply padding after next layout pass if height not known yet
        if (height > 0) {
            maybeApplyRecyclerPadding(force = true)
        } else {
            post { maybeApplyRecyclerPadding(force = true) }
        }
    }

    /** Detach from currently attached RecyclerView. */
    @MainThread
    fun detach() {
        recyclerView?.removeOnScrollListener(scrollListener)
        restoreRecyclerPaddingIfNeeded()
        recyclerView = null
    }

    /** Inflate and set a layout resource as content of this header. */
    fun inflateContent(@LayoutRes layoutRes: Int): View {
        val view = LayoutInflater.from(context).inflate(layoutRes, this, false)
        setContentView(view)
        return view
    }

    /** Programmatically set the content view. Replaces any existing content. */
    fun setContentView(view: View) {
        if (view.parent == this) return // already set
        removeAllViews()
        contentView = view
        addView(view, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        contentCreatedListener?.invoke(view)
        // Reapply padding since header height may change
        post { maybeApplyRecyclerPadding(force = true) }
    }

    /** Alias for setContentView for semantic clarity */
    fun setHeaderView(view: View) = setContentView(view)

    /** Callback invoked after content view is created/assigned. */
    fun onContentViewCreated(listener: (View) -> Unit) {
        contentCreatedListener = listener
        contentView?.let { listener(it) }
    }

    /** Alias for onContentViewCreated for generic naming */
    fun onViewCreated(listener: (View) -> Unit) = onContentViewCreated(listener)

    fun getContentView(): View? = contentView

    fun setScrollMode(mode: ScrollMode) {
        scrollMode = mode
    }

    fun setHideThresholdPx(px: Int) {
        hideThresholdPx = px
    }

    fun resetScrollingState() {
        accumulatedScroll = 0
        isHidden = false
        animate().cancel()
        translationY = 0f
    }

    private fun handleHideOnScroll(dy: Int) {
        val h = height
        if (h <= 0) return
        // Accumulate offset (0 visible -> h fully hidden)
        accumulatedScroll = (accumulatedScroll + dy).coerceIn(0, h)
        translationY = -accumulatedScroll.toFloat()
        val fullyHidden = accumulatedScroll == h
        if (fullyHidden != isHidden) {
            isHidden = fullyHidden
        }
    }

    private fun handleScrollWithContent(dy: Int) {
        accumulatedScroll += dy
        val clamped = min(max(accumulatedScroll, 0), height)
        translationY = -clamped.toFloat()
    }

    private fun animateTranslation(target: Float) {
        animate().translationY(target)
            .setInterpolator(DecelerateInterpolator())
            .setDuration(180L)
            .start()
    }

    private fun maybeApplyRecyclerPadding(force: Boolean = false) {
        if (!adjustRecyclerPadding) return
        val rv = recyclerView ?: return
        val headerHeight = height
        if (headerHeight <= 0) return
        if (originalRecyclerPaddingTop == -1) {
            originalRecyclerPaddingTop = rv.paddingTop
        }
        if (!force && headerHeight == lastAppliedHeaderHeight) return
        val desiredTop = originalRecyclerPaddingTop + headerHeight
        if (rv.paddingTop != desiredTop) {
            rv.setPadding(rv.paddingLeft, desiredTop, rv.paddingRight, rv.paddingBottom)
            rv.clipToPadding = false
        }
        lastAppliedHeaderHeight = headerHeight
    }

    private fun restoreRecyclerPaddingIfNeeded() {
        val rv = recyclerView ?: return
        if (originalRecyclerPaddingTop != -1) {
            rv.setPadding(rv.paddingLeft, originalRecyclerPaddingTop, rv.paddingRight, rv.paddingBottom)
        }
        originalRecyclerPaddingTop = -1
        lastAppliedHeaderHeight = -1
    }

    fun reapplyRecyclerPadding() = maybeApplyRecyclerPadding(force = true)

    fun setAdjustRecyclerPadding(adjust: Boolean) {
        adjustRecyclerPadding = adjust
        if (adjust) reapplyRecyclerPadding() else restoreRecyclerPaddingIfNeeded()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        detach()
        removeOnLayoutChangeListener(layoutChangeListener)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    /** Convenience: true if header currently hidden (only for HIDE_ON_SCROLL mode). */
    fun isHeaderHidden(): Boolean = isHidden

    /** Remove current content without adding a new one. */
    fun clearContent() {
        removeAllViews()
        contentView = null
    }

    /** Iterate child views (normally just one) */
    fun forEachChild(action: (View) -> Unit) {
        children.forEach(action)
    }

    /** Ratio [0f,1f] of how much header is hidden (only meaningful for HIDE_ON_SCROLL). */
    fun hiddenRatio(): Float = if (height > 0) accumulatedScroll / height.toFloat() else 0f

    /** Manually hide the header. If [override] true, disables automatic scroll reactions until resumed. */
    fun hideHeader(animated: Boolean = true, override: Boolean = true) {
        val action = {
            accumulatedScroll = height
            isHidden = true
            translationY = -height.toFloat()
        }
        if (height == 0) { // Not laid out yet
            post { hideHeader(animated, override) }
            return
        }
        if (animated) {
            animate().translationY(-height.toFloat())
                .setDuration(180L)
                .withEndAction(action)
                .start()
        } else {
            action()
        }
        if (override) manualOverride = true
    }

    /** Manually show the header. If [override] true, keeps automatic behavior suspended until resumed. */
    fun showHeader(animated: Boolean = true, override: Boolean = true) {
        val action = {
            accumulatedScroll = 0
            isHidden = false
            translationY = 0f
        }
        if (animated) {
            animate().translationY(0f)
                .setDuration(180L)
                .withEndAction(action)
                .start()
        } else {
            action()
        }
        if (override) manualOverride = true
    }

    /** Toggle header visibility manually (always sets manual override). */
    fun toggleHeader(animated: Boolean = true) {
        if (isHidden) showHeader(animated, override = true) else hideHeader(animated, override = true)
    }

    /** Resume automatic scroll-based behavior. Optionally reset position. */
    fun resumeAutoBehavior(reset: Boolean = false) {
        manualOverride = false
        if (reset) {
            when (scrollMode) {
                ScrollMode.HIDE_ON_SCROLL, ScrollMode.SCROLL_WITH_CONTENT -> {
                    accumulatedScroll = 0
                    isHidden = false
                    translationY = 0f
                }
                else -> Unit
            }
        }
    }
}