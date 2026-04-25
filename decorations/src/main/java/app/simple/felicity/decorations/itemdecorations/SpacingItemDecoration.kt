package app.simple.felicity.decorations.itemdecorations

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Adds even spacing around every item in a [RecyclerView], whether it's a simple list
 * or a multi-column grid. Think of it as putting equal-sized cushions between all your items.
 *
 * For grids it respects variable-span items (items that take up more than one column) by
 * asking the layout manager's [GridLayoutManager.SpanSizeLookup] for the real column index
 * and span size — rather than guessing from the raw adapter position.
 *
 * @param horizontalSpacing Horizontal gap in pixels. In a grid this is the gap between columns
 *                          (and half of it becomes the outer edge padding on each side).
 * @param verticalSpacing   Vertical gap in pixels. This becomes the gap between rows and,
 *                          optionally, the top/bottom edge padding.
 * @param leftEdge          Whether to add spacing on the very left edge of the list.
 * @param rightEdge         Whether to add spacing on the very right edge of the list.
 * @param topEdge           Whether to add spacing above the first row of items.
 * @param bottomEdge        Whether to add spacing below the last item.
 *
 * @author Hamza417
 */
class SpacingItemDecoration(
        private val horizontalSpacing: Int,
        private val verticalSpacing: Int,
        private val leftEdge: Boolean = true,
        private val rightEdge: Boolean = true,
        private val topEdge: Boolean = true,
        private val bottomEdge: Boolean = true
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val layoutManager = parent.layoutManager

        /**
         * When items are being animated (e.g. after a sort order change), the adapter
         * position of the view may not be known yet. In that case we fall back to the
         * layout position, which is the last position the view was laid out at.
         * If even that comes back as NO_POSITION (the view is in a completely
         * indeterminate transitional state), we skip all offsets so we don't accidentally
         * apply wrong spacing that persists beyond the animation.
         */
        val adapterPos = parent.getChildAdapterPosition(view)
        val position = if (adapterPos != RecyclerView.NO_POSITION) {
            adapterPos
        } else {
            parent.getChildLayoutPosition(view)
        }

        if (position == RecyclerView.NO_POSITION) {
            return
        }

        if (layoutManager is GridLayoutManager) {
            val spanCount = layoutManager.spanCount
            val lookup = layoutManager.spanSizeLookup

            /**
             * Ask the layout manager which column this item actually starts in.
             * This is important for variable-span grids where some items take up
             * more than one column — raw "position % spanCount" would give the wrong
             * answer for any item that comes after a wide item.
             */
            val spanIndex = lookup.getSpanIndex(position, spanCount)
            val spanSize = lookup.getSpanSize(position)

            /**
             * The "span group index" is just a fancy name for the row number.
             * We use it to decide whether to add top-edge padding — only the very first
             * row of items should get that extra breathing room at the top.
             */
            val spanGroupIndex = lookup.getSpanGroupIndex(position, spanCount)

            if (spanCount > 1) {
                val halfHorizontal = horizontalSpacing / 2
                val halfVertical = verticalSpacing / 2

                // Left offset: proportional to how far right the item's column start is.
                outRect.left = halfHorizontal - spanIndex * halfHorizontal / spanCount
                // Right offset: proportional to how much column space the item uses up.
                outRect.right = (spanIndex + spanSize) * halfHorizontal / spanCount

                outRect.top = if (topEdge && spanGroupIndex == 0) halfVertical else 0
                outRect.bottom = halfVertical
            } else {
                // Single-column grid — treat it like a linear list.
                outRect.left = horizontalSpacing - spanIndex * horizontalSpacing / spanCount
                outRect.right = (spanIndex + spanSize) * horizontalSpacing / spanCount

                outRect.top = if (topEdge && spanGroupIndex == 0) verticalSpacing else 0
                outRect.bottom = verticalSpacing
            }
        } else {
            // Fallback for LinearLayoutManager and other non-grid layout managers.
            outRect.left = if (leftEdge) horizontalSpacing else 0
            outRect.right = if (rightEdge) horizontalSpacing else 0
            outRect.top = if (topEdge && position == 0) verticalSpacing else 0
            outRect.bottom = if (bottomEdge) verticalSpacing else 0
        }
    }
}