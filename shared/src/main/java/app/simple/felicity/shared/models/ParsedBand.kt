package app.simple.felicity.shared.models

data class ParsedBand(
        val gain: Float,
        val q: Float,
        val frequencyHz: Float,
        val color: Int = 0,
        val type: Int = 0
)
