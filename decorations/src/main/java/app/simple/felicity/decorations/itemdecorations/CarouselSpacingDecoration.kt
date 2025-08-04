@file:Suppress("KDocUnresolvedReference")

package app.simple.felicity.decorations.itemdecorations

import androidx.annotation.Px
import androidx.recyclerview.widget.RecyclerView

/** Works best with a [LinearLayoutManager] in [LinearLayoutManager.HORIZONTAL] orientation */
class CarouselSpacingDecoration(
        @Px private val centerSpacing: Int = 0,
        @Px private val neighborOverlap: Int = 0,
        @Px private val sideOverlap: Int = 0
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: android.graphics.Rect, view: android.view.View, parent: RecyclerView, state: RecyclerView.State) {
        val itemCount = state.itemCount
        val itemPosition = parent.getChildAdapterPosition(view)
        val centerPosition = itemCount / 2

        when {
            itemPosition == centerPosition -> {
                // Center item: more space on both sides
                outRect.left = centerSpacing / 2
                outRect.right = centerSpacing / 2
            }
            itemPosition == centerPosition - 1 || itemPosition == centerPosition + 1 -> {
                // Neighbors: moderate overlap
                outRect.left = neighborOverlap / 2
                outRect.right = neighborOverlap / 2
            }
            itemPosition == 0 -> {
                outRect.left = 0
                outRect.right = sideOverlap / 2
            }
            itemPosition == itemCount - 1 -> {
                outRect.left = sideOverlap / 2
                outRect.right = 0
            }
            else -> {
                // All other items: more overlap
                outRect.left = sideOverlap / 2
                outRect.right = sideOverlap / 2
            }
        }
    }
}