package app.simple.felicity.repository.engines

import app.simple.felicity.repository.models.AudioWithStat

data class ScoredCandidate(
        val audioWithStat: AudioWithStat,
        val score: Double
)