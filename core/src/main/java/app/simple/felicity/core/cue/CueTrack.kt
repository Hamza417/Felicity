package app.simple.felicity.core.cue

data class CueTrack(
        val number: Int,
        val type: String?,
        var title: String? = null,
        var performer: String? = null,
        var startMs: Long = 0L,
        var pregapMs: Long = 0L,
        var file: CueFile? = null
)