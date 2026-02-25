package app.simple.felicity.decorations.views

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import app.simple.felicity.decorations.corners.DynamicCornerMaterialCardView
import app.simple.felicity.decorations.typeface.TypeFaceTextView
import app.simple.felicity.shared.utils.ColorUtils.animateColorChange
import app.simple.felicity.theme.managers.ThemeManager
import kotlin.math.abs

/**
 * Self-contained mini-player card that IS a [DynamicCornerMaterialCardView].
 *
 * Layout (single flat card, no wrapper):
 *   ┌──────────────────────────────────────────────────┐
 *   │ [AlbumArt] ←─── swipeable pager ────→ [▶/⏸]     │
 *   │             Title                                │
 *   │             Artist                               │
 *   └──────────────────────────────────────────────────┘
 *
 * No external layout file or ViewBinding is required.
 * Wire up [callbacks] to drive image loading, play/pause, and navigation.
 *
 * All scroll-hide/show behavior is preserved.
 *
 * ### Transparent mode
 * [makeTransparent] zeroes elevation, makes the card background transparent and
 * forces all text/icon colors to white so content stays legible over any backdrop.
 * [makeOpaque] reverses everything back to the current theme colors.
 */
class MiniPlayer @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : DynamicCornerMaterialCardView(context, attrs, defStyleAttr) {

    // ── UI ───────────────────────────────────────────────────────────────────

    private val pager: ViewPager2
    private val playPause: FlipPlayPauseView

    // ── Adapter ──────────────────────────────────────────────────────────────

    private val innerAdapter = InnerAdapter()
    private var items: List<MiniPlayerItem> = emptyList()

    /**
     * All [TypeFaceTextView]s currently bound across visible pages.
     * Used to switch color mode when toggling transparency.
     */
    private val activeTextViews: MutableSet<TypeFaceTextView> = mutableSetOf()

    // ── Callbacks ────────────────────────────────────────────────────────────

    interface Callbacks {
        /** Called when the user swipes to a new page and settles on [position]. */
        fun onPageSelected(position: Int) {}

        /** Load album art into [imageView] using [payload] (e.g. an Audio object). */
        fun onLoadArt(imageView: ImageView, payload: Any?) {}

        /** Called when the play/pause button is clicked. */
        fun onPlayPauseClick() {}

        /** Called when a page row is tapped. */
        fun onItemClick(position: Int) {}

        /** Called when a page row is long-pressed. */
        fun onItemLongClick(position: Int) {}
    }

    var callbacks: Callbacks? = null

    // ── Transparency state ────────────────────────────────────────────────────

    private var isTransparent: Boolean = false
    private var opaqueCardColor: Int = Color.TRANSPARENT
    private var transparentColorAnimator: ValueAnimator? = null

    // ── Margin / inset bookkeeping ────────────────────────────────────────────

    /** The uniform side margin applied in init (dp → px). */
    private var baseSideMarginPx: Int = 0

    /** The last nav-bar inset height received, so we can combine it with baseSideMarginPx. */
    private var navBarInsetPx: Int = 0

    // ── Scroll-hide bookkeeping ───────────────────────────────────────────────

    private val attached: MutableMap<RecyclerView, RecyclerView.OnScrollListener> = mutableMapOf()

    private val animDuration = 180L
    private val animInterpolator: TimeInterpolator = AccelerateDecelerateInterpolator()
    private val showInterpolator = DecelerateInterpolator()
    private val hideInterpolator = AccelerateInterpolator()
    private val epsilon = 1f

    private val hideDistance: Float
        get() {
            val bm = (layoutParams as? MarginLayoutParams)?.bottomMargin ?: 0
            Log.d("MiniPlayer", "Calculating hide distance: height=$height, bottomMargin=${(layoutParams as? MarginLayoutParams)?.bottomMargin ?: 0}")
            return height.toFloat() + bm.toFloat()
        }

    private var pendingRestoreTranslationY: Float? = null
    private var pendingRestoreFraction: Float? = null
    private var suppressAutoFromRecyclerUntilIdle: Boolean = false
    private var isManuallyControlled: Boolean = false
    private var hadImmersiveDrag: Boolean = false

    private val resetManualControlHandler = Handler(Looper.getMainLooper())
    private val resetManualControlRunnable = Runnable { isManuallyControlled = false }

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        // The card IS this view — just set its layout margin once inflated
        post {
            val lp = layoutParams as? MarginLayoutParams ?: return@post
            val margin = dp(15)
            baseSideMarginPx = margin
            lp.setMargins(margin, margin, margin, margin + navBarInsetPx)
            layoutParams = lp
        }

        // ViewPager2 fills the remaining width
        pager = ViewPager2(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(64), 1f)
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 1
            adapter = innerAdapter
        }

        // Play/Pause button on the trailing edge
        playPause = FlipPlayPauseView(context).apply {
            val sz = dp(40)
            layoutParams = LinearLayout.LayoutParams(sz, sz).also {
                val hMargin = dp(10)
                it.setMargins(hMargin, 0, hMargin, 0)
                it.gravity = Gravity.CENTER_VERTICAL
            }
        }

        // Single horizontal row — no extra wrapper needed
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT)
            addView(pager)
            addView(playPause)
        }

        addView(row)
        isVisible = true

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                callbacks?.onPageSelected(position)
            }
        })

        playPause.setOnClickListener { callbacks?.onPlayPauseClick() }

        // Keep the card above the navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            navBarInsetPx = nav.bottom
            val lp = v.layoutParams as? MarginLayoutParams ?: return@setOnApplyWindowInsetsListener insets
            lp.bottomMargin = baseSideMarginPx + navBarInsetPx
            v.layoutParams = lp
            insets
        }
    }

    // ── Public data API ───────────────────────────────────────────────────────

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(newItems: List<MiniPlayerItem>) {
        items = newItems
        innerAdapter.notifyDataSetChanged()
    }

    fun setCurrentItem(position: Int, smoothScroll: Boolean = true) {
        if (position in items.indices) {
            pager.setCurrentItem(position, smoothScroll)
        }
    }

    val currentItem: Int get() = pager.currentItem

    // ── Playback state ────────────────────────────────────────────────────────

    fun setPlaying(playing: Boolean, animate: Boolean = true) {
        if (playing) playPause.playing(animate) else playPause.paused(animate)
    }

    // ── Transparency ──────────────────────────────────────────────────────────

    fun makeTransparent(animated: Boolean = true) {
        if (isTransparent) return
        isTransparent = true

        // Remember the current opaque background so we can restore it
        opaqueCardColor = cardBackgroundColor.defaultColor

        // Background → fully transparent.
        // DO NOT touch elevation/cardElevation — MaterialCardView uses cardElevation to
        // compute compat shadow padding, so changing it shifts the view's bounds and
        // causes the bottom margin to visually jump.  Instead, zero the shadow outline
        // colors to achieve the flat/invisible shadow look.
        animateCardColor(opaqueCardColor, Color.TRANSPARENT, animated)
        outlineAmbientShadowColor = Color.TRANSPARENT
        outlineSpotShadowColor = Color.TRANSPARENT

        // Texts → white
        activeTextViews.forEach { tv -> tv.animateColorChange(Color.WHITE) }

        // Icon → white
        if (animated) {
            ValueAnimator.ofArgb(playPause.iconColor, Color.WHITE).apply {
                duration = animDuration
                addUpdateListener { playPause.iconColor = it.animatedValue as Int }
                start()
            }
        } else {
            playPause.iconColor = Color.WHITE
        }
    }

    fun makeOpaque(animated: Boolean = true) {
        if (!isTransparent) return
        isTransparent = false

        val target = if (opaqueCardColor != Color.TRANSPARENT) opaqueCardColor
        else ThemeManager.theme.viewGroupTheme.backgroundColor

        // Restore background and shadow colors (elevation itself is never changed).
        animateCardColor(Color.TRANSPARENT, target, animated)
        val accentColor = ThemeManager.accent.primaryAccentColor
        outlineAmbientShadowColor = accentColor
        outlineSpotShadowColor = accentColor

        // Texts → theme primary / secondary colors
        val primaryColor = ThemeManager.theme.textViewTheme.primaryTextColor
        val secondaryColor = ThemeManager.theme.textViewTheme.secondaryTextColor
        activeTextViews.forEach { tv ->
            val targetColor = if (tv.tag == TAG_ARTIST) secondaryColor else primaryColor
            tv.animateColorChange(targetColor)
        }

        // Icon → theme colour
        val iconColor = ThemeManager.theme.iconTheme.regularIconColor
        if (animated) {
            ValueAnimator.ofArgb(playPause.iconColor, iconColor).apply {
                duration = animDuration
                addUpdateListener { playPause.iconColor = it.animatedValue as Int }
                start()
            }
        } else {
            playPause.iconColor = iconColor
        }
    }

    private fun animateCardColor(from: Int, to: Int, animated: Boolean) {
        transparentColorAnimator?.cancel()
        if (!animated || from == to) {
            setCardBackgroundColor(to)
            return
        }
        transparentColorAnimator = ValueAnimator.ofArgb(from, to).apply {
            duration = animDuration
            interpolator = animInterpolator
            addUpdateListener { setCardBackgroundColor(it.animatedValue as Int) }
            start()
        }
    }

    // ── Show / Hide ───────────────────────────────────────────────────────────

    fun show(animated: Boolean = true) {
        animate().cancel()
        isVisible = true
        alpha = 1f
        pendingRestoreFraction = null
        pendingRestoreTranslationY = null
        suppressAutoFromRecyclerUntilIdle = true
        isManuallyControlled = true
        if (height == 0) {
            addOnLayoutChangeListener(object : OnLayoutChangeListener {
                override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int,
                                            ol: Int, ot: Int, or2: Int, ob: Int) {
                    removeOnLayoutChangeListener(this)
                    show(animated)
                }
            })
            return
        }
        animateTranslationY(0f, animated)
    }

    fun hide(animated: Boolean = true) {
        animate().cancel()
        isVisible = true
        pendingRestoreFraction = null
        pendingRestoreTranslationY = null
        suppressAutoFromRecyclerUntilIdle = true
        isManuallyControlled = true
        if (height == 0) {
            addOnLayoutChangeListener(object : OnLayoutChangeListener {
                override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int,
                                            ol: Int, ot: Int, or2: Int, ob: Int) {
                    removeOnLayoutChangeListener(this)
                    hide(animated)
                }
            })
            return
        }
        animateTranslationY(hideDistance, animated)
    }

    @Suppress("unused")
    fun offsetBy(dy: Int) = updateForScrollDelta(dy)

    @Suppress("unused")
    fun snapToShown(animated: Boolean = true) = animateTranslationY(0f, animated)

    @Suppress("unused")
    fun snapToHidden(animated: Boolean = true) = animateTranslationY(hideDistance, animated)

    private fun animateTranslationY(target: Float, animated: Boolean) {
        if (!animated) {
            translationY = target
            suppressAutoFromRecyclerUntilIdle = false
            resetManualControlHandler.removeCallbacks(resetManualControlRunnable)
            resetManualControlHandler.postDelayed(resetManualControlRunnable, 500)
            return
        }
        animate().translationY(target)
            .setDuration(animDuration)
            .setInterpolator(animInterpolator)
            .withEndAction {
                suppressAutoFromRecyclerUntilIdle = false
                resetManualControlHandler.removeCallbacks(resetManualControlRunnable)
                resetManualControlHandler.postDelayed(resetManualControlRunnable, 500)
            }
            .start()
    }

    private fun updateForScrollDelta(dy: Int) {
        if (height == 0) return
        if (suppressAutoFromRecyclerUntilIdle || isManuallyControlled) return
        animate().cancel()
        val target = (translationY + dy).coerceIn(0f, hideDistance)
        if (target != translationY) translationY = target
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h > 0) {
            if (oldh > 0 && translationY > 0f) {
                val bm = (layoutParams as? MarginLayoutParams)?.bottomMargin ?: 0
                val oldHideDistance = oldh.toFloat() + bm
                if (oldHideDistance > 0f) {
                    val fraction = (translationY / oldHideDistance).coerceIn(0f, 1f)
                    val newTy = (fraction * hideDistance).coerceIn(0f, hideDistance)
                    if (newTy != translationY) translationY = newTy
                }
            }
            applyPendingTranslationIfPossible()
        }
    }

    private fun isFullyShown(): Boolean = translationY <= epsilon
    private fun isFullyHidden(): Boolean = abs(translationY - hideDistance) <= epsilon

    // ── State save / restore ──────────────────────────────────────────────────

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val fraction = if (hideDistance > 0f) (translationY / hideDistance).coerceIn(0f, 1f) else 0f
        return SavedState(superState).also {
            it.translationY = this.translationY
            it.fraction = fraction
            it.isTransparent = this.isTransparent
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            pendingRestoreFraction = state.fraction.takeIf { it in 0f..1f }
            if (pendingRestoreFraction == null) pendingRestoreTranslationY = state.translationY
            // Restore transparency without animation (we're just rebuilding)
            if (state.isTransparent) makeTransparent(animated = false)
            applyPendingTranslationIfPossible()
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    private fun applyPendingTranslationIfPossible() {
        if (height <= 0) {
            addOnLayoutChangeListener(object : OnLayoutChangeListener {
                override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int,
                                            ol: Int, ot: Int, or2: Int, ob: Int) {
                    removeOnLayoutChangeListener(this)
                    applyPendingTranslationIfPossible()
                }
            })
            return
        }
        var applied = false
        pendingRestoreFraction?.let { f ->
            animate().cancel()
            translationY = when {
                f >= 0.995f -> hideDistance
                f <= 0.005f -> 0f
                else -> (f.coerceIn(0f, 1f) * hideDistance).coerceIn(0f, hideDistance)
            }
            applied = true
        }
        if (!applied) {
            pendingRestoreTranslationY?.let { ty ->
                animate().cancel()
                translationY = ty.coerceIn(0f, hideDistance)
            }
        }
        pendingRestoreFraction = null
        pendingRestoreTranslationY = null
    }

    // ── RecyclerView scroll-hide ──────────────────────────────────────────────

    private fun isRecyclerVerticallyScrollable(rv: RecyclerView): Boolean {
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
                if (!isRecyclerVerticallyScrollable(rv)) return
                updateForScrollDelta(dy)
            }

            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (isManuallyControlled) return
                val scrollable = isRecyclerVerticallyScrollable(rv)
                if (!scrollable) {
                    when (newState) {
                        RecyclerView.SCROLL_STATE_DRAGGING -> {
                            hadImmersiveDrag = true
                            if (!isFullyHidden()) {
                                animate().translationY(hideDistance)
                                    .setDuration(250)
                                    .setInterpolator(hideInterpolator).start()
                            }
                        }
                        RecyclerView.SCROLL_STATE_SETTLING, RecyclerView.SCROLL_STATE_IDLE -> {
                            if (hadImmersiveDrag && !isFullyShown()) {
                                animate().translationY(0f)
                                    .setDuration(250)
                                    .setInterpolator(showInterpolator)
                                    .withEndAction { hadImmersiveDrag = false }
                                    .start()
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
                        if (translationY <= hideDistance / 2f) {
                            animate()
                                .translationY(0f)
                                .setDuration(250)
                                .setInterpolator(showInterpolator)
                                .start()
                        } else {
                            animate()
                                .translationY(hideDistance)
                                .setDuration(250)
                                .setInterpolator(hideInterpolator)
                                .start()
                        }
                    }
                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        if (isFullyShown()) {
                            animate()
                                .translationY(hideDistance)
                                .setDuration(250)
                                .setInterpolator(hideInterpolator)
                                .start()
                        }
                    }
                }
            }
        }

        recyclerView.addOnScrollListener(listener)
        attached[recyclerView] = listener
        suppressAutoFromRecyclerUntilIdle = false
    }

    @Suppress("unused")
    fun attachToRecyclerViews(vararg recyclerViews: RecyclerView) =
        recyclerViews.forEach { attachToRecyclerView(it) }

    fun detachFromRecyclerView(recyclerView: RecyclerView) {
        attached.remove(recyclerView)?.let { recyclerView.removeOnScrollListener(it) }
    }

    @Suppress("unused")
    fun detachFromRecyclerViews(vararg recyclerViews: RecyclerView) =
        recyclerViews.forEach { detachFromRecyclerView(it) }

    fun detachFromAllRecyclerViews() {
        attached.forEach { (rv, l) -> rv.removeOnScrollListener(l) }
        attached.clear()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        suppressAutoFromRecyclerUntilIdle = false
        resetManualControlHandler.removeCallbacks(resetManualControlRunnable)
        isManuallyControlled = false
        hadImmersiveDrag = false
    }

    override fun onDetachedFromWindow() {
        detachFromAllRecyclerViews()
        suppressAutoFromRecyclerUntilIdle = false
        resetManualControlHandler.removeCallbacks(resetManualControlRunnable)
        isManuallyControlled = false
        hadImmersiveDrag = false
        super.onDetachedFromWindow()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    // ── Inner adapter ─────────────────────────────────────────────────────────

    private inner class InnerAdapter : RecyclerView.Adapter<InnerAdapter.PageHolder>() {

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val albumArt = ImageView(parent.context).apply {
                val sz = dp(64)
                layoutParams = LinearLayout.LayoutParams(sz, sz)
                scaleType = ImageView.ScaleType.CENTER_CROP
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }

            val title = TypeFaceTextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
                isSingleLine = true
                marqueeRepeatLimit = -1
                isSelected = true
                fontStyle = TypeFaceTextView.BOLD
                val hPad = dp(5)
                setPadding(hPad, 0, hPad, 0)
                tag = TAG_TITLE
            }

            val artist = TypeFaceTextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
                isSingleLine = true
                marqueeRepeatLimit = -1
                isSelected = true
                fontStyle = TypeFaceTextView.REGULAR
                val hPad = dp(5)
                setPadding(hPad, 0, hPad, 0)
                tag = TAG_ARTIST
            }

            val textBlock = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(title)
                addView(artist)
            }

            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT)
                addView(albumArt)
                addView(textBlock)
            }

            // If already in transparent mode, start new views as white immediately
            if (isTransparent) {
                title.setTextColor(Color.WHITE)
                artist.setTextColor(Color.WHITE)
            }

            return PageHolder(row, albumArt, title, artist)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.artist.text = item.artist
            callbacks?.onLoadArt(holder.albumArt, item.payload)

            holder.row.setOnClickListener { callbacks?.onItemClick(holder.bindingAdapterPosition) }
            holder.row.setOnLongClickListener {
                callbacks?.onItemLongClick(holder.bindingAdapterPosition)
                true
            }
        }

        override fun onViewAttachedToWindow(holder: PageHolder) {
            activeTextViews += holder.title
            activeTextViews += holder.artist
        }

        override fun onViewDetachedFromWindow(holder: PageHolder) {
            activeTextViews -= holder.title
            activeTextViews -= holder.artist
        }

        inner class PageHolder(
                val row: LinearLayout,
                val albumArt: ImageView,
                val title: TypeFaceTextView,
                val artist: TypeFaceTextView
        ) : RecyclerView.ViewHolder(row)
    }

    // ── SavedState ────────────────────────────────────────────────────────────

    internal class SavedState : BaseSavedState {
        var translationY: Float = 0f
        var fraction: Float = -1f
        var isTransparent: Boolean = false

        constructor(superState: Parcelable?) : super(superState)
        constructor(source: Parcel) : super(source) {
            translationY = source.readFloat()
            fraction = try {
                source.readFloat()
            } catch (_: Exception) {
                -1f
            }
            isTransparent = source.readInt() != 0
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeFloat(translationY)
            out.writeFloat(fraction)
            out.writeInt(if (isTransparent) 1 else 0)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }

    companion object {
        private const val TAG_TITLE = "mini_player_title"
        private const val TAG_ARTIST = "mini_player_artist"
    }
}