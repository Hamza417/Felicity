package app.simple.felicity.decorations.itemdecorations

import android.graphics.Rect
import android.view.View
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
        val position = parent.getChildAdapterPosition(view)

        outRect.left = if (leftEdge) horizontalSpacing else 0
        outRect.right = if (rightEdge) horizontalSpacing else 0
        outRect.top = if (topEdge && position == 0) verticalSpacing else 0
        outRect.bottom = if (bottomEdge) verticalSpacing else 0
    }
}
