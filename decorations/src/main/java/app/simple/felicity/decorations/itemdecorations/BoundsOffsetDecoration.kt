package app.simple.felicity.decorations.itemdecorations

import android.graphics.Rect
import android.util.Log
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class BoundsOffsetDecoration : RecyclerView.ItemDecoration() {

    private val negativeOffset = -60
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)

        val itemPosition = parent.getChildAdapterPosition(view)
        Log.d("ArtFlow", "Item position: $itemPosition")

        // It is crucial to refer to layoutParams.width
        // (view.width is 0 at this time)!
        val itemWidth = view.layoutParams.width
        val offset = (parent.width - itemWidth) / 2

        when (itemPosition) {
            0 -> {
                outRect.left = offset
            }

            state.itemCount - 1 -> {
                outRect.right = offset
            }

            else -> {
                outRect.left = negativeOffset
                outRect.right = negativeOffset
            }
        }
    }
}
