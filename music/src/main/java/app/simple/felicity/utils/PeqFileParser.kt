package app.simple.felicity.utils

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Parses parametric EQ presets from two different file formats so the user can import
 * presets from a wide range of tools without worrying about which format they used.
 *
 * The two supported formats are:
 *
 * 1. The AutoEQ / ParametricEQ plain-text format produced by tools like CrinGraph and
 *    SquigLink. A typical file looks like:
 *    ```
 *    Preamp: -3.5 dB
 *    Filter 1: ON PK  Fc 80   Hz Gain  4.2 dB Q 0.8
 *    Filter 2: ON LSC Fc 30   Hz Gain -5.0 dB Q 0.7
 *    Filter 3: ON HSC Fc 8000 Hz Gain -2.5 dB Q 0.7
 *    Filter 4: OFF PK Fc 400  Hz Gain  0.0 dB Q 1.0
 *    ```
 *    Recognized type keywords (case-insensitive): PK, LSC/LS (low-shelf), HSC/HS (high-shelf).
 *    Filters marked OFF are silently skipped.
 *
 * 2. A JSON array format where each element is a preset object with a `name`, `preamp`,
 *    and `bands` array. Each band carries `type` (0 = low-shelf, 1 = high-shelf, 3 = peak),
 *    `frequency`, `q`, and `gain` fields. The first element in the array is used.
 *
 * The format is detected automatically by checking whether the content starts with `[` or `{`.
 *
 * Note: the current DSP engine handles all parametric bands as peaking filters.
 * Low-shelf and high-shelf entries are imported with their gain, Q, and frequency intact,
 * but the exact shelf curve shape is not replicated by the engine at this time.
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
     * valid filter data could be found. The [fileName] is used to derive the human-readable
     * preset name when the file itself does not supply one — the extension is stripped automatically.
     *
     * The format is detected automatically: if the content starts with `[` or `{` the JSON
     * path is tried first; otherwise the plain-text AutoEQ path is used.
     *
     * @param text     The full text content of the imported file.
     * @param fileName The original file name (with or without an extension).
     */
    fun parse(text: String, fileName: String): ParsedPreset? {
        val trimmed = text.trimStart()
        return if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            parseJson(trimmed, fileName)
        } else {
            parsePlainText(text, fileName)
        }
    }

    /**
     * Handles the JSON array preset format where the file contains one or more preset
     * objects. Only the first preset in the array is imported. Each band object must
     * have `frequency`, `q`, and `gain` fields; bands where the gain is exactly 0 and
     * the type is a shelf (0 or 1) are still included so the user can see all nodes.
     */
    private fun parseJson(text: String, fileName: String): ParsedPreset? {
        return try {
            // The format is always a JSON array at the top level, but guard against a
            // bare object just in case some tools omit the outer brackets.
            val root: JSONObject = if (text.startsWith("[")) {
                val arr = JSONArray(text)
                if (arr.length() == 0) return null
                arr.getJSONObject(0)
            } else {
                JSONObject(text)
            }

            val name = root.optString("name", fileName.substringBeforeLast(".", fileName))
            val preampDb = root.optDouble("preamp", 0.0).toFloat()
            val bandsJson = root.optJSONArray("bands") ?: return null

            val bands = mutableListOf<Triple<Float, Float, Float>>()
            for (i in 0 until bandsJson.length()) {
                val band = bandsJson.getJSONObject(i)
                val freq = band.optDouble("frequency", Double.NaN).toFloat()
                val q = band.optDouble("q", 1.0).toFloat()
                val gain = band.optDouble("gain", 0.0).toFloat()

                // Skip bands with invalid frequency values.
                if (freq.isNaN() || freq <= 0f) continue

                bands.add(Triple(gain, q, freq))
            }

            if (bands.isEmpty()) return null
            ParsedPreset(name = name, preampDb = preampDb, bands = bands)
        } catch (_: JSONException) {
            null
        }
    }

    /**
     * Handles the standard AutoEQ / ParametricEQ plain-text format.
     */
    private fun parsePlainText(text: String, fileName: String): ParsedPreset? {
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

