package app.simple.felicity.core.m3u

/**
 * A complete, spec-faithful parser for M3U and M3U8 playlist files.
 *
 * <p>Supported directives:</p>
 * <ul>
 *   <li>{@code #EXTM3U} — header marker, validated but nothing is extracted.</li>
 *   <li>{@code #EXTINF:duration,Title} — attaches duration (seconds) and a display
 *       name to the very next path line. Fractional durations (e.g. {@code 237.5})
 *       are truncated to whole seconds. A missing comma means only the duration is
 *       present. A duration of {@code -1} is the M3U convention for "unknown".</li>
 *   <li>{@code #PLAYLIST:name} — optional playlist-level display name.</li>
 *   <li>All other {@code #…} lines are treated as comments and silently ignored.</li>
 * </ul>
 *
 * <p>The parser is intentionally forgiving: malformed {@code #EXTINF} values are
 * replaced with safe defaults rather than throwing, and UTF-8 BOMs are stripped
 * before processing so files saved by Windows tools work without drama.</p>
 *
 * @author Hamza417
 */
object M3uParser {

    private const val DIRECTIVE_M3U = "#EXTM3U"
    private const val DIRECTIVE_INF = "#EXTINF:"
    private const val DIRECTIVE_PLAYLIST = "#PLAYLIST:"

    /**
     * Parses an M3U or M3U8 playlist from a full content string.
     *
     * <p>Strips a leading UTF-8 BOM character if one is present — some tools
     * love adding those and it confuses parsers that forget to check.</p>
     *
     * @param content The raw text content of the M3U file.
     * @return A fully populated [M3uPlaylist].
     */
    fun parse(content: String): M3uPlaylist {
        // Some Windows tools save M3U files with a UTF-8 BOM. Strip it here so
        // it doesn't accidentally end up as part of the first directive or path.
        val clean = content.trimStart('\uFEFF')
        return parseLines(clean.lineSequence())
    }

    /**
     * Parses an M3U playlist from a lazy sequence of lines.
     *
     * <p>This overload is memory-friendly for large playlists because it does not
     * require the whole file to be in memory as a single string. The parser reads
     * one line at a time and builds the result incrementally.</p>
     *
     * @param lines The lines of the M3U file as a lazy sequence.
     * @return A fully populated [M3uPlaylist].
     */
    fun parse(lines: Sequence<String>): M3uPlaylist = parseLines(lines)

    /**
     * Core parsing logic shared by both public overloads.
     * Walks through each line and decides whether it is a directive, a comment,
     * or a track path, building up the [M3uPlaylist] as it goes.
     */
    private fun parseLines(lines: Sequence<String>): M3uPlaylist {
        val entries = mutableListOf<M3uEntry>()
        var playlistName: String? = null

        // These hold the metadata from the most recent #EXTINF line. They get
        // consumed (and reset) as soon as the next path line is encountered.
        var pendingTitle: String? = null
        var pendingDuration: Long = -1L

        for (rawLine in lines) {
            val line = rawLine.trim()

            when {
                // Blank lines are of no interest to us — carry on.
                line.isEmpty() -> Unit

                // The #EXTM3U header just confirms this is an extended M3U file.
                // Nothing to extract here; its presence is its message.
                line.equals(DIRECTIVE_M3U, ignoreCase = true) -> Unit

                // #PLAYLIST:My Awesome Soundtrack — optional human-readable name.
                line.startsWith(DIRECTIVE_PLAYLIST, ignoreCase = true) -> {
                    playlistName = line.substring(DIRECTIVE_PLAYLIST.length).trim()
                        .takeIf { it.isNotEmpty() }
                }

                // #EXTINF:duration,Track Title — rich metadata for the upcoming track.
                line.startsWith(DIRECTIVE_INF, ignoreCase = true) -> {
                    val body = line.substring(DIRECTIVE_INF.length)
                    val commaIndex = body.indexOf(',')

                    if (commaIndex >= 0) {
                        // Everything before the comma is the duration.
                        // Everything after is the title (which itself may contain commas).
                        pendingDuration = body.substring(0, commaIndex).trim()
                            .toDoubleOrNull()?.toLong() ?: -1L
                        val rawTitle = body.substring(commaIndex + 1).trim()
                        pendingTitle = rawTitle.takeIf { it.isNotEmpty() }
                    } else {
                        // No comma means just a duration, no title.
                        pendingDuration = body.trim().toDoubleOrNull()?.toLong() ?: -1L
                        pendingTitle = null
                    }
                }

                // Any other # line is just a comment. We respect its privacy.
                line.startsWith('#') -> Unit

                // Everything else is a track path or URL.
                else -> {
                    entries.add(
                            M3uEntry(
                                    path = line,
                                    title = pendingTitle,
                                    durationSeconds = pendingDuration
                            )
                    )
                    // Reset pending metadata so it doesn't bleed onto the next track.
                    pendingTitle = null
                    pendingDuration = -1L
                }
            }
        }

        return M3uPlaylist(name = playlistName, entries = entries)
    }
}

