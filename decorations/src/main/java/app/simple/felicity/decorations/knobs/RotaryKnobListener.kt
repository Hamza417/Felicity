package app.simple.felicity.decorations.knobs

interface RotaryKnobListener {
    fun onRotate(value: Float)
    fun onIncrement(value: Float)
    fun onUserInteractionStart(value: Float) {}
    fun onUserInteractionEnd(value: Float) {}

    /**
     * Called whenever the knob value changes to request a display string.
     * Return the text you want rendered between the min/max tick marks.
     * Default returns the value rounded to one decimal place.
     */
    fun onLabel(value: Float): String = "%.1f".format(value)
}
