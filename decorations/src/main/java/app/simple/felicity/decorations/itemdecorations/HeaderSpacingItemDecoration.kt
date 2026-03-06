package app.simple.felicity.decorations.itemdecorations

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

/**
 * Adds blank space at the top of the first list item equal to the height of a floating header
 * (e.g. [app.simple.felicity.decorations.views.AppHeader]).
 *
 * This is the cleanest way to push list content below an overlaid header without touching
 * [RecyclerView.setPadding], which causes unwanted scroll side effects during item drags.
 *
 * Call [updateHeaderHeight] whenever the header height changes.
 */
class HeaderSpacingItemDecoration(headerHeight: Int = 0) : RecyclerView.ItemDecoration() {

    var headerHeight: Int = headerHeight
        set(value) {
            if (field != value) {
                field = value
                // Trigger a re-layout so the new offset is picked up immediately
                attachedRecyclerView?.invalidateItemDecorations()
            }
        }

    private var attachedRecyclerView: RecyclerView? = null

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        attachedRecyclerView = parent
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_ID.toInt()) return

        if (isInFirstRow(view, parent, position)) {
            outRect.top = headerHeight
        } else {
            outRect.top = 0
        }
    }

    /**
     * Returns true if the item at [position] is in the very first row, taking the layout
     * manager's span count into account.
     *
     * - [GridLayoutManager]: asks the SpanSizeLookup so items with span > 1 are handled correctly.
     * - [StaggeredGridLayoutManager]: every item whose column index < spanCount is in "row 0",
     *   i.e. the first [spanCount] items that haven't been pushed down by a full-span item.
     * - Everything else (linear): only position 0 is the "first row".
     */
    private fun isInFirstRow(view: View, parent: RecyclerView, position: Int): Boolean {
        return when (val lm = parent.layoutManager) {
            is GridLayoutManager -> {
                val spanCount = lm.spanCount
                val lookup = lm.spanSizeLookup
                // Accumulate span widths from 0 up to (but not including) this position.
                // If they haven't yet filled a complete row, this item is still in row 0.
                var spansUsed = 0
                for (i in 0 until position) {
                    spansUsed += lookup.getSpanSize(i)
                    if (spansUsed >= spanCount) return false  // a full row was completed before reaching position
                }
                true
            }
            is StaggeredGridLayoutManager -> {
                // In a staggered grid each column holds its own independent stream of items.
                // The first item in each column is effectively "row 0".
                // We approximate this by checking if the item's span index < spanCount and
                // there is no full-span item above it at position 0.
                val lp = view.layoutParams as? StaggeredGridLayoutManager.LayoutParams
                // Full-span item at position 0 occupies the whole first row.
                if (lp?.isFullSpan == true) return position == 0
                // Otherwise the first spanCount non-full-span items are in row 0.
                position < lm.spanCount
            }
            else -> position == 0
        }
    }

    fun updateHeaderHeight(height: Int) {
        headerHeight = height
    }

    fun detach() {
        attachedRecyclerView?.removeItemDecoration(this)
        attachedRecyclerView = null
    }
}

