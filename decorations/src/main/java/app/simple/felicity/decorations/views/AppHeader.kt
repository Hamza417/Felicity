package app.simple.felicity.decorations.views

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.DecelerateInterpolator
import androidx.annotation.LayoutRes
import androidx.annotation.MainThread
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.itemdecorations.HeaderSpacingItemDecoration
import app.simple.felicity.decorations.theme.ThemeFrameLayout
import app.simple.felicity.shared.utils.UnitUtils.dpToPx
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
 *
 * Header spacing is achieved via [HeaderSpacingItemDecoration] added to the RecyclerView.
 * This avoids any RecyclerView padding changes, which interfere with drag-and-drop operations.
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
    private var hideThresholdPx: Int = dpToPx(10F).toInt()
    private var accumulatedScroll = 0
    private var isHidden = false

    private var contentView: View? = null
    private var contentCreatedListener: ((View) -> Unit)? = null

    private var manualOverride = false
    private var statusBarPaddingApplied = false
    private var isScrollListenerAttached = false

    /** Decoration owned by this header; added/removed alongside the scroll listener. */
    private var spacingDecoration: HeaderSpacingItemDecoration? = null

    private val layoutChangeListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        updateSpacingDecoration()
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            if (dy == 0) return
            if (manualOverride) return
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
                if (!statusBarPaddingApplied) {
                    setPadding(paddingLeft, height + paddingTop, paddingRight, paddingBottom)
                    post { updateSpacingDecoration() }
                }
                statusBarPaddingApplied = true
            }
        }

        addOnLayoutChangeListener(layoutChangeListener)
    }

    // --------------------------
    // Attach / Detach
    // --------------------------

    /** Attach header to a RecyclerView so it can respond to scrolling. */
    @MainThread
    fun attachTo(rv: RecyclerView, mode: ScrollMode = scrollMode, adjustPadding: Boolean = true) {
        Log.d(TAG, "Attaching to RecyclerView with mode=$mode")
        if (recyclerView == rv && scrollMode == mode) {
            rv.removeOnScrollListener(scrollListener)
            rv.addOnScrollListener(scrollListener)
            isScrollListenerAttached = true
            updateSpacingDecoration()
            return
        }

        detach()
        recyclerView = rv
        scrollMode = mode

        rv.removeOnScrollListener(scrollListener)
        rv.addOnScrollListener(scrollListener)
        isScrollListenerAttached = true

        // Install our spacing decoration
        val deco = HeaderSpacingItemDecoration(height)
        spacingDecoration = deco
        rv.addItemDecoration(deco, 0) // insert at index 0 so it runs first

        // If height is already known, update now; otherwise the layoutChangeListener will fire
        if (height > 0) {
            deco.updateHeaderHeight(height)
        }
    }

    /** Detach from currently attached RecyclerView. */
    @MainThread
    fun detach() {
        val rv = recyclerView ?: return
        rv.removeOnScrollListener(scrollListener)
        isScrollListenerAttached = false
        spacingDecoration?.detach()
        spacingDecoration = null
        recyclerView = null
    }

    // --------------------------
    // Spacing decoration helpers
    // --------------------------

    /**
     * Called whenever the header lays out so the decoration offset stays in sync.
     * Safe to call at any time — the decoration itself only triggers invalidateItemDecorations()
     * which does NOT scroll the list.
     */
    private fun updateSpacingDecoration() {
        val h = height
        if (h <= 0) return
        spacingDecoration?.updateHeaderHeight(h)
    }

    // --------------------------
    // Content
    // --------------------------

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
        post { updateSpacingDecoration() }
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

    // --------------------------
    // Scroll modes
    // --------------------------

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

    @Suppress("unused")
    private fun animateTranslation(target: Float) {
        animate().translationY(target)
            .setInterpolator(DecelerateInterpolator())
            .setDuration(180L)
            .start()
    }

    // --------------------------
    // Public API
    // --------------------------

    /** Kept for API compatibility; no-op since padding is no longer used. */
    fun reapplyRecyclerPadding() {
        updateSpacingDecoration()
    }

    /** Kept for API compatibility; no-op since padding is no longer used. */
    fun setAdjustRecyclerPadding(adjust: Boolean) {
        // No-op: spacing is handled by HeaderSpacingItemDecoration
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val rv = recyclerView
        if (rv != null) {
            attachTo(rv, scrollMode)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        recyclerView?.removeOnScrollListener(scrollListener)
        isScrollListenerAttached = false
        removeOnLayoutChangeListener(layoutChangeListener)
    }

    fun isHeaderHidden(): Boolean = isHidden

    fun clearContent() {
        removeAllViews()
        contentView = null
    }

    fun forEachChild(action: (View) -> Unit) {
        children.forEach(action)
    }

    fun hiddenRatio(): Float = if (height > 0) accumulatedScroll / height.toFloat() else 0f

    fun hideHeader(animated: Boolean = true, override: Boolean = true) {
        val action = {
            accumulatedScroll = height
            isHidden = true
            translationY = -height.toFloat()
        }
        if (height == 0) {
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

    fun toggleHeader(animated: Boolean = true) {
        if (isHidden) showHeader(animated, override = true) else hideHeader(animated, override = true)
    }

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

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        return SavedState(superState).apply {
            modeOrdinal = scrollMode.ordinal
            hideThreshold = hideThresholdPx
            savedAccumulatedScroll = accumulatedScroll
            savedIsHidden = isHidden
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
        manualOverride = false

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
            updateSpacingDecoration()
        }
    }

    private class SavedState : BaseSavedState {
        var modeOrdinal: Int = 0
        var hideThreshold: Int = 0
        var savedAccumulatedScroll: Int = 0
        var savedIsHidden: Boolean = false

        constructor(superState: Parcelable?) : super(superState)
        private constructor(parcel: Parcel) : super(parcel) {
            modeOrdinal = parcel.readInt()
            hideThreshold = parcel.readInt()
            savedAccumulatedScroll = parcel.readInt()
            savedIsHidden = parcel.readInt() == 1
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(modeOrdinal)
            out.writeInt(hideThreshold)
            out.writeInt(savedAccumulatedScroll)
            out.writeInt(if (savedIsHidden) 1 else 0)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }

    companion object {
        private const val TAG = "AppHeader"
    }
}
