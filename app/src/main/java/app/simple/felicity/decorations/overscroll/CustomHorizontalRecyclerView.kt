package app.simple.felicity.decorations.overscroll

import android.content.Context
import android.graphics.drawable.ShapeDrawable
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.EdgeEffect
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.decorations.padding.Utils
import app.simple.felicity.decorations.theme.ThemeRecyclerView
import app.simple.felicity.preferences.AccessibilityPreferences
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.utils.ConditionUtils.invert
import app.simple.felicity.utils.RecyclerViewUtils.flingTranslationMagnitude
import app.simple.felicity.utils.RecyclerViewUtils.overScrollRotationMagnitude
import app.simple.felicity.utils.RecyclerViewUtils.overScrollTranslationMagnitude
import app.simple.felicity.utils.StatusBarHeight
import kotlin.math.abs
import kotlin.math.pow

/**
 * Custom recycler view with nice layout animation and
 * smooth overscroll effect and various states retention
 */
open class CustomHorizontalRecyclerView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : ThemeRecyclerView(context, attrs, defStyleAttr) {

    // Change pow to control speed.
    // Bigger = faster. RecyclerView default is 5.
    private val POW = 1.0
    private var isLandscape = false
    private var interpolator: Interpolator? = null
    private var statusBarPaddingRequired = true
    private var navigationBarPaddingRequired = true

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.RecyclerView, defStyleAttr, 0).apply {
            try {
                statusBarPaddingRequired = getBoolean(R.styleable.RecyclerView_statusPaddingRequired, true)
                navigationBarPaddingRequired = getBoolean(R.styleable.RecyclerView_navigationPaddingRequired, true)

                Utils.applySystemBarPadding(this@CustomHorizontalRecyclerView, statusBarPaddingRequired, navigationBarPaddingRequired)

                isLandscape = StatusBarHeight.isLandscape(context)

                if (isInEditMode.invert()) {
                    if (AccessibilityPreferences.isAnimationReduced()) {
                        layoutAnimation = null
                    }
                }
            } finally {
                recycle()
            }
        }

        layoutManager = object : LinearLayoutManager(context, HORIZONTAL, false) {
            override fun canScrollVertically(): Boolean {
                return false
            }
        }

        setHasFixedSize(true)

        if (isInEditMode.invert()) {
            if (AccessibilityPreferences.isDividerEnabled()) {
                val divider = DividerItemDecoration(context, DividerItemDecoration.HORIZONTAL)

                divider.setDrawable(ShapeDrawable().apply {
                    intrinsicHeight = 1
                    paint.color = ThemeManager.theme.viewGroupTheme.dividerColor
                })

                addItemDecoration(divider)
            }
        }

        if (isInEditMode.invert()) {
            this.edgeEffectFactory = object : EdgeEffectFactory() {
                override fun createEdgeEffect(recyclerView: RecyclerView, direction: Int): EdgeEffect {
                    return object : EdgeEffect(recyclerView.context) {
                        override fun onPull(deltaDistance: Float) {
                            super.onPull(deltaDistance)
                            handlePull(deltaDistance)
                        }

                        override fun onPull(deltaDistance: Float, displacement: Float) {
                            super.onPull(deltaDistance, displacement)
                            handlePull(deltaDistance)
                        }

                        override fun onRelease() {
                            super.onRelease()
                            /**
                             * The finger is lifted. This is when we should start the animations to bring
                             * the view property values back to their resting states.
                             */
                            recyclerView.forEachVisibleHolder { holder: HorizontalListViewHolder ->
                                holder.rotation.start()
                                holder.translationX.start()
                            }
                        }

                        override fun onAbsorb(velocity: Int) {
                            super.onAbsorb(velocity)
                            val sign = if (direction == DIRECTION_RIGHT) -1 else 1

                            /**
                             * The list has reached the edge on fling
                             */
                            val translationVelocity = sign * velocity * flingTranslationMagnitude
                            recyclerView.forEachVisibleHolder { holder: HorizontalListViewHolder ->
                                holder.translationX
                                    .setStartVelocity(translationVelocity)
                                    .start()
                            }
                        }

                        private fun handlePull(deltaDistance: Float) {
                            /**
                             * This is called on every touch event while the list is scrolled with a finger.
                             * simply update the view properties without animation.
                             */
                            val sign = if (direction == DIRECTION_RIGHT) 1 else -1
                            val rotationDelta = sign * deltaDistance * overScrollRotationMagnitude

                            /**
                             * This value decides how fast the recycler view views should move when
                             * they're being overscrolled. Often it is determined using the area of the
                             * recycler view because its length is how far the finger can move hence
                             * the overscroll value.
                             */
                            val overscrollLengthConst = if (isLandscape) recyclerView.width / 2 else recyclerView.width
                            val translationXDelta = sign * overscrollLengthConst * deltaDistance * overScrollTranslationMagnitude

                            recyclerView.forEachVisibleHolder { holder: HorizontalListViewHolder ->
                                holder.rotation.cancel()
                                holder.translationX.cancel()
                                holder.itemView.rotation += rotationDelta
                                holder.itemView.translationX -= translationXDelta
                            }
                        }
                    }
                }
            }
        }

        interpolator = Interpolator { t ->
            var t = t
            t = abs(t - 1.0f)
            (1.0f - t.toDouble().pow(POW)).toFloat()
        }
    }

    override fun smoothScrollBy(dx: Int, dy: Int) {
        super.smoothScrollBy(dx, dy, DecelerateInterpolator(1.5F))
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        super.setAdapter(adapter)
        adapter?.stateRestorationPolicy = Adapter.StateRestorationPolicy.ALLOW
        if (isInEditMode.invert()) {
            if (!AccessibilityPreferences.isAnimationReduced()) {
                scheduleLayoutAnimation()
            }
        }
    }

    private inline fun <reified T : HorizontalListViewHolder> RecyclerView.forEachVisibleHolder(action: (T) -> Unit) {
        for (i in 0 until childCount) {
            action(getChildViewHolder(getChildAt(i)) as T)
        }
    }
}
