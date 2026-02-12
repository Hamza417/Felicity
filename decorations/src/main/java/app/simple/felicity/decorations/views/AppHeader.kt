package app.simple.felicity.decorations.views

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.DecelerateInterpolator
import androidx.annotation.LayoutRes
import androidx.annotation.MainThread
import androidx.core.view.children
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.theme.ThemeFrameLayout
import app.simple.felicity.shared.utils.WindowUtil
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
    private var statusBarPaddingApplied = false

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
                if (statusBarPaddingApplied.not()) {
                    setPadding(paddingLeft, height + paddingTop, paddingRight, paddingBottom)
                    // Header height changed due to inset; reapply list padding
                    post { maybeApplyRecyclerPadding(force = true) }
                }
                statusBarPaddingApplied = true
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
        addView(view, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
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
        val previousPaddingTop = rv.paddingTop
        val paddingDelta = desiredTop - previousPaddingTop
        if (rv.paddingTop != desiredTop) {
            rv.setPadding(rv.paddingLeft, desiredTop, rv.paddingRight, rv.paddingBottom)
            rv.clipToPadding = false
        }
        lastAppliedHeaderHeight = headerHeight

        // Force proper positioning of items, especially when data loads instantly
        ensureListStartBelowPadding(rv)

        // Use multiple delayed attempts to ensure proper positioning
        rv.post {
            ensureListStartBelowPadding(rv)
            if (rv.canScrollVertically(RecyclerView.VERTICAL)) {
                rv.scrollBy(0, -paddingDelta)

                // scrollBy may have made the header visible, so
                // restore the header position to based on the last
                // known scroll state (only if idle to avoid disrupting user scroll)
                // TODO
            }

            // Second attempt after potential adapter notifications
            rv.post {
                ensureListStartBelowPadding(rv)
            }
        }
    }

    private fun ensureListStartBelowPadding(rv: RecyclerView) {
        // Avoid interfering with active user drag/fast-scroll; only adjust when idle
        if (rv.scrollState != RecyclerView.SCROLL_STATE_IDLE || rv.isComputingLayout) {
            // Try again shortly when things settle
            rv.post {
                if (rv.scrollState == RecyclerView.SCROLL_STATE_IDLE && !rv.isComputingLayout) {
                    ensureListStartBelowPadding(rv)
                }
            }
            return
        }

        val firstChild = rv.getChildAt(0) ?: return
        val desiredTop = rv.paddingTop
        val currentTop = firstChild.top

        // Calculate item decoration offset for the first item
        val itemDecorationOffset = getFirstItemDecorationTopOffset(rv, firstChild)

        // The actual desired position should account for item decoration spacing
        val adjustedDesiredTop = desiredTop + itemDecorationOffset
        val delta = adjustedDesiredTop - currentTop

        // If the first item is positioned above the adjusted padding area, we need to adjust
        if (delta > 0) {
            // Check if we're at or near the top of the list to avoid disrupting user scroll
            val scrollOffset = rv.computeVerticalScrollOffset()

            // Get first visible item position for more accurate checking
            val layoutManager = rv.layoutManager
            val firstVisiblePosition = when (layoutManager) {
                is LinearLayoutManager -> layoutManager.findFirstVisibleItemPosition()
                is GridLayoutManager -> layoutManager.findFirstVisibleItemPosition()
                is StaggeredGridLayoutManager -> {
                    val positions = layoutManager.findFirstVisibleItemPositions(null)
                    positions.minOrNull() ?: -1
                }
                else -> -1
            }

            // Allow adjustment in these scenarios:
            // 1. At absolute top with no scroll history
            // 2. Very close to top (within adjustment range)
            // 3. First item is visible (position 0) regardless of scroll state
            val isAtTop = scrollOffset == 0 && !rv.canScrollVertically(-1)
            val isNearTop = scrollOffset <= delta
            val isFirstItemVisible = firstVisiblePosition == 0

            if (isAtTop || isNearTop || isFirstItemVisible) {
                // For instant loading scenarios, we might need to invalidate layout first
                if (scrollOffset == 0 && firstVisiblePosition == 0 && (rv.adapter?.itemCount ?: 0) > 0) {
                    // This is likely an instant loading scenario - force a layout pass
                    rv.requestLayout()
                    rv.post {
                        // Re-check after layout and adjust if needed (still only if idle)
                        if (rv.scrollState != RecyclerView.SCROLL_STATE_IDLE || rv.isComputingLayout) return@post
                        val updatedFirstChild = rv.getChildAt(0)
                        if (updatedFirstChild != null) {
                            val updatedItemDecorationOffset = getFirstItemDecorationTopOffset(rv, updatedFirstChild)
                            val updatedAdjustedDesiredTop = rv.paddingTop + updatedItemDecorationOffset
                            val updatedDelta = updatedAdjustedDesiredTop - updatedFirstChild.top
                            if (updatedDelta > 0) {
                                rv.scrollBy(0, -updatedDelta)
                            }
                        }
                    }
                } else {
                    // Normal case - just scroll to correct position (only if idle)
                    if (rv.scrollState == RecyclerView.SCROLL_STATE_IDLE && !rv.isComputingLayout) {
                        rv.scrollBy(0, -delta)
                    }
                }
            }
        }
    }

    /**
     * Calculate the top offset that should be applied to the first item due to item decorations.
     * This accounts for spacing that gets applied after layout.
     */
    private fun getFirstItemDecorationTopOffset(rv: RecyclerView, firstChild: View): Int {
        val tempRect = android.graphics.Rect()
        var totalTopOffset = 0

        // Iterate through all item decorations and calculate their contribution to top spacing
        for (i in 0 until rv.itemDecorationCount) {
            val decoration = rv.getItemDecorationAt(i)
            val position = rv.getChildAdapterPosition(firstChild)

            // Create a temporary state for calculation
            val state = RecyclerView.State()

            // Get the offsets this decoration would apply to the first item
            tempRect.setEmpty()
            decoration.getItemOffsets(tempRect, firstChild, rv, state)

            // For the first item, we only care about top spacing
            // Some decorations (like SongHolderSpacingItemDecoration) apply spacing to position 1
            // Others (like SpacingItemDecoration) apply spacing to position 0
            if (position <= 1) { // Cover both cases
                totalTopOffset += tempRect.top
            }
        }

        return totalTopOffset
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

    @Suppress("SameParameterValue")
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

    // --------------------------
    // State saving / restoring
    // --------------------------
    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        return SavedState(superState).apply {
            modeOrdinal = scrollMode.ordinal
            hideThreshold = hideThresholdPx
            savedAccumulatedScroll = accumulatedScroll
            savedIsHidden = isHidden
            savedManualOverride = manualOverride
            savedAdjustRecyclerPadding = adjustRecyclerPadding
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        val modes = ScrollMode.entries
        if (state.modeOrdinal in modes.indices) {
            scrollMode = modes[state.modeOrdinal]
        }
        hideThresholdPx = state.hideThreshold
        accumulatedScroll = state.savedAccumulatedScroll
        isHidden = state.savedIsHidden
        manualOverride = state.savedManualOverride
        adjustRecyclerPadding = state.savedAdjustRecyclerPadding

        // Apply translation after we're laid out so we know our height
        post {
            when (scrollMode) {
                ScrollMode.PINNED -> {
                    accumulatedScroll = 0
                    isHidden = false
                    translationY = 0f
                }
                ScrollMode.HIDE_ON_SCROLL, ScrollMode.SCROLL_WITH_CONTENT -> {
                    val h = height
                    val clamped = if (h > 0) accumulatedScroll.coerceIn(0, h) else accumulatedScroll
                    accumulatedScroll = clamped
                    translationY = -clamped.toFloat()
                    isHidden = if (scrollMode == ScrollMode.HIDE_ON_SCROLL && h > 0) {
                        clamped == h
                    } else {
                        clamped > 0
                    }
                }
            }
            maybeApplyRecyclerPadding(force = true)
        }
    }

    private class SavedState : BaseSavedState {
        var modeOrdinal: Int = 0
        var hideThreshold: Int = 0
        var savedAccumulatedScroll: Int = 0
        var savedIsHidden: Boolean = false
        var savedManualOverride: Boolean = false
        var savedAdjustRecyclerPadding: Boolean = true

        constructor(superState: Parcelable?) : super(superState)
        private constructor(parcel: Parcel) : super(parcel) {
            modeOrdinal = parcel.readInt()
            hideThreshold = parcel.readInt()
            savedAccumulatedScroll = parcel.readInt()
            savedIsHidden = parcel.readInt() == 1
            savedManualOverride = parcel.readInt() == 1
            savedAdjustRecyclerPadding = parcel.readInt() == 1
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(modeOrdinal)
            out.writeInt(hideThreshold)
            out.writeInt(savedAccumulatedScroll)
            out.writeInt(if (savedIsHidden) 1 else 0)
            out.writeInt(if (savedManualOverride) 1 else 0)
            out.writeInt(if (savedAdjustRecyclerPadding) 1 else 0)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }
}
