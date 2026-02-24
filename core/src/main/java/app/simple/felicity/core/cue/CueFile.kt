package app.simple.felicity.core.cue

data class CueFile(
        val fileName: String,
        val type: String?,
        val tracks: MutableList<CueTrack> = mutableListOf()
)