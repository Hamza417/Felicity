package app.simple.felicity.decorations.overscroll

import android.view.View
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView

object RecyclerViewUtils {
    private fun RecyclerView.smoothScrollToCenteredPosition(position: Int) {
        val smoothScroller = object : LinearSmoothScroller(context) {
            override fun calculateDxToMakeVisible(view: View?, snapPreference: Int): Int {
                val dxToStart = super.calculateDxToMakeVisible(view, SNAP_TO_START)
                val dxToEnd = super.calculateDxToMakeVisible(view, SNAP_TO_END)

                return (dxToStart + dxToEnd) / 2
            }
        }

        smoothScroller.targetPosition = position
        layoutManager?.startSmoothScroll(smoothScroller)
    }
}