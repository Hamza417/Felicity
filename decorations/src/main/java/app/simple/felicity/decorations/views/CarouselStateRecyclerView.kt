package app.simple.felicity.decorations.views

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import app.simple.felicity.decorations.overscroll.CustomHorizontalRecyclerView
import app.simple.felicity.decorations.singletons.CarouselScrollStateStore

class CarouselStateRecyclerView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : CustomHorizontalRecyclerView(context, attrs, defStyleAttr) {

    private var uniqueKey: String? = null
    private var isStateRestored = false

    /**
     * Watches for data changes to restore state once data is populated.
     * Overrides multiple methods to support ListAdapter/DiffUtil updates.
     */
    private val restoreObserver = object : AdapterDataObserver() {
        override fun onChanged() {
            attemptRestore()
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            attemptRestore()
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            attemptRestore()
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            attemptRestore()
        }
    }

    /**
     * Ties this carousel to a stable identifier.
     * Now properly triggers a save of the old state and a restore of the new one.
     */
    fun setUniqueKey(key: String) {
        if (this.uniqueKey == key) return // Prevent unnecessary work if the key hasn't changed

        // 1. Save the state of the OUTGOING carousel before swapping keys
        if (this.uniqueKey != null) {
            saveScrollState()
        }

        // 2. Set the new key and reset the restoration flag
        this.uniqueKey = key
        this.isStateRestored = false

        // 3. Attempt to restore immediately (if data is already present)
        attemptRestore()
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        try {
            this.adapter?.unregisterAdapterDataObserver(restoreObserver)
        } catch (_: IllegalStateException) {
            // Ignored
        }

        super.setAdapter(adapter)

        // Modern RecyclerViews natively prevent state restoration when empty.
        // This acts as a great safety net alongside our manual dictionary.
        adapter?.stateRestorationPolicy = Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

        adapter?.registerAdapterDataObserver(restoreObserver)
        attemptRestore()
    }

    override fun onDetachedFromWindow() {
        saveScrollState()
        super.onDetachedFromWindow()
    }

    // REMOVED onScrolled() override to prevent severe performance penalties.

    /**
     * Expose this publicly so the Parent Adapter can call it in onViewRecycled()
     */
    fun saveScrollState() {
        val key = uniqueKey ?: return
        val lm = layoutManager as? LinearLayoutManager ?: return

        lm.onSaveInstanceState()?.let { state ->
            CarouselScrollStateStore.saveState(key, state)
            Log.d(TAG, "Saved scroll state for key '$key'")
        }
    }

    private fun attemptRestore() {
        if (isStateRestored) return // Already restored for this key
        if (adapter == null || adapter!!.itemCount == 0) return // No data to layout yet

        val key = uniqueKey ?: return
        val lm = layoutManager as? LinearLayoutManager ?: return

        CarouselScrollStateStore.getState(key)?.let { state ->
            lm.onRestoreInstanceState(state)
            Log.d(TAG, "Restored scroll state for key '$key'")
        } ?: Log.d(TAG, "No scroll state found for key '$key'")

        // Mark as restored so we don't continually reset the user's scroll position on future data updates
        isStateRestored = true
    }

    companion object {
        private const val TAG = "CarouselStateRecyclerView"
    }
}