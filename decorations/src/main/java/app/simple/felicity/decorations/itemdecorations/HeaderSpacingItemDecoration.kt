package app.simple.felicity.decorations.itemdecorations

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

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
        if (position == 0) {
            outRect.top = headerHeight
        } else {
            outRect.top = 0
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

