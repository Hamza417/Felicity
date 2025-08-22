package app.simple.felicity.decorations.itemdecorations

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class SpacingItemDecoration(
        private val spacing: Int,
        private val leftEdge: Boolean = true,
        private val rightEdge: Boolean = true,
        private val topEdge: Boolean = true,
        private val bottomEdge: Boolean = true
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)

        outRect.left = if (leftEdge) spacing else 0
        outRect.right = if (rightEdge) spacing else 0
        outRect.top = if (topEdge && position == 0) spacing else 0
        outRect.bottom = if (bottomEdge) spacing else 0
    }
}