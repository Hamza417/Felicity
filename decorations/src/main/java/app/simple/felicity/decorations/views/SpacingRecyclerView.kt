package app.simple.felicity.decorations.views

import android.content.SharedPreferences
import android.view.animation.DecelerateInterpolator
import androidx.transition.ChangeBounds
import androidx.transition.ChangeTransform
import androidx.transition.Fade
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import app.simple.felicity.decorations.itemdecorations.SpacingItemDecoration
import app.simple.felicity.decorations.overscroll.CustomVerticalRecyclerView
import app.simple.felicity.preferences.AppearancePreferences

class SpacingRecyclerView : CustomVerticalRecyclerView {

    constructor(context: android.content.Context) : super(context)
    constructor(context: android.content.Context, attrs: android.util.AttributeSet?) : super(context, attrs)
    constructor(context: android.content.Context, attrs: android.util.AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        updateSpacing()
    }

    fun updateSpacing() {
        for (i in 0 until itemDecorationCount) {
            val decoration = getItemDecorationAt(i)
            if (decoration is SpacingItemDecoration) {
                removeItemDecoration(decoration)
                break
            }
        }

        addItemDecoration(
                SpacingItemDecoration(
                        AppearancePreferences.DEFAULT_SPACING.toInt(),
                        AppearancePreferences.getListSpacing().toInt()))
    }

    fun beginDelayedTransition() {
        val transition = TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER

            // Animate layout bounds (size/position changes)
            addTransition(ChangeBounds().apply {
                duration = 400
                interpolator = DecelerateInterpolator(1.5F)
            })

            // Animate size of children (width/height)
            addTransition(ChangeTransform().apply {
                duration = 400
                interpolator = DecelerateInterpolator(1.5F)
            })

            // Optionally animate appearing/disappearing items
            addTransition(Fade(Fade.IN).apply {
                duration = 250
            })
            addTransition(Fade(Fade.OUT).apply {
                duration = 250
            })
        }

        TransitionManager.beginDelayedTransition(this, transition)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            AppearancePreferences.LIST_SPACING -> {
                updateSpacing()
            }
        }
    }
}