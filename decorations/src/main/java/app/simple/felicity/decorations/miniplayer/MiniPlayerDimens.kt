package app.simple.felicity.decorations.miniplayer

import android.util.TypedValue
import android.view.View

/**
 * Convenience dimension conversion extensions scoped to [View].
 *
 * These replace the private `dp()` / `sp()` helpers that were previously inlined
 * inside [MiniPlayer], making them reusable across all classes in this package
 * without pulling in a full utility object.
 */

/** Converts a dp value to pixels using this view's display metrics. */
internal fun View.dp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

/** Converts an sp value to pixels using this view's display metrics. */
internal fun View.sp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

