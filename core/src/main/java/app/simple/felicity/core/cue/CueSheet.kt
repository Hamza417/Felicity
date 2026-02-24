package app.simple.felicity.core.cue

data class CueSheet(
        val files: List<CueFile>,
        val tracks: List<CueTrack>,
        val title: String? = null,
        val performer: String? = null
)