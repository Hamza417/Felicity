package app.simple.felicity.decorations.utils

import android.view.ViewTreeObserver
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.decorations.itemdecorations.DividerItemDecoration

object RecyclerViewUtils {
    var bouncyValue = app.simple.felicity.preferences.BehaviourPreferences.getDampingRatio()
    var stiffnessValue = app.simple.felicity.preferences.BehaviourPreferences.getStiffness()

    const val TYPE_HEADER = 0
    const val TYPE_ITEM = 1
    const val TYPE_DIVIDER = 2
    const val TYPE_ALBUMS = 3
    const val TYPE_ARTISTS = 4
    const val TYPE_GENRES = 5

    private const val value = 1.0f
    const val flingTranslationMagnitude = value
    const val overScrollRotationMagnitude = value
    const val overScrollTranslationMagnitude = value

    fun RecyclerView.clearDecorations() {
        if (itemDecorationCount > 0) {
            for (i in itemDecorationCount - 1 downTo 0) {
                removeItemDecorationAt(i)
            }
        }
    }

    fun RecyclerView.clearDividerDecorations() {
        if (itemDecorationCount > 0) {
            for (i in itemDecorationCount - 1 downTo 0) {
                if (getItemDecorationAt(i) is DividerItemDecoration) {
                    removeItemDecorationAt(i)
                }
            }
        }
    }

    fun RecyclerView.addItemDecorationSafely(itemDecoration: RecyclerView.ItemDecoration) {
        for (i in 0 until itemDecorationCount) {
            if (getItemDecorationAt(i) == itemDecoration) {
                return
            }
        }

        addItemDecoration(itemDecoration)
    }

    /**
     * Iterate over all view holders in the RecyclerView and perform the given action on each.
     * @param action The action to perform on each view holder.
     * @param T The type of the view holder to perform the action on.
     * @see RecyclerView.ViewHolder
     * @see RecyclerView.getChildViewHolder
     * @see RecyclerView.getChildAt
     * @see RecyclerView.getChildCount
     */
    inline fun <reified T : RecyclerView.ViewHolder> RecyclerView.forEachViewHolder(action: (T) -> Unit) {
        for (i in 0 until childCount) {
            val holder = getChildViewHolder(getChildAt(i))
            if (holder is T) {
                action(holder)
            }
        }
    }

    /**
     * Iterate over all view holders in the RecyclerView and perform the given action on each.
     * @param action The action to perform on each view holder.
     * @param count The current count of the view holder.
     * @param T The type of the view holder to perform the action on.
     * @see RecyclerView.ViewHolder
     * @see RecyclerView.getChildViewHolder
     * @see RecyclerView.getChildAt
     * @see RecyclerView.getChildCount
     */
    inline fun <reified T : RecyclerView.ViewHolder> RecyclerView.forEachViewHolderIndexed(action: (T, Int) -> Unit) {
        for (i in 0 until childCount) {
            val holder = getChildViewHolder(getChildAt(i))
            if (holder is T) {
                action(holder, i)
            }
        }
    }

    /**
     * Get a random view holder from the RecyclerView.
     * @param T The type of the view holder to get.
     * @param action The action to perform on the view holder.
     * @return A random view holder from the RecyclerView.
     * @see RecyclerView.ViewHolder
     * @see RecyclerView.getChildViewHolder
     * @see RecyclerView.getChildAt
     * @see RecyclerView.getChildCount
     */
    inline fun <reified T : RecyclerView.ViewHolder> RecyclerView.randomViewHolder(action: (T) -> Unit) {
        val randomIndex = (0 until childCount).random()
        val holder = getChildViewHolder(getChildAt(randomIndex))
        if (holder is T) {
            action(holder)
        }
    }

    inline fun <reified T : RecyclerView.ViewHolder> RecyclerView.findFirstHolder(action: (T) -> Unit) {
        for (i in 0 until childCount) {
            val holder = getChildViewHolder(getChildAt(i))
            if (holder is T) {
                action(holder)
                break
            }
        }
    }

    /**
     * Universally prevents LayoutAnimation and ItemAnimator collisions on initial load.
     * * @param customAnimator The animator to attach after the first load.
     * If null, it just restores whatever was already there (like DefaultItemAnimator).
     */
    fun RecyclerView.withDelayedAnimator(customAnimator: RecyclerView.ItemAnimator? = null) {
        // Determine which animator we want to eventually use
        val targetAnimator = customAnimator ?: this.itemAnimator

        // Strip it off so the initial XML layout animation can run cleanly
        this.itemAnimator = null

        // Listen for the exact moment the view tree finishes its first layout draw
        this.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // Unregister immediately so this only happens exactly once per screen load
                this@withDelayedAnimator.viewTreeObserver.removeOnGlobalLayoutListener(this)

                // Post back to the main thread queue to safely snap the animator back on
                // after the UI is completely settled.
                this@withDelayedAnimator.post {
                    this@withDelayedAnimator.itemAnimator = targetAnimator
                }
            }
        })
    }
}
