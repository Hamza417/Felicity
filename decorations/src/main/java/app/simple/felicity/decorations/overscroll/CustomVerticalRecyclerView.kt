package app.simple.felicity.decorations.overscroll

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.ShapeDrawable
import android.util.AttributeSet
import android.util.Log
import android.widget.EdgeEffect
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import app.simple.felicity.core.utils.BarHeight
import app.simple.felicity.core.utils.WindowUtil
import app.simple.felicity.decoration.R
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.itemdecorations.DividerItemDecoration
import app.simple.felicity.decorations.theme.ThemeRecyclerView
import app.simple.felicity.decorations.utils.RecyclerViewUtils.flingTranslationMagnitude
import app.simple.felicity.decorations.utils.RecyclerViewUtils.overScrollTranslationMagnitude
import app.simple.felicity.preferences.AccessibilityPreferences
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.shared.utils.ConditionUtils.invert
import app.simple.felicity.theme.managers.ThemeManager

/**
 * Custom recycler view with nice layout animation and
 * smooth overscroll effect and various states retention
 */
open class CustomVerticalRecyclerView(context: Context, attrs: AttributeSet?) : ThemeRecyclerView(context, attrs),
                                                                                DynamicAnimation.OnAnimationUpdateListener {

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs)

    private var manuallyAnimated = false
    private var fastScroll = true
    private var isEdgeColorRequired = true
    private var statusBarPaddingRequired = true
    private var navigationBarPaddingRequired = true

    private var dividerItemDecoration: DividerItemDecoration? = null

    private var edgeColor = 0

    init {
        if (isInEditMode.invert()) {
            context.theme.obtainStyledAttributes(attrs, R.styleable.RecyclerView, 0, 0).apply {
                try {
                    edgeColor = app.simple.felicity.theme.managers.ThemeManager.accent.primaryAccentColor

                    if (getBoolean(R.styleable.RecyclerView_isFadingEdgeRequired, false)) {
                        isVerticalFadingEdgeEnabled = true
                        setFadingEdgeLength(BarHeight.getStatusBarHeight(resources) + paddingTop)
                    }

                    fastScroll = getBoolean(R.styleable.RecyclerView_isFastScrollRequired, true)
                    manuallyAnimated = getBoolean(R.styleable.RecyclerView_manuallyAnimated, false)
                    isEdgeColorRequired = getBoolean(R.styleable.RecyclerView_isEdgeColorRequired, true)

                    statusBarPaddingRequired = getBoolean(R.styleable.RecyclerView_statusPaddingRequired, true)
                    navigationBarPaddingRequired = getBoolean(R.styleable.RecyclerView_navigationPaddingRequired, true)

                    if (statusBarPaddingRequired && navigationBarPaddingRequired) {
                        fitsSystemWindows = true
                    } else {
                        if (statusBarPaddingRequired) {
                            WindowUtil.getStatusBarHeightWhenAvailable(this@CustomVerticalRecyclerView) { height ->
                                setPadding(paddingLeft, height + paddingTop, paddingRight, paddingBottom)
                            }
                        }

                        if (navigationBarPaddingRequired) {
                            WindowUtil.getNavigationBarHeightWhenAvailable(this@CustomVerticalRecyclerView) { height ->
                                setPadding(paddingLeft, paddingTop, paddingRight, height + paddingBottom)
                            }
                        }

                    }

                    if (AccessibilityPreferences.isAnimationReduced()) {
                        layoutAnimation = null
                    }
                } finally {
                    recycle()
                }
            }

            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = !AccessibilityPreferences.isAnimationReduced()
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            setHasFixedSize(true)

            if (statusBarPaddingRequired || navigationBarPaddingRequired) {
                clipToPadding = false
            }

            addDividers()

            this.edgeEffectFactory = object : EdgeEffectFactory() {
                override fun createEdgeEffect(recyclerView: RecyclerView, direction: Int): EdgeEffect {
                    return object : EdgeEffect(recyclerView.context) {
                        override fun onPull(deltaDistance: Float) {
                            super.onPull(deltaDistance)
                            handlePull(deltaDistance)
                            setEdgeColor()
                            // clearDividerDecorations()
                        }

                        override fun onPull(deltaDistance: Float, displacement: Float) {
                            super.onPull(deltaDistance, displacement)
                            handlePull(deltaDistance)
                            setEdgeColor()
                            // clearDividerDecorations()
                        }

                        private fun handlePull(deltaDistance: Float) {
                            /**
                             * This is called on every touch event while the list is scrolled with a finger.
                             * simply update the view properties without animation.
                             */
                            val sign = if (direction == DIRECTION_BOTTOM) -1 else 1

                            /**
                             * This value decides how fast the recycler view views should move when
                             * they're being overscrolled. Often it is determined using the area of the
                             * recycler view because its length is how far the finger can move hence
                             * the overscroll value.
                             */
                            // val overscrollLengthConst = if (isLandscape) recyclerView.height else recyclerView.height / 2
                            val translationYDelta = sign * recyclerView.height / 2 * deltaDistance * overScrollTranslationMagnitude

                            recyclerView.forEachVisibleHolder { holder: VerticalListViewHolder ->
                                holder.translationY.cancel()
                                holder.itemView.translationY += translationYDelta
                            }
                        }

                        override fun onRelease() {
                            super.onRelease()
                            setEdgeColor()
                            /**
                             * The finger is lifted. This is when we should start the animations to bring
                             * the view property values back to their resting states.
                             */
                            recyclerView.forEachVisibleHolder { holder: VerticalListViewHolder ->
                                try {
                                    holder.translationY.cancel()
                                    holder.translationY.removeUpdateListener(this@CustomVerticalRecyclerView)
                                } catch (e: UnsupportedOperationException) {
                                    Log.e("CustomVerticalRecyclerView", "onRelease: ", e)
                                }

                                try {
                                    holder.translationY.addUpdateListener(this@CustomVerticalRecyclerView)
                                } catch (e: UnsupportedOperationException) {
                                    Log.e("CustomVerticalRecyclerView", "onRelease: ${e.message}")
                                }

                                holder.translationY.start()
                            }
                        }

                        override fun onAbsorb(velocity: Int) {
                            super.onAbsorb(velocity)
                            setEdgeColor()
                            val sign = if (direction == DIRECTION_BOTTOM) -1 else 1

                            /**
                             * The list has reached the edge on fling
                             */
                            val translationVelocity = sign * velocity * flingTranslationMagnitude
                            recyclerView.forEachVisibleHolder { holder: VerticalListViewHolder ->
                                try {
                                    holder.translationY.cancel()
                                    holder.translationY.removeUpdateListener(this@CustomVerticalRecyclerView)
                                } catch (e: UnsupportedOperationException) {
                                    Log.e("CustomVerticalRecyclerView", "onRelease: ", e)
                                }

                                try {
                                    holder.translationY.addUpdateListener(this@CustomVerticalRecyclerView)
                                } catch (e: UnsupportedOperationException) {
                                    Log.e("CustomVerticalRecyclerView", "onRelease: ${e.message}")
                                }

                                holder.translationY
                                    .setStartVelocity(translationVelocity)
                                    .start()
                            }
                        }

                        /**
                         * Have to call from all [EdgeEffect.onPull], [EdgeEffect.onRelease],
                         * [EdgeEffect.onAbsorb] functions to make sure the edge colors don't appear
                         * on non-required places. This is how it is but works.
                         */
                        private fun setEdgeColor() {
                            @Suppress("LiftReturnOrAssignment")
                            if (!isEdgeColorRequired) {
                                color = app.simple.felicity.theme.managers.ThemeManager.theme.viewGroupTheme.backgroundColor
                            } else {
                                color = edgeColor
                            }
                        }
                    }
                }
            }
        }
    }

    private fun addDividers() {
        if (AccessibilityPreferences.isDividerEnabled()) {
            dividerItemDecoration = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)

            dividerItemDecoration!!.setDrawable(ShapeDrawable().apply {
                intrinsicHeight = 1
                paint.color = app.simple.felicity.theme.managers.ThemeManager.theme.viewGroupTheme.dividerColor
            })

            addItemDecoration(dividerItemDecoration!!)
        }
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        clearAnimation()

        try {
            adapter?.stateRestorationPolicy = Adapter.StateRestorationPolicy.ALLOW
        } catch (e: UnsupportedOperationException) {
            e.printStackTrace()
        }

        if (this.adapter != null) {
            this.clearAnimation()
            this.animate()
                .alpha(0F)
                .setDuration(300L)
                .withEndAction {
                    super.setAdapter(adapter)
                    this.animate()
                        .alpha(1f)
                        .setDuration(300L)
                        .start()
                }
        } else {
            super.setAdapter(adapter)
        }

        if (!manuallyAnimated && isInEditMode.invert()) {
            if (!AccessibilityPreferences.isAnimationReduced()) {
                scheduleLayoutAnimation()
            }
        }

        SectionedFastScroller.attach(this)
    }

    override fun isPaddingOffsetRequired(): Boolean {
        return statusBarPaddingRequired || navigationBarPaddingRequired
    }

    override fun getTopPaddingOffset(): Int {
        return if (statusBarPaddingRequired) {
            -paddingTop
        } else {
            0
        }
    }

    override fun getBottomPaddingOffset(): Int {
        return if (navigationBarPaddingRequired) {
            paddingBottom
        } else {
            0
        }
    }

    override fun getBottomFadingEdgeStrength(): Float {
        return super.getBottomFadingEdgeStrength()
    }

    private inline fun <reified T : VerticalListViewHolder> RecyclerView.forEachVisibleHolder(action: (T) -> Unit) {
        for (i in 0 until childCount) {
            action(getChildViewHolder(getChildAt(i)) as T)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            AppearancePreferences.ACCENT_COLOR -> {
                edgeColor = ThemeManager.accent.primaryAccentColor
            }
        }
    }

    override fun onAnimationUpdate(animation: DynamicAnimation<*>?, value: Float, velocity: Float) {
        invalidateItemDecorations()
    }
}
