package app.simple.felicity.decorations.itemdecorations

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

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
        val position = parent.getChildAdapterPosition(view)
        if (layoutManager is GridLayoutManager) {
            val spanCount = layoutManager.spanCount
            val column = position % spanCount
            if (spanCount > 1) {
                val halfHorizontal = horizontalSpacing / 2
                val halfVertical = verticalSpacing / 2
                outRect.left = halfHorizontal - column * halfHorizontal / spanCount
                outRect.right = (column + 1) * halfHorizontal / spanCount
                if (topEdge && position < spanCount) {
                    outRect.top = halfVertical
                } else {
                    outRect.top = 0
                }
                outRect.bottom = halfVertical
            } else {
                outRect.left = horizontalSpacing - column * horizontalSpacing / spanCount
                outRect.right = (column + 1) * horizontalSpacing / spanCount
                if (topEdge && position < spanCount) {
                    outRect.top = verticalSpacing
                } else {
                    outRect.top = 0
                }
                outRect.bottom = verticalSpacing
            }
        } else {
            // fallback for linear layout
            outRect.left = if (leftEdge) horizontalSpacing else 0
            outRect.right = if (rightEdge) horizontalSpacing else 0
            outRect.top = if (topEdge && position == 0) verticalSpacing else 0
            outRect.bottom = if (bottomEdge) verticalSpacing else 0
        }
    }
}