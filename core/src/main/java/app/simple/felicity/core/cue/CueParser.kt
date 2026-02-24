package app.simple.felicity.core.cue

object CueParser {

    fun parse(text: String): CueSheet {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("REM", ignoreCase = true) }
            .toList()

        val files = mutableListOf<CueFile>()
        val allTracks = mutableListOf<CueTrack>()

        var currentFile: CueFile? = null
        var currentTrack: CueTrack? = null

        var sheetTitle: String? = null
        var sheetPerformer: String? = null

        for (raw in lines) {
            val line = raw.replace(Regex("\\s+"), " ")
            val parts = line.split(" ", limit = 2)
            val cmd = parts[0].uppercase()
            val arg = parts.getOrNull(1)?.trim()

            when (cmd) {

                "TITLE" -> {
                    val value = unquote(arg)
                    if (currentTrack != null) currentTrack.title = value
                    else sheetTitle = value
                }

                "PERFORMER" -> {
                    val value = unquote(arg)
                    if (currentTrack != null) currentTrack.performer = value
                    else sheetPerformer = value
                }

                "FILE" -> {
                    val (name, type) = parseFile(arg)
                    currentFile = CueFile(name, type)
                    files += currentFile
                }

                "TRACK" -> {
                    val (num, type) = parseTrack(arg)
                    val track = CueTrack(num, type)
                    track.file = currentFile
                    currentFile?.tracks?.add(track)
                    allTracks += track
                    currentTrack = track
                }

                "INDEX" -> {
                    val (indexNum, time) = parseIndex(arg)
                    if (indexNum == 1 && currentTrack != null) {
                        currentTrack.startMs = cueTimeToMs(time)
                    }
                }

                "PREGAP" -> {
                    currentTrack?.pregapMs = cueTimeToMs(arg)
                }
            }
        }

        return CueSheet(files, allTracks, sheetTitle, sheetPerformer)
    }

    private fun parseFile(arg: String?): Pair<String, String?> {
        if (arg == null) return "" to null
        val match = Regex("\"(.*?)\"\\s*(\\w+)?").find(arg)
        return if (match != null) {
            match.groupValues[1] to match.groupValues.getOrNull(2)
        } else {
            val parts = arg.split(" ")
            parts[0] to parts.getOrNull(1)
        }
    }

    private fun parseTrack(arg: String?): Pair<Int, String?> {
        val parts = arg?.split(" ") ?: return 0 to null
        val num = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val type = parts.getOrNull(1)
        return num to type
    }

    private fun parseIndex(arg: String?): Pair<Int, String> {
        val parts = arg?.split(" ") ?: return 0 to "00:00:00"
        val num = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val time = parts.getOrNull(1) ?: "00:00:00"
        return num to time
    }

    private fun cueTimeToMs(time: String?): Long {
        if (time == null) return 0
        val parts = time.split(":")
        val mm = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val ss = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val ff = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return ((mm * 60 + ss) * 1000L) + (ff * 1000L / 75L)
    }

    private fun unquote(s: String?): String? {
        if (s == null) return null
        return s.trim().removePrefix("\"").removeSuffix("\"")
    }
}