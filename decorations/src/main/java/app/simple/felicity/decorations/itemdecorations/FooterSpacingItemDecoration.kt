package app.simple.felicity.decorations.itemdecorations

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

/**
 * Adds blank space at the bottom of the last list item equal to the height of a floating footer
 * (e.g. [app.simple.felicity.decorations.miniplayer.MiniPlayer]).
 *
 * This is the cleanest way to push list content above an overlaid footer without touching
 * [RecyclerView.setPadding], which causes unwanted scroll side effects during item drags.
 *
 * Call [updateFooterHeight] whenever the footer height changes (e.g. on size or margin change).
 *
 * @author Hamza417
 */
class FooterSpacingItemDecoration(footerHeight: Int = 0) : RecyclerView.ItemDecoration() {

    /**
     * Height of the footer in pixels. Setting a new value automatically triggers a decoration
     * invalidation on the attached [RecyclerView] so the new offset is picked up immediately.
     */
    var footerHeight: Int = footerHeight
        set(value) {
            if (field != value) {
                field = value
                attachedRecyclerView?.invalidateItemDecorations()
            }
        }

    private var attachedRecyclerView: RecyclerView? = null

    override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
    ) {
        attachedRecyclerView = parent
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) {
            outRect.bottom = 0
            return
        }

        val itemCount = state.itemCount
        if (itemCount <= 0) {
            outRect.bottom = 0
            return
        }

        if (isInLastRow(view, parent, position, itemCount)) {
            outRect.bottom = footerHeight
        } else {
            outRect.bottom = 0
        }
    }

    /**
     * Determines whether the item at [position] belongs to the last row of the list,
     * taking the layout manager's span configuration into account.
     *
     * For [GridLayoutManager] the last row is computed by accumulating span widths
     * from index 0 and tracking the start index of the row currently being filled.
     * For [StaggeredGridLayoutManager] the last [spanCount] positions are treated as
     * the last row (a safe approximation since column assignments are dynamic).
     * For every other layout manager only the final position counts.
     *
     * @param view      the child view being decorated
     * @param parent    the [RecyclerView] that owns the decoration
     * @param position  adapter position of [view]
     * @param itemCount total item count reported by [RecyclerView.State]
     * @return `true` if [position] is in the last visible row
     * @author Hamza417
     */
    private fun isInLastRow(
            view: View,
            parent: RecyclerView,
            position: Int,
            itemCount: Int
    ): Boolean {
        if (position < 0 || itemCount <= 0) return false
        return when (val lm = parent.layoutManager) {
            is GridLayoutManager -> position >= computeLastRowStart(lm, itemCount)
            is StaggeredGridLayoutManager -> position >= itemCount - lm.spanCount
            else -> position == itemCount - 1
        }
    }

    /**
     * Computes the adapter position that starts the last row in a [GridLayoutManager].
     *
     * Items are iterated from 0 to [itemCount] - 1. Every time adding an item's span
     * would overflow the grid's [GridLayoutManager.spanCount], a new row begins at that
     * position — making it the new candidate for the last row start.
     *
     * @param lm        the [GridLayoutManager] whose span configuration is used
     * @param itemCount total number of adapter items
     * @return the position of the first item in the last row
     * @author Hamza417
     */
    private fun computeLastRowStart(lm: GridLayoutManager, itemCount: Int): Int {
        if (itemCount == 0) return 0
        val spanCount = lm.spanCount
        val lookup = lm.spanSizeLookup
        var spansInRow = 0
        var rowStart = 0
        for (i in 0 until itemCount) {
            val s = lookup.getSpanSize(i)
            if (spansInRow + s > spanCount) {
                rowStart = i
                spansInRow = s
            } else {
                spansInRow += s
            }
        }
        return rowStart
    }

    /** Update the footer height and trigger a decoration refresh. */
    fun updateFooterHeight(height: Int) {
        footerHeight = height
    }

    /**
     * Removes this decoration from the attached [RecyclerView] and clears the reference.
     * Safe to call multiple times.
     */
    fun detach() {
        attachedRecyclerView?.removeItemDecoration(this)
        attachedRecyclerView = null
    }
}

