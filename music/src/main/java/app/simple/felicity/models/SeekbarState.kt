package app.simple.felicity.models

data class SeekbarState(
        val position: Float,
        val max: Float,
        val min: Float,
        val stepSize: Float = 0f,
        val default: Float = 0f
)
