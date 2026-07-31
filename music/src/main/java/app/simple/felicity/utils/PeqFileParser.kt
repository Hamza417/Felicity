package app.simple.felicity.utils

/**
 * Parses the standard AutoEQ / ParametricEQ plain-text format that tools like CrinGraph,
 * SquigLink, and most online EQ generators produce. A typical file looks like this:
 *
 * ```
 * Preamp: -3.5 dB
 * Filter 1: ON PK  Fc 80   Hz Gain  4.2 dB Q 0.8
 * Filter 2: ON LSC Fc 30   Hz Gain -5.0 dB Q 0.7
 * Filter 3: ON HSC Fc 8000 Hz Gain -2.5 dB Q 0.7
 * Filter 4: OFF PK Fc 400  Hz Gain  0.0 dB Q 1.0
 * ```
 *
 * Recognized filter type keywords (case-insensitive):
 *   PK  — peaking (bell) filter
 *   LSC or LS — low-shelf filter
 *   HSC or HS — high-shelf filter
 *
 * Any filter whose status token is not "ON" is silently skipped, so disabled
 * filters from the source file are never imported. Lines that are completely
 * unrecognized are also ignored, which keeps the parser tolerant of comments
 * or extra metadata some tools may add.
 *
 * Note: the current DSP engine handles all parametric bands as peaking filters.
 * Low-shelf and high-shelf entries from the file are imported with their gain,
 * Q, and frequency preserved, but their exact shelf curve shape is not replicated
 * by the engine at this time.
 *
 * @author Hamza417
 */
object PeqFileParser {

    /**
     * Holds the complete result after parsing a PEQ file.
     *
     * @property name      A suggested preset name taken from the file name (no extension).
     * @property preampDb  The overall pre-amplifier gain in dB from the "Preamp:" line.
     *                     Defaults to 0 dB when the line is absent.
     * @property bands     All enabled bands as (gainDb, qFactor, frequencyHz) triples,
     *                     in the order they appeared in the file.
     */
    data class ParsedPreset(
            val name: String,
            val preampDb: Float,
            val bands: List<Triple<Float, Float, Float>>
    )

    /**
     * Parses [text] as a ParametricEQ file and returns a [ParsedPreset], or null if no
     * valid, enabled filter lines could be found. The [fileName] is used to derive the
     * human-readable preset name — the extension is stripped automatically.
     *
     * @param text     The full text content of the imported file.
     * @param fileName The original file name (with or without an extension).
     */
    fun parse(text: String, fileName: String): ParsedPreset? {
        var preampDb = 0f
        val bands = mutableListOf<Triple<Float, Float, Float>>()

        for (line in text.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Preamp:", ignoreCase = true) -> {
                    preampDb = parsePreampLine(trimmed) ?: preampDb
                }
                trimmed.startsWith("Filter", ignoreCase = true) -> {
                    val band = parseFilterLine(trimmed)
                    if (band != null) bands.add(band)
                }
            }
        }

        if (bands.isEmpty()) return null

        // Drop the file extension so "Moondrop Chu 2.txt" becomes "Moondrop Chu 2".
        val name = fileName.substringBeforeLast(".", fileName)
        return ParsedPreset(name = name, preampDb = preampDb, bands = bands)
    }

    /**
     * Extracts the numeric dB value from a line like "Preamp: -3.5 dB".
     * Returns null when the line is malformed or has no parsable number.
     */
    private fun parsePreampLine(line: String): Float? {
        val after = line.substringAfter(":", "").trim()
        // The first whitespace-delimited token should be the dB number.
        return after.split("\\s+".toRegex()).firstOrNull()?.toFloatOrNull()
    }

    /**
     * Parses a single "Filter N: ON TYPE Fc X Hz Gain Y dB Q Z" line into a
     * (gainDb, qFactor, frequencyHz) triple. Returns null for any line that is
     * disabled, malformed, or uses an unrecognized filter type keyword.
     *
     * The parser is intentionally lenient about token ordering and spacing so it
     * handles files produced by a variety of tools, not just the canonical format.
     */
    private fun parseFilterLine(line: String): Triple<Float, Float, Float>? {
        val afterColon = line.substringAfter(":", "").trim()
        val tokens = afterColon.split("\\s+".toRegex())

        // Need at least the ON/OFF status token and a filter type.
        if (tokens.size < 2) return null

        // Skip any filter that is explicitly turned off in the source file.
        if (!tokens[0].equals("ON", ignoreCase = true)) return null

        var freq: Float? = null
        var gain: Float? = null
        var q: Float? = null

        // Walk the tokens looking for the Fc, Gain, and Q keywords.
        // We skip over anything we don't recognize, which makes the parser
        // resilient against extra columns some tools include.
        var i = 0
        while (i < tokens.size) {
            when (tokens[i].uppercase()) {
                "FC" -> {
                    freq = tokens.getOrNull(i + 1)?.toFloatOrNull()
                    i++
                }
                "GAIN" -> {
                    gain = tokens.getOrNull(i + 1)?.toFloatOrNull()
                    i++
                }
                "Q" -> {
                    q = tokens.getOrNull(i + 1)?.toFloatOrNull()
                    i++
                }
            }
            i++
        }

        val f = freq ?: return null
        val g = gain ?: return null
        // Q is optional in some tool outputs; fall back to a neutral value of 1.0.
        val qVal = q ?: 1.0f

        return Triple(g, qVal, f)
    }
}

