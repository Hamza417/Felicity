package app.simple.felicity.decorations.knobs.simple

import android.graphics.drawable.Drawable

/**
 * Base class for all rotary knob drawables.
 *
 * Subclasses must implement [onPressedStateChanged] to visually transition between
 * the idle (not-touched) and pressed (touched) states.
 *
 * [RotaryKnobView] will only accept instances of this class.
 */
abstract class RotaryKnobDrawable : Drawable() {

    /**
     * Called by [RotaryKnobView] when the touch state changes.
     *
     * @param pressed `true` when the knob is being touched, `false` when released.
     * @param animationDuration duration in milliseconds for the visual transition.
     */
    abstract fun onPressedStateChanged(pressed: Boolean, animationDuration: Int = DEFAULT_TRANSITION_DURATION)

    companion object {
        const val DEFAULT_TRANSITION_DURATION = 400
    }
}

