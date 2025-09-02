package app.simple.felicity.decorations.utils

import android.graphics.drawable.Drawable
import android.view.View
import app.simple.felicity.decorations.drawables.ShimmerDrawable
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.theme.managers.ThemeManager

object ViewUtils {
    private const val SKELETON_TAG_KEY = -158743
    private const val SKELETON_PREV_BG_KEY = -158744

    fun View.setSkeletonBackground(
            enable: Boolean = true,
            baseColor: Int = ThemeManager.theme.viewGroupTheme.highlightColor,
            highlightColor: Int = ThemeManager.theme.viewGroupTheme.backgroundColor,
            shimmerWidthFraction: Float = 0.6f,
            duration: Long = 1000L
    ) {
        if (!enable) {
            clearSkeletonBackground()
            return
        }

        // If already applied, do nothing
        val existing = getTag(SKELETON_TAG_KEY) as? ShimmerDrawable
        if (existing != null) return

        // Save previous background to restore later
        setTag(SKELETON_PREV_BG_KEY, background)

        val shimmer = ShimmerDrawable(baseColor = baseColor,
                                      highlightColor = highlightColor,
                                      shimmerWidthFraction = shimmerWidthFraction,
                                      duration = duration,
                                      cornerRadius = AppearancePreferences.getCornerRadius())
        setTag(SKELETON_TAG_KEY, shimmer)
        background = shimmer
        shimmer.start()
    }

    fun View.clearSkeletonBackground() {
        val shimmer = getTag(SKELETON_TAG_KEY) as? ShimmerDrawable
        if (shimmer != null) {
            shimmer.stop()
            // restore previous background if any
            val prev = getTag(SKELETON_PREV_BG_KEY) as? Drawable
            background = prev
            setTag(SKELETON_TAG_KEY, null)
            setTag(SKELETON_PREV_BG_KEY, null)
        }
    }
}
