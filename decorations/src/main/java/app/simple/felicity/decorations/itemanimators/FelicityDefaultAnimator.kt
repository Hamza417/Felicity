package app.simple.felicity.decorations.itemanimators

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.ViewPropertyAnimator
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

/**
 * A custom ItemAnimator that provides a subtle elastic bounce on insertions
 * and a smooth deceleration on removals. It delegates Moves and Changes 
 * to the standard DefaultItemAnimator.
 */
class FelicityDefaultAnimator : DefaultItemAnimator() {

    companion object {
        private const val TAG = "ElasticAnimator"
        private const val DURATION_ADD = 300L
        private const val DURATION_REMOVE = 500L
        private const val DECELERATE_FACTOR = 3f
        private const val SCALE_START = 0.85f
    }

    private val decelerateInterpolator = DecelerateInterpolator(DECELERATE_FACTOR)
    private val addAnimators = mutableMapOf<RecyclerView.ViewHolder, ViewPropertyAnimator>()
    private val removeAnimators = mutableMapOf<RecyclerView.ViewHolder, ViewPropertyAnimator>()

    override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
        endAnimation(holder) // Cancel any existing animations on this recycled view

        // Initial state before bouncing in
        holder.itemView.alpha = 0f
        holder.itemView.scaleX = SCALE_START
        holder.itemView.scaleY = SCALE_START

        val animator = holder.itemView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(DURATION_ADD)
            .setInterpolator(decelerateInterpolator)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    dispatchAddStarting(holder)
                }

                override fun onAnimationCancel(animation: Animator) {
                    // Restore properties immediately if canceled
                    holder.itemView.alpha = 1f
                    holder.itemView.scaleX = 1f
                    holder.itemView.scaleY = 1f
                }

                override fun onAnimationEnd(animation: Animator) {
                    animation.removeAllListeners()
                    addAnimators.remove(holder)
                    dispatchAddFinished(holder)
                }
            })

        addAnimators[holder] = animator
        animator.start()

        // Return false because we are running the animation immediately 
        // rather than waiting for the default staggered pending queue.
        return false
    }

    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        endAnimation(holder)

        val animator = holder.itemView.animate()
            .alpha(0f)
            .scaleX(SCALE_START)
            .scaleY(SCALE_START)
            .setDuration(DURATION_REMOVE)
            .setInterpolator(decelerateInterpolator)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    dispatchRemoveStarting(holder)
                }

                override fun onAnimationEnd(animation: Animator) {
                    animation.removeAllListeners()
                    // Restore view properties so it's clean when recycled
                    holder.itemView.alpha = 1f
                    holder.itemView.scaleX = 1f
                    holder.itemView.scaleY = 1f
                    removeAnimators.remove(holder)
                    dispatchRemoveFinished(holder)
                }
            })

        removeAnimators[holder] = animator
        animator.start()
        return false
    }

    // Critical Lifecycle Overrides to prevent phantom views

    override fun endAnimation(item: RecyclerView.ViewHolder) {
        super.endAnimation(item)
        addAnimators[item]?.cancel()
        removeAnimators[item]?.cancel()
    }

    override fun endAnimations() {
        super.endAnimations()
        addAnimators.values.forEach { it.cancel() }
        removeAnimators.values.forEach { it.cancel() }
    }

    override fun isRunning(): Boolean {
        return super.isRunning() || addAnimators.isNotEmpty() || removeAnimators.isNotEmpty()
    }
}