package app.simple.felicity.decorations.knobs

import android.graphics.drawable.Drawable

/**
 * Base class for all rotary knob drawables used by [RotaryKnobView].
 *
 * Subclasses must implement [onPressedStateChanged] to visually transition between
 * the idle (not-touched) and pressed (touched) states, and [getCurrentStateColor] to
 * expose the animated tint so [RotaryKnobView] can keep arc and tick marks in sync
 * without casting to a concrete subtype.
 *
 * Subclasses that need theme-aware colors should override [onAttachedToKnobView] to
 * register with [app.simple.felicity.theme.managers.ThemeManager] and apply the current
 * theme, and override [onDetachedFromKnobView] to unregister.
 *
 * [RotaryKnobView] calls these lifecycle methods at the correct times and also sets
 * [Drawable.Callback] so that [invalidateSelf] correctly propagates to the host view.
 */
abstract class RotaryKnobDrawable : Drawable() {

    /**
     * Called by [RotaryKnobView] when the touch state changes.
     *
     * @param pressed `true` when the knob is being touched, `false` when released.
     * @param animationDuration duration in milliseconds for the visual transition.
     */
    abstract fun onPressedStateChanged(pressed: Boolean, animationDuration: Int = DEFAULT_TRANSITION_DURATION)

    /**
     * Returns the current animated color representing the knob state (idle vs pressed).
     * [RotaryKnobView] uses this to keep static arc and tick marks visually in sync with
     * the rotating knob without requiring a drawable-type cast.
     */
    abstract fun getCurrentStateColor(): Int

    /**
     * Called by [RotaryKnobView] immediately after the host view is attached to a window
     * (or when this drawable is swapped in while the view is already attached).
     *
     * Implementations should register with
     * [app.simple.felicity.theme.managers.ThemeManager] here and apply the current theme
     * immediately so the drawable is correctly colored before the first draw.
     */
    open fun onAttachedToKnobView() {}

    /**
     * Called by [RotaryKnobView] just before the host view is detached from its window, or
     * just before this drawable is replaced by another drawable.
     *
     * Implementations should unregister from
     * [app.simple.felicity.theme.managers.ThemeManager] here to avoid memory leaks.
     */
    open fun onDetachedFromKnobView() {}

    /**
     * Called by [RotaryKnobView] when the knob position is changed programmatically via
     * [RotaryKnobView.setKnobPosition], as opposed to a direct user touch.
     *
     * Implementations should animate a brief glow-pulse on the indicator dot only —
     * the ring and arc elements should remain at their idle state so the user can
     * distinguish a programmatic update from a manual interaction.
     *
     * The default implementation is a no-op; override in concrete subclasses.
     */
    open fun onProgrammaticPositionChanged() {}

    /**
     * Returns `true` if this drawable uses [android.graphics.Paint.setShadowLayer] for glow
     * or bloom effects, which requires the host view to operate in software rendering mode
     * ([android.view.View.LAYER_TYPE_SOFTWARE]).
     *
     * [RotaryKnobView] calls this after every drawable swap and adjusts its layer type
     * accordingly. Override and return `true` in any subclass that relies on
     * [android.graphics.Paint.setShadowLayer].
     */
    open fun requiresSoftwareLayer(): Boolean = false

    companion object {
        const val DEFAULT_TRANSITION_DURATION = 400

        /** Total duration in milliseconds of the programmatic indicator glow pulse (0 → peak → 0). */
        const val PROGRAMMATIC_GLOW_DURATION = 250L
    }
}

