package app.simple.felicity.decorations.itemanimators

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import java.util.WeakHashMap

class FlipItemAnimator : DefaultItemAnimator() {

    private val changeDuration = 400L
    private val runningAnimators = WeakHashMap<View, AnimatorSet>()

    override fun animateChange(
            oldHolder: RecyclerView.ViewHolder,
            newHolder: RecyclerView.ViewHolder,
            preLayoutInfo: ItemHolderInfo,
            postLayoutInfo: ItemHolderInfo
    ): Boolean {
        // Cancel any running animators
        endAnimation(oldHolder)
        endAnimation(newHolder)

        // Fade out oldHolder
        val oldAlphaAnim = ObjectAnimator.ofFloat(oldHolder.itemView, "alpha", 0f)
        oldAlphaAnim.duration = changeDuration / 2
        oldAlphaAnim.interpolator = AccelerateInterpolator()

        oldAlphaAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                oldHolder.itemView.alpha = 1f
                oldHolder.itemView.rotationX = 0f
                dispatchChangeFinished(oldHolder, false)

                // Flip in newHolder
                val view = newHolder.itemView
                view.alpha = 0f
                view.rotationX = -180f

                val newAlphaAnim = ObjectAnimator.ofFloat(view, "alpha", 1f)
                val newRotateAnim = ObjectAnimator.ofFloat(view, "rotationX", 0f)
                val set = AnimatorSet()
                set.playTogether(newAlphaAnim, newRotateAnim)
                set.duration = changeDuration / 2
                set.interpolator = DecelerateInterpolator()
                set.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(anim: Animator) {
                        runningAnimators.remove(view)
                        dispatchAnimationFinished(newHolder)
                    }
                })
                runningAnimators[view] = set
                set.start()
            }

            override fun onAnimationCancel(animation: Animator) {
                oldHolder.itemView.alpha = 1f
                oldHolder.itemView.rotationX = 0f
                dispatchAnimationFinished(oldHolder)
                dispatchChangeFinished(oldHolder, true)
            }
        })

        runningAnimators[oldHolder.itemView] = AnimatorSet().apply {
            play(oldAlphaAnim)
            start()
        }

        return true
    }

    override fun endAnimation(item: RecyclerView.ViewHolder) {
        runningAnimators[item.itemView]?.cancel()
        runningAnimators.remove(item.itemView)
        item.itemView.alpha = 1f
        item.itemView.rotationX = 0f
        super.endAnimation(item)
    }
}