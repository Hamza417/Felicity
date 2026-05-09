package app.simple.felicity.decorations.highlight

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import app.simple.felicity.decorations.highlight.HighlightViewDelegate.Companion.animateColor
import app.simple.felicity.decorations.ripple.FelicityRippleDrawable
import app.simple.felicity.preferences.AccessibilityPreferences
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.theme.managers.ThemeManager
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * A helper that holds all the shared state and logic used by the highlight view family
 * ([HighlightTextView], [HighlightImageButton], [HighlightIcon]). Instead of copy-pasting
 * the same color resolution, background building, and touch animation code into every class,
 * each view creates one of these and delegates the heavy lifting here.
 *
 * @param strokeWidthPx the stroke thickness already converted to pixels by the owning view.
 * @author Hamza417
 */
class HighlightViewDelegate(var strokeWidthPx: Float = 0f) {

    /** Which visual style the highlight should use — flat fill, outline, or both. */
    var highlightMode: Int = MODE_FLAT

    /**
     * When true, a custom color was pinned by the owner and theme changes should not
     * override the fill or stroke color.
     */
    var useCustomColor: Boolean = false

    /**
     * When true, the active accent color drives the background. Text and drawable tints
     * should also be forced to white so they stay readable on the colored surface.
     * This is specific to views that show text, but keeping it here avoids a separate flag.
     */
    var useAccentColor: Boolean = false

    @ColorInt
    var customColor: Int = Color.TRANSPARENT

    /**
     * Figures out what color should fill the pill background, respecting the priority order:
     * accent mode first (most prominent), then a pinned custom color, then the regular
     * theme highlight.
     */
    @ColorInt
    fun resolveFillColor(): Int {
        if (useAccentColor) return ThemeManager.accent.primaryAccentColor
        return if (useCustomColor) customColor
        else ThemeManager.theme.viewGroupTheme.highlightColor
    }

    /**
     * Same priority as [resolveFillColor] but for the stroke — accent first,
     * then custom color, then the active accent from the theme.
     */
    @ColorInt
    fun resolveStrokeColor(): Int {
        if (useAccentColor) return ThemeManager.accent.primaryAccentColor
        return if (useCustomColor) customColor
        else ThemeManager.accent.primaryAccentColor
    }

    /**
     * Builds a fresh pill-shaped [MaterialShapeDrawable] based on the current [highlightMode].
     * The caller is responsible for setting it as the view's background.
     */
    fun buildBackground(cornerRadius: Float): MaterialShapeDrawable {
        val background = MaterialShapeDrawable(
                ShapeAppearanceModel().toBuilder()
                    .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
                    .build()
        )
        val fillColor = resolveFillColor()
        val strokeColor = resolveStrokeColor()

        when (highlightMode) {
            MODE_OUTLINE -> {
                background.fillColor = ColorStateList.valueOf(Color.TRANSPARENT)
                background.setStroke(strokeWidthPx, strokeColor)
            }
            MODE_BOTH -> {
                background.fillColor = ColorStateList.valueOf(fillColor)
                background.setStroke(strokeWidthPx, strokeColor)
            }
            else -> {
                // MODE_FLAT: a clean filled pill with no border.
                background.fillColor = ColorStateList.valueOf(fillColor)
            }
        }
        return background
    }

    /**
     * Builds a [FelicityRippleDrawable] foreground that matches the current background so
     * the ripple always looks like it belongs to the same surface.
     */
    fun buildRipple(cornerRadius: Float): FelicityRippleDrawable {
        val rippleColor = if (useCustomColor) customColor
        else ThemeManager.accent.primaryAccentColor

        val ripple = FelicityRippleDrawable(rippleColor)
        ripple.setCornerRadius(cornerRadius)
        // Outline buttons have no fill, so the ripple should also start transparent.
        ripple.setStartColor(
                if (highlightMode == MODE_OUTLINE) Color.TRANSPARENT else resolveFillColor()
        )
        return ripple
    }

    /**
     * Updates the internal state for a custom color pin, or clears it if [color] is
     * [Color.TRANSPARENT]. Call this and then rebuild the background/ripple.
     */
    fun applyCustomColor(@ColorInt color: Int) {
        if (color == Color.TRANSPARENT) {
            useCustomColor = false
            customColor = Color.TRANSPARENT
        } else {
            useCustomColor = true
            customColor = color
        }
    }

    /**
     * Returns the global corner radius that all pill shapes should use so everything
     * stays consistent with the user's preference.
     */
    fun getCornerRadius(): Float = AppearancePreferences.getCornerRadius()

    companion object {
        /** A clean filled pill — the default look. */
        const val MODE_FLAT = 0

        /** Stroke border only, no background fill — great for a subtle, ghost-button style. */
        const val MODE_OUTLINE = 1

        /** Both a fill and a stroke, for when you really want the button to stand out. */
        const val MODE_BOTH = 2

        /** How long color transitions take, in milliseconds. */
        const val COLOR_ANIM_DURATION = 400L

        /**
         * A simple callback used by [animateColor] so that Java callers can pass a plain
         * lambda without worrying about Kotlin's Unit return type.
         */
        fun interface ColorCallback {
            fun onColor(color: Int)
        }

        /**
         * Starts a smooth color transition between [from] and [to], calling [onUpdate]
         * on every frame so the caller can apply the new color wherever it needs to go.
         *
         * If both [from] and [to] are [Color.TRANSPARENT] the call is skipped entirely —
         * there is nothing visible to animate in that case (e.g. outline mode fill).
         */
        @JvmStatic
        fun animateColor(
                @ColorInt from: Int,
                @ColorInt to: Int,
                duration: Long = COLOR_ANIM_DURATION,
                onUpdate: ColorCallback
        ) {
            // No point running an animator when both ends are invisible.
            if (from == Color.TRANSPARENT && to == Color.TRANSPARENT) return
            val anim = ValueAnimator.ofObject(ArgbEvaluator(), from, to)
            anim.duration = duration
            anim.interpolator = DecelerateInterpolator(1.5f)
            anim.addUpdateListener { onUpdate.onColor(it.animatedValue as Int) }
            anim.start()
        }

        /**
         * Shrinks [view] slightly so it feels like it's being physically pressed.
         * Only runs when the accessibility highlight mode is turned on.
         */
        @JvmStatic
        fun animateTouchDown(view: View, animDuration: Long) {
            if (AccessibilityPreferences.isHighlightMode() && view.isClickable) {
                view.animate()
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .alpha(0.7f)
                    .setInterpolator(LinearOutSlowInInterpolator())
                    .setDuration(animDuration)
                    .start()
            }
        }

        /**
         * Bounces [view] back to its normal size and opacity after a press.
         */
        @JvmStatic
        fun animateTouchUp(view: View, animDuration: Long) {
            if (AccessibilityPreferences.isHighlightMode() && view.isClickable) {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setStartDelay(50)
                    .setInterpolator(LinearOutSlowInInterpolator())
                    .setDuration(animDuration)
                    .start()
            }
        }
    }
}

