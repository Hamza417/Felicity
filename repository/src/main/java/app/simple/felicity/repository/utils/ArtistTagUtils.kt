package app.simple.felicity.repository.utils

import app.simple.felicity.preferences.LibraryPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth for turning a raw artist metadata string (e.g. an ID3 "artist"
 * or "album_artist" field) into the list of individual artists it credits.
 *
 * The app stores the artist tag as one delimited string, so deciding where one artist ends
 * and the next begins is a heuristic. To avoid mangling names that legitimately contain
 * punctuation (AC/DC, Simon & Garfunkel, Earth, Wind & Fire, Tyler, The Creator), this
 * splitter is deliberately conservative:
 *
 *  - Only rarely-ambiguous characters (`;`, `|`, `\`, NUL) and high-confidence textual
 *    collaboration markers (`feat`, `ft`, `featuring`, `vs`, `x`, `pres`, `starring`) are
 *    treated as separators.
 *  - Characters that frequently appear inside real band names (`/`, `,`, `+`, `&`, `and`,
 *    `with`) are intentionally NOT separators.
 *  - Any field whose full value is on the bundled whitelist is never split, as a second line
 *    of defence (mainly relevant when the user configures custom separators).
 *
 * Behaviour is user-configurable through [LibraryPreferences]: splitting can be turned off
 * entirely, and a custom separator set can replace the built-in one.
 */
object ArtistTagUtils {

    /**
     * Conservative default separator set. See the class doc for the rationale behind which
     * delimiters are included and, more importantly, which are intentionally left out. The
     * NUL character covers real multi-value tags (e.g. ID3v2.4) should they ever reach here.
     */
    private const val DEFAULT_ARTIST_SEPARATOR_REGEX =
            "\\s*[;|\\x00\\\\]\\s*" +
                    "|\\s+feat\\.?\\s+|\\s+ft\\.?\\s+|\\s+featuring\\s+" +
                    "|\\s+vs\\.?\\s+|\\s+x\\s+|\\s+pres\\.?\\s+|\\s+starring\\s+"

    private const val ARTIST_WHITELIST = "/artist_whitelist.txt"

    private val defaultRegex: Regex by lazy {
        Regex(DEFAULT_ARTIST_SEPARATOR_REGEX, RegexOption.IGNORE_CASE)
    }

    private val customRegexCache = ConcurrentHashMap<String, Regex>()

    /**
     * Known artist names that contain characters which could otherwise be misread as
     * separators. Loaded once from the bundled resource and compared case-insensitively.
     */
    private val bundledWhitelist: Set<String> by lazy {
        ArtistTagUtils::class.java.getResourceAsStream(ARTIST_WHITELIST)
            ?.bufferedReader()?.use { reader ->
                reader.readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toHashSet()
            } ?: emptySet()
    }

    /**
     * Splits [field] into the individual artists it credits.
     *
     * The default arguments read live values from [LibraryPreferences] and the bundled
     * whitelist, so production callers simply call `splitArtists(field)`. Unit tests can pass
     * explicit values to keep the logic pure and independent of Android.
     *
     * @return the credited artists in their original order, de-duplicated; an empty list when
     *         [field] is null or blank.
     */
    fun splitArtists(
            field: String?,
            splittingEnabled: Boolean = LibraryPreferences.isSplitMultipleArtistsEnabled(),
            customSeparators: String = LibraryPreferences.getArtistSeparators(),
            whitelist: Set<String> = bundledWhitelist
    ): List<String> {
        val raw = field?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        if (!splittingEnabled) return listOf(raw)

        val regex = separatorRegexFor(customSeparators)
        // Fast path: nothing that looks like a separator, so it's a single artist.
        if (!regex.containsMatchIn(raw)) return listOf(raw)
        // Second line of defence: never split an officially whitelisted name.
        if (isWhitelisted(raw, whitelist)) return listOf(raw)

        return raw.split(regex)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /**
     * Whether [artistField] actually credits [name] as a standalone artist.
     *
     * A single-word name must match one of the split pieces exactly (so "AC" does not match
     * "AC/DC", and "LISA" does not match "LISA GERRARD"). A multi-word name may also match as a
     * case-insensitive substring, which keeps the previous, more tolerant behaviour for
     * naturally specific names like "Daft Punk".
     */
    fun artistFieldMatchesName(
            artistField: String?,
            name: String,
            splittingEnabled: Boolean = LibraryPreferences.isSplitMultipleArtistsEnabled(),
            customSeparators: String = LibraryPreferences.getArtistSeparators(),
            whitelist: Set<String> = bundledWhitelist
    ): Boolean {
        if (artistField == null) return false
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return false

        val parts = splitArtists(artistField, splittingEnabled, customSeparators, whitelist)
        if (parts.any { it.equals(trimmedName, ignoreCase = true) }) return true

        return trimmedName.contains(' ') && artistField.contains(trimmedName, ignoreCase = true)
    }

    /**
     * Resolves the separator [Regex] to use. An empty custom value falls back to the safe
     * built-in default; a non-empty one replaces it, with each whitespace-separated token
     * treated as a literal separator (surrounding whitespace is absorbed).
     */
    private fun separatorRegexFor(customSeparators: String): Regex {
        val key = customSeparators.trim()
        if (key.isEmpty()) return defaultRegex
        return customRegexCache.getOrPut(key) {
            val pattern = key.split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .joinToString("|") { "\\s*" + Regex.escape(it) + "\\s*" }
            if (pattern.isEmpty()) defaultRegex else Regex(pattern, RegexOption.IGNORE_CASE)
        }
    }

    private fun isWhitelisted(field: String, whitelist: Set<String>): Boolean {
        return whitelist.any { it.equals(field, ignoreCase = true) }
    }
}
 