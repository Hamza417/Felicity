package app.simple.felicity.decorations.views

import android.animation.TimeInterpolator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.theme.ThemeMaterialCardView
import kotlin.math.abs

/**
 * Generic container view that hosts arbitrary content via ViewBinding.
 * Provides an onViewCreated-style callback and can attach to multiple
 * RecyclerViews to hide on scroll down and show on scroll up.
 */
class MiniPlayer @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val card: ThemeMaterialCardView

    // Reintroduce internal RecyclerView attach/detach support
    private val attached: MutableMap<RecyclerView, RecyclerView.OnScrollListener> = mutableMapOf()

    private val animDuration = 180L
    private val animInterpolator: TimeInterpolator = AccelerateDecelerateInterpolator()
    private val showInterpolator = DecelerateInterpolator()
    private val hideInterpolator = AccelerateInterpolator()

    // Consider near-zero or near-hideDistance as stable states
    private val epsilon = 1f

    private val hideDistance: Float
        get() {
            val bm = (layoutParams as? MarginLayoutParams)?.bottomMargin ?: 0
            return height.toFloat() + bm.toFloat()
        }

    private var currentBinding: ViewBinding? = null

    // Preserve/restoration support
    private var pendingRestoreTranslationY: Float? = null
    private var pendingRestoreFraction: Float? = null

    // When true, ignore RecyclerView-driven updates until next IDLE
    private var suppressAutoFromRecyclerUntilIdle: Boolean = false
    private var isManuallyControlled: Boolean = false

    private val resetManualControlHandler = Handler(Looper.getMainLooper())
    private val resetManualControlRunnable = Runnable {
        isManuallyControlled = false
    }

    init {
        // Inflate base container layout (root is <merge/>)
        LayoutInflater.from(context).inflate(R.layout.miniplayer_view, this, true)
        card = findViewById(R.id.container)
        isVisible = true
    }

    // region Content API

    /**
     * Set/replace content with a ViewBinding. Calls [onViewCreated] after the view is attached.
     */
    fun <T : ViewBinding> setContent(binding: T, onViewCreated: (T) -> Unit = {}) {
        // Remove existing
        removeContent()
        // Attach new
        card.addView(binding.root)
        currentBinding = binding
        onViewCreated(binding)
    }

    /** Remove current content view, if any. */
    fun removeContent() {
        if (card.isNotEmpty()) card.removeAllViews()
        currentBinding = null
    }

    /** Returns the currently set ViewBinding, if any. */
    @Suppress("UNCHECKED_CAST", "unused")
    fun <T : ViewBinding> getContent(): T? = currentBinding as? T

    // region Hide/Show animation
    fun show(animated: Boolean = true) {
        // Ensure no conflicting animations
        animate().cancel()
        // Make this view visible in case it was set to GONE/INVISIBLE
        isVisible = true
        alpha = 1f
        // Clear any pending restore so explicit command wins
        pendingRestoreFraction = null
        pendingRestoreTranslationY = null
        // Prevent RecyclerView listeners from immediately overriding this explicit command
        suppressAutoFromRecyclerUntilIdle = true
        isManuallyControlled = true
        // Defer until laid out to get correct hideDistance
        if (height == 0) {
            addOnLayoutChangeListener(object : OnLayoutChangeListener {
                override fun onLayoutChange(v: View?, left: Int, top: Int, right: Int, bottom: Int,
                                            oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
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
        // Keep part of layout; we hide by translating, not by visibility
        isVisible = true
        pendingRestoreFraction = null
        pendingRestoreTranslationY = null
        // Prevent RecyclerView listeners from immediately overriding this explicit command
        suppressAutoFromRecyclerUntilIdle = true
        isManuallyControlled = true
        if (height == 0) {
            addOnLayoutChangeListener(object : OnLayoutChangeListener {
                override fun onLayoutChange(v: View?, left: Int, top: Int, right: Int, bottom: Int,
                                            oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                    removeOnLayoutChangeListener(this)
                    hide(animated)
                }
            })
            return
        }
        animateTranslationY(hideDistance, animated)
    }
    // endregion

    @Suppress("unused")
    /** Public API for the attacher to move the view with scroll delta. */
    fun offsetBy(dy: Int) {
        updateForScrollDelta(dy)
    }

    @Suppress("unused")
    /** Snap helpers for attacher */
    fun snapToShown(animated: Boolean = true) = animateTranslationY(0f, animated)

    @Suppress("unused")
    fun snapToHidden(animated: Boolean = true) = animateTranslationY(hideDistance, animated)

    private fun animateTranslationY(target: Float, animated: Boolean) {
        if (!animated) {
            translationY = target
            // Allow RecyclerView-driven updates again after explicit movement completes
            suppressAutoFromRecyclerUntilIdle = false
            // Delay resetting manual control to prevent immediate scroll interference
            resetManualControlHandler.removeCallbacks(resetManualControlRunnable)
            resetManualControlHandler.postDelayed(resetManualControlRunnable, 500)
            return
        }
        animate().translationY(target)
            .setDuration(animDuration)
            .setInterpolator(animInterpolator)
            .withEndAction {
                suppressAutoFromRecyclerUntilIdle = false
                // Delay resetting manual control to prevent immediate scroll interference
                resetManualControlHandler.removeCallbacks(resetManualControlRunnable)
                resetManualControlHandler.postDelayed(resetManualControlRunnable, 500)
            }
            .start()
    }

    // Move the container incrementally with scroll delta and clamp within [0, hideDistance]
    private fun updateForScrollDelta(dy: Int) {
        if (height == 0) return // not laid out yet
        if (suppressAutoFromRecyclerUntilIdle || isManuallyControlled) return // honor explicit show/hide
        // Cancel any ongoing animation for responsive drag-follow
        animate().cancel()
        val target = (translationY + dy).coerceIn(0f, hideDistance)
        if (target != translationY) translationY = target
    }

    // Clamp translation when size changes (e.g., rotation, insets change)
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h > 0) {
            // If size changed, preserve relative offset to keep hidden state intact
            if (oldh > 0 && translationY > 0f) {
                val bm = (layoutParams as? MarginLayoutParams)?.bottomMargin ?: 0
                val oldHideDistance = oldh.toFloat() + bm
                if (oldHideDistance > 0f) {
                    val fraction = (translationY / oldHideDistance).coerceIn(0f, 1f)
                    val newTy = (fraction * hideDistance).coerceIn(0f, hideDistance)
                    if (newTy != translationY) translationY = newTy
                }
            }
            // Apply any pending restore after layout is ready
            applyPendingTranslationIfPossible()
        }
    }

    // Helpers to detect stable states
    private fun isFullyShown(): Boolean = translationY <= epsilon
    private fun isFullyHidden(): Boolean = abs(translationY - hideDistance) <= epsilon

    // region State save/restore
    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val fraction = if (hideDistance > 0f) (translationY / hideDistance).coerceIn(0f, 1f) else 0f
        return SavedState(superState).also {
            it.translationY = this.translationY
            it.fraction = fraction
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            // Prefer restoring by fraction so hidden stays hidden even if height changes
            pendingRestoreFraction = state.fraction.takeIf { it in 0f..1f }
            // Fallback to absolute translation if fraction isn't valid
            if (pendingRestoreFraction == null) pendingRestoreTranslationY = state.translationY
            applyPendingTranslationIfPossible()
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    private fun applyPendingTranslationIfPossible() {
        if (height <= 0) {
            // Defer until laid out
            addOnLayoutChangeListener(object : OnLayoutChangeListener {
                override fun onLayoutChange(v: View?, left: Int, top: Int, right: Int, bottom: Int,
                                            oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                    removeOnLayoutChangeListener(this)
                    applyPendingTranslationIfPossible()
                }
            })
            return
        }

        var applied = false
        pendingRestoreFraction?.let { f ->
            animate().cancel()
            val ty = (f.coerceIn(0f, 1f) * hideDistance).coerceIn(0f, hideDistance)
            translationY = when {
                f >= 0.995f -> hideDistance // force exact hidden
                f <= 0.005f -> 0f           // force exact shown
                else -> ty
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

    // region RecyclerView attach API (active)
    @Suppress("unused")
    /** Attach to one or more RecyclerViews to auto hide/show on scroll. */
    fun attachToRecyclerViews(vararg recyclerViews: RecyclerView) {
        recyclerViews.forEach { attachToRecyclerView(it) }
    }

    /** Attach to a single RecyclerView. No-op if already attached. */
    fun attachToRecyclerView(recyclerView: RecyclerView) {
        if (attached.containsKey(recyclerView)) return
        val listener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                updateForScrollDelta(dy)
            }

            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                super.onScrollStateChanged(rv, newState)
                // Don't lift suppression or allow scroll behavior if manually controlled
                if (isManuallyControlled) return

                // Lift suppression only when user starts dragging and not manually controlled
                if (suppressAutoFromRecyclerUntilIdle && newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    suppressAutoFromRecyclerUntilIdle = false
                }
                if (suppressAutoFromRecyclerUntilIdle) return

                when (newState) {
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        // Preserve fully hidden/shown states; only snap if in-between
                        if (isFullyShown() || isFullyHidden()) return
                        val halfway = hideDistance / 2f
                        if (translationY <= halfway) {
                            animate().translationY(0f)
                                .setDuration(250)
                                .setInterpolator(showInterpolator)
                                .start()
                        } else {
                            animate().translationY(hideDistance)
                                .setDuration(250)
                                .setInterpolator(hideInterpolator)
                                .start()
                        }
                    }
                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        if (isFullyShown()) {
                            animate().translationY(hideDistance)
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
        // Ensure we start responsive to scroll immediately on (re)attach
        suppressAutoFromRecyclerUntilIdle = false
    }

    /** Detach from the given RecyclerViews. */
    fun detachFromRecyclerViews(vararg recyclerViews: RecyclerView) {
        recyclerViews.forEach { detachFromRecyclerView(it) }
    }

    /** Detach from a single RecyclerView. */
    fun detachFromRecyclerView(recyclerView: RecyclerView) {
        val listener = attached.remove(recyclerView) ?: return
        recyclerView.removeOnScrollListener(listener)
    }

    /** Detach from all attached RecyclerViews. */
    fun detachFromAllRecyclerViews() {
        attached.forEach { (rv, listener) -> rv.removeOnScrollListener(listener) }
        attached.clear()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Ensure we don't carry suppression across fragment/activity transitions
        suppressAutoFromRecyclerUntilIdle = false
        resetManualControlHandler.removeCallbacks(resetManualControlRunnable)
        isManuallyControlled = false
    }

    override fun onDetachedFromWindow() {
        detachFromAllRecyclerViews()
        // Clear any suppression to avoid sticky state on reattach
        suppressAutoFromRecyclerUntilIdle = false
        resetManualControlHandler.removeCallbacks(resetManualControlRunnable)
        isManuallyControlled = false
        super.onDetachedFromWindow()
    }

    // region SavedState class
    internal class SavedState : BaseSavedState {
        var translationY: Float = 0f
        var fraction: Float = -1f

        constructor(superState: Parcelable?) : super(superState)
        constructor(source: Parcel) : super(source) {
            translationY = source.readFloat()
            fraction = try { source.readFloat() } catch (e: Exception) { -1f }
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeFloat(translationY)
            out.writeFloat(fraction)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }
}