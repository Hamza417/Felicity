package app.simple.felicity.models

data class SeekbarState(
        val position: Float,
        val max: Float,
        val min: Float,
        val stepSize: Float = 0f,
        val default: Float = 0f,
        val leftLabel: Boolean = false,
        val rightLabel: Boolean = false,
        /**
         * Provides the formatted string for the left-side label.
         * Only called when [leftLabel] is true.
         * Receives (progress, min, max).
         */
        val leftLabelProvider: ((progress: Float, min: Float, max: Float) -> String)? = null,
        /**
         * Provides the formatted string for the right-side label.
         * Only called when [rightLabel] is true.
         * Receives (progress, min, max).
         */
        val rightLabelProvider: ((progress: Float, min: Float, max: Float) -> String)? = null,
)
