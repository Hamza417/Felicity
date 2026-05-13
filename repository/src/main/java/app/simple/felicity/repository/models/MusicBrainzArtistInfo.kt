package app.simple.felicity.repository.models

/**
 * A clean summary of what MusicBrainz knows about an artist.
 *
 * Rather than handing raw JSON all the way up to the UI, this class holds only
 * the fields we actually want to display so every layer above the repository
 * stays blissfully ignorant of the API shape.
 *
 * @param name          The artist's name as stored in MusicBrainz.
 * @param disambiguation A short one-liner that tells artists with the same name
 *                       apart (e.g. "Norwegian DJ and music producer").
 * @param type          Whether this is a Person, Group, Orchestra, etc.
 * @param country       ISO 3166-1 alpha-2 country code where the artist is from.
 * @param beginYear     The year the artist started (born or formed).
 * @param endYear       The year the artist ended (died or disbanded), if applicable.
 * @param ended         True when the artist is no longer active.
 * @param tags          Genre and style tags voted on by the MusicBrainz community,
 *                      ordered by vote count so the most agreed-upon tags come first.
 * @param bio           A short biography pulled from the artist's linked Wikipedia page.
 *                      Null when no Wikipedia page is linked.
 * @param wikipediaUrl  The full URL to the artist's Wikipedia page, if one is linked.
 */
data class MusicBrainzArtistInfo(
        val name: String,
        val disambiguation: String?,
        val type: String?,
        val country: String?,
        val beginYear: String?,
        val endYear: String?,
        val ended: Boolean,
        val tags: List<String>,
        val bio: String?,
        val wikipediaUrl: String?
)

