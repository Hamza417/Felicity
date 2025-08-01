package app.simple.felicity.decorations.utils

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View

object ViewUtils {
    fun View.drawBottomToTopFadeBackground() {
        background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.DKGRAY, Color.TRANSPARENT)
        )
    }
}