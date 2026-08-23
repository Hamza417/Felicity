package app.simple.felicity.utils

import app.simple.felicity.utils.PeqFileParser.CSV_MATCH_THRESHOLD
import app.simple.felicity.utils.PeqFileParser.MAX_GRAPHIC_EQ_BANDS
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * A "one for all" parser for parametric/graphic EQ preset files, built to tolerate the many
 * subtly different formats produced by the PEQ tooling ecosystem (CrinGraph, SquigLink,
 * AutoEQ, Equalizer APO, Wavelet, RootlessJamesDSP/JDSP, Poweramp-style JSON exports, plain
 * CSV frequency response dumps, etc.) without requiring the user to know or care which one
 * they have.
 *
 * Supported formats, tried in order until one succeeds:
 *
 * 1. **AutoEQ / Equalizer APO ParametricEQ plain text** — the de-facto standard produced by
 *    CrinGraph/SquigLink and consumed by Equalizer APO, Wavelet, RootlessJamesDSP, etc.:
 *    ```
 *    Preamp: -3.5 dB
 *    Filter 1: ON PK  Fc 80   Hz Gain  4.2 dB Q 0.8
 *    Filter 2: ON LSC Fc 30   Hz Gain -5.0 dB Q 0.7
 *    Filter 3: ON HSC Fc 8000 Hz Gain -2.5 dB Q 0.7
 *    Filter 4: OFF PK Fc 400  Hz Gain  0.0 dB Q 1.0
 *    ```
 *    Recognized type keywords (case-insensitive, prefix-matched): PK/PEQ (peak),
 *    LSC/LS/LSQ (low-shelf), HSC/HS/HSQ (high-shelf). Lines using other APO filter kinds
 *    (LP, HP, BP, AP, NO, MODAL) are skipped since they cannot be represented as a single
 *    gain/Q/frequency node. Filters marked `OFF` are silently skipped. The `Filter N:` prefix
 *    and the `ON`/`OFF` token are both optional so terser exports still parse correctly.
 *
 * 2. **Equalizer APO GraphicEQ line** — a single line of comma-separated `freq gain` pairs:
 *    ```
 *    GraphicEQ: 20 0.0; 21 0.1; 25 0.4; ... 20000 -1.2
 *    ```
 *    Since these curves can carry 100+ points, the result is uniformly down-sampled to
 *    [MAX_GRAPHIC_EQ_BANDS] nodes so the parametric slider stays usable.
 *
 * 3. **JSON** — either a bare preset object or an array of preset objects (first one wins),
 *    or even a bare array of band objects with no wrapper at all. Key names are matched
 *    liberally to cover the different apps that export this shape:
 *      - preset name: `name` / `title` / `presetName` / `preset_name`
 *      - preamp: `preamp` / `preAmp` / `preampGain` / `preamp_gain` / `globalGain`
 *      - band list: `bands` / `filters` / `peq` / `eq` / a bare top-level array
 *      - frequency: `frequency` / `freq` / `fc` / `hz`
 *      - Q: `q` / `Q` / `qFactor` / `bandwidth`
 *      - gain: `gain` / `gainDb` / `gain_db` / `db` / `value`
 *      - enabled flag (optional): `enabled` / `active` / `on` — bands explicitly disabled
 *        are skipped, everything else defaults to enabled.
 *
 * 4. **Plain CSV / whitespace frequency-response dump** — lines of `freq, gain[, q]` with no
 *    keywords at all. Only used as a last resort, and only when the vast majority of
 *    non-empty lines actually look numeric, to avoid misfiring on unrelated text files.
 *
 * Note: the current DSP engine treats every parametric band as a peaking filter regardless
 * of the type it was tagged with in the source file. Shelf entries are imported with their
 * gain, Q, and frequency intact, but the exact shelf curve shape is not yet replicated.
 *
 * @author Hamza417
 */
object PeqFileParser {

    /** GraphicEQ curves are down-sampled to at most this many parametric bands. */
    private const val MAX_GRAPHIC_EQ_BANDS = 24

    /** Minimum fraction of non-empty lines that must look numeric for the CSV fallback to trigger. */
    private const val CSV_MATCH_THRESHOLD = 0.8

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
     * Parses [text] as a PEQ preset file and returns a [ParsedPreset], or null if none of
     * the supported formats could be recognized. The [fileName] is used to derive the
     * human-readable preset name when the file itself does not supply one — the extension
     * is stripped automatically.
     *
     * Every supported format is tried, in order of how unambiguous its signature is, until
     * one of them successfully yields at least one band.
     *
     * @param text     The full text content of the imported file.
     * @param fileName The original file name (with or without an extension).
     */
    fun parse(text: String, fileName: String): ParsedPreset? {
        val presetName = fileName.substringBeforeLast(".", fileName)
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        // 1. JSON is the most unambiguous signature — try it first if it looks like JSON.
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            parseJson(trimmed, presetName)?.let { return it }
        }

        // 2. Equalizer APO GraphicEQ single-line curve.
        parseGraphicEq(text, presetName)?.let { return it }

        // 3. Standard AutoEQ / Equalizer APO ParametricEQ plain text (Preamp:/Filter lines).
        parsePlainText(text, presetName)?.let { return it }

        // 4. Last resort: raw numeric "freq, gain[, q]" rows with no keywords at all.
        parseCsv(text, presetName)?.let { return it }

        return null
    }

    // -------------------------------------------------------------------------
    // JSON
    // -------------------------------------------------------------------------

    /**
     * Handles JSON presets. Accepts a bare preset object, an array of preset objects
     * (first one wins), or a bare array of band objects with no wrapper. Key names are
     * matched against every alias used by the tools in the wild.
     */
    private fun parseJson(text: String, fallbackName: String): ParsedPreset? {
        return try {
            when (val root = JSONTokener(text).nextValue()) {
                is JSONArray -> {
                    if (root.length() == 0) return null
                    // Distinguish "array of presets" from "bare array of bands": a preset
                    // object has a bands/filters/peq/eq key, a band object has a frequency key.
                    val first = root.optJSONObject(0) ?: return null
                    if (findBandsArray(first) != null || first.has("name")) {
                        parseJsonPreset(first, fallbackName)
                    } else {
                        parseJsonBandArray(root, fallbackName, preambDb = 0f)
                    }
                }
                is JSONObject -> parseJsonPreset(root, fallbackName)
                else -> null
            }
        } catch (_: JSONException) {
            null
        }
    }

    private fun parseJsonPreset(root: JSONObject, fallbackName: String): ParsedPreset? {
        val name = firstNonEmptyString(root, "name", "title", "presetName", "preset_name") ?: fallbackName
        val preampDb = firstDouble(root, "preamp", "preAmp", "preampGain", "preamp_gain", "globalGain")?.toFloat() ?: 0f
        val bandsJson = findBandsArray(root) ?: return null
        return parseJsonBandArray(bandsJson, name, preampDb)
    }

    private fun parseJsonBandArray(bandsJson: JSONArray, name: String, preambDb: Float): ParsedPreset? {
        val bands = mutableListOf<Triple<Float, Float, Float>>()
        for (i in 0 until bandsJson.length()) {
            val band = bandsJson.optJSONObject(i) ?: continue

            // Respect an explicit enabled/active/on flag when present; default to enabled.
            val enabled = firstBoolean(band, "enabled", "active", "on") ?: true
            if (!enabled) continue

            val freq = firstDouble(band, "frequency", "freq", "fc", "hz")?.toFloat() ?: continue
            if (freq.isNaN() || freq <= 0f) continue

            val q = firstDouble(band, "q", "Q", "qFactor", "bandwidth")?.toFloat() ?: 1.0f
            val gain = firstDouble(band, "gain", "gainDb", "gain_db", "db", "value")?.toFloat() ?: 0f

            bands.add(Triple(gain, q, freq))
        }

        if (bands.isEmpty()) return null
        return ParsedPreset(name = name, preampDb = preambDb, bands = bands)
    }

    /** Looks for the band list under any of the common key aliases used across tools. */
    private fun findBandsArray(root: JSONObject): JSONArray? =
        root.optJSONArray("bands")
            ?: root.optJSONArray("filters")
            ?: root.optJSONArray("peq")
            ?: root.optJSONArray("eq")

    private fun firstNonEmptyString(obj: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            val value = obj.optString(key, "")
            if (value.isNotBlank()) return value
        }
        return null
    }

    private fun firstDouble(obj: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                val value = obj.optDouble(key, Double.NaN)
                if (!value.isNaN()) return value
            }
        }
        return null
    }

    private fun firstBoolean(obj: JSONObject, vararg keys: String): Boolean? {
        for (key in keys) {
            if (obj.has(key) && !obj.isNull(key)) return obj.optBoolean(key, true)
        }
        return null
    }

    // -------------------------------------------------------------------------
    // GraphicEQ (Equalizer APO single-line curve)
    // -------------------------------------------------------------------------

    /**
     * Handles the Equalizer APO "GraphicEQ:" line — a dense curve of `freq gain` pairs
     * separated by `;` or `,`. The curve is uniformly down-sampled to [MAX_GRAPHIC_EQ_BANDS]
     * peaking bands so it stays usable on the parametric slider.
     */
    private fun parseGraphicEq(text: String, fallbackName: String): ParsedPreset? {
        val line = text.lines().firstOrNull { it.trim().startsWith("GraphicEQ:", ignoreCase = true) }
            ?: return null

        var preampDb = 0f
        text.lines().firstOrNull { it.trim().startsWith("Preamp:", ignoreCase = true) }?.let {
            preampDb = parsePreampLine(it.trim()) ?: 0f
        }

        val after = line.substringAfter(":", "").trim()
        val points = after.split(";", ",")
            .mapNotNull { pair ->
                val parts = pair.trim().split("\\s+".toRegex())
                if (parts.size < 2) return@mapNotNull null
                val freq = parts[0].toFloatOrNull() ?: return@mapNotNull null
                val gain = parts[1].toFloatOrNull() ?: return@mapNotNull null
                if (freq <= 0f) return@mapNotNull null
                freq to gain
            }

        if (points.isEmpty()) return null

        val sampled = if (points.size <= MAX_GRAPHIC_EQ_BANDS) {
            points
        } else {
            val step = (points.size - 1).toFloat() / (MAX_GRAPHIC_EQ_BANDS - 1)
            (0 until MAX_GRAPHIC_EQ_BANDS).map { i ->
                points[(i * step).toInt().coerceIn(0, points.size - 1)]
            }.distinctBy { it.first }
        }

        val bands = sampled.map { (freq, gain) -> Triple(gain, 1.0f, freq) }
        if (bands.isEmpty()) return null

        return ParsedPreset(name = fallbackName, preampDb = preampDb, bands = bands)
    }

    // -------------------------------------------------------------------------
    // AutoEQ / Equalizer APO ParametricEQ plain text
    // -------------------------------------------------------------------------

    /**
     * Handles the standard AutoEQ / ParametricEQ plain-text format.
     */
    private fun parsePlainText(text: String, fallbackName: String): ParsedPreset? {
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
        return ParsedPreset(name = fallbackName, preampDb = preampDb, bands = bands)
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
     * disabled, malformed, or uses a filter type that cannot be represented as a
     * single gain/Q/frequency node (LP, HP, BP, AP, NO, MODAL).
     *
     * The parser is intentionally lenient about token ordering, spacing, and the
     * presence of the "Filter N:" / "ON" tokens so it handles files produced by a
     * wide variety of tools, not just the canonical Equalizer APO format.
     */
    private fun parseFilterLine(line: String): Triple<Float, Float, Float>? {
        val afterColon = line.substringAfter(":", line).trim()
        val tokens = afterColon.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        var i = 0

        // The ON/OFF status token is optional in some exports; when present, honor it.
        if (tokens[i].equals("ON", ignoreCase = true)) {
            i++
        } else if (tokens[i].equals("OFF", ignoreCase = true)) {
            return null
        }

        // The filter type keyword should be next. Reject unsupported shapes outright.
        val type = tokens.getOrNull(i)?.uppercase() ?: return null
        val isSupportedType = type.startsWith("PK") || type.startsWith("PEQ") ||
                type.startsWith("LS") || type.startsWith("HS")
        if (!isSupportedType) return null

        var freq: Float? = null
        var gain: Float? = null
        var q: Float? = null

        // Walk the remaining tokens looking for the Fc, Gain, and Q keywords.
        // We skip over anything we don't recognize, which makes the parser
        // resilient against extra columns some tools include.
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

    // -------------------------------------------------------------------------
    // Plain CSV / whitespace frequency-response dump (last resort)
    // -------------------------------------------------------------------------

    /**
     * Handles bare `freq, gain[, q]` rows with no keywords whatsoever, as produced by
     * some measurement/response export tools. To avoid misidentifying unrelated text
     * files, this is only used when at least [CSV_MATCH_THRESHOLD] of the non-empty
     * lines actually look like a numeric row.
     */
    private fun parseCsv(text: String, fallbackName: String): ParsedPreset? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val rowRegex = "^-?\\d+(\\.\\d+)?[\\s,;]+-?\\d+(\\.\\d+)?([\\s,;]+-?\\d+(\\.\\d+)?)?$".toRegex()
        val matching = lines.filter { rowRegex.matches(it) }
        if (matching.size < (lines.size * CSV_MATCH_THRESHOLD)) return null

        val bands = matching.mapNotNull { row ->
            val parts = row.split("[\\s,;]+".toRegex())
            val freq = parts.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
            val gain = parts.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
            if (freq <= 0f) return@mapNotNull null
            val q = parts.getOrNull(2)?.toFloatOrNull() ?: 1.0f
            Triple(gain, q, freq)
        }

        if (bands.isEmpty()) return null
        return ParsedPreset(name = fallbackName, preampDb = 0f, bands = bands)
    }
}

