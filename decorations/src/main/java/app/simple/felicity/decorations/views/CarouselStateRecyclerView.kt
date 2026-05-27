package app.simple.felicity.decorations.views

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import app.simple.felicity.decorations.overscroll.CustomHorizontalRecyclerView
import app.simple.felicity.decorations.singletons.CarouselScrollStateStore

/**
 * A drop-in replacement for [CustomHorizontalRecyclerView] that also keeps track of
 * where the user has scrolled. When the fragment is recreated the list will jump
 * back to exactly the same spot instead of starting from the beginning.
 *
 * Just call [setUniqueKey] with a stable string that identifies this carousel
 * (e.g. the section title) before attaching an adapter, and everything else is
 * handled automatically.
 *
 * @author Hamza417
 */
class CarouselStateRecyclerView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : CustomHorizontalRecyclerView(context, attrs, defStyleAttr) {

    private var uniqueKey: String? = null

    /**
     * Watches for the first batch of items to arrive from the adapter so we
     * know it's safe to scroll to the saved position. We unregister right after
     * the first restore to avoid doing it on every future data change.
     */
    private val restoreObserver = object : AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            restoreScrollState()
            adapter?.unregisterAdapterDataObserver(this)
        }

        override fun onChanged() {
            restoreScrollState()
            adapter?.unregisterAdapterDataObserver(this)
        }
    }

    /**
     * Ties this carousel to a stable identifier so the scroll store can tell
     * instances apart. Call this before [setAdapter] so the key is ready when
     * state restoration is triggered.
     */
    fun setUniqueKey(key: String) {
        uniqueKey = key
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        // Unregister any leftover observer from the previous adapter first.
        this.adapter?.unregisterAdapterDataObserver(restoreObserver)
        super.setAdapter(adapter)
        if (adapter != null) {
            // If the adapter already has items (data was loaded before setAdapter was called),
            // the observer will never fire, so we restore immediately via post to let the
            // layout pass finish first. Otherwise, we register the observer and wait for data.
            if (adapter.itemCount > 0) {
                post { restoreScrollState() }
            } else {
                adapter.registerAdapterDataObserver(restoreObserver)
            }
        }
    }

    override fun onDetachedFromWindow() {
        saveScrollState()
        super.onDetachedFromWindow()
    }

    override fun onScrolled(dx: Int, dy: Int) {
        super.onScrolled(dx, dy)
        // Save on every scroll so the latest position survives even if the view
        // is destroyed without a normal detach lifecycle call.
        saveScrollState()
    }

    private fun saveScrollState() {
        val key = uniqueKey ?: return
        val lm = layoutManager as? LinearLayoutManager ?: return
        lm.onSaveInstanceState()?.let { state ->
            CarouselScrollStateStore.saveState(key, state)
            Log.d("CarouselStateRecyclerView", "Saved scroll state for key '$key'")
        }
    }

    private fun restoreScrollState() {
        val key = uniqueKey ?: return
        val lm = layoutManager as? LinearLayoutManager ?: return
        Log.d("CarouselStateRecyclerView", "Attempting to restore scroll state for key '$key'")
        CarouselScrollStateStore.getState(key)?.let { state ->
            lm.onRestoreInstanceState(state)
            Log.d("CarouselStateRecyclerView", "Restored scroll state for key '$key', $state")
            return
        }

        Log.d("CarouselStateRecyclerView", "No scroll state found for key '$key'")
    }
}

