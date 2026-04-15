package app.simple.felicity.shared.utils

import android.content.Context
import android.util.TypedValue
import android.view.View

object UnitUtils {
    /**
     * Converts dp to pixels using the screen's display metrics. Just a handy shortcut
     * so we don't have to type out the full conversion formula everywhere.
     */
    fun Context.dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

    fun View.dpToPx(dp: Float): Float = context.dpToPx(dp)
}