package app.simple.felicity.repository.models

import com.google.gson.annotations.SerializedName

/**
 * Represents a single artist entry returned by the MusicBrainz artist-search endpoint.
 * MusicBrainz calls these "artist records" and each one has a unique identifier (MBID)
 * we can use to fetch more detailed information.
 */
data class MusicBrainzArtist(
        val id: String?,
        val name: String?,
        val score: Int = 0
)

/**
 * The top-level wrapper around the list of artist matches that MusicBrainz sends back
 * when we search for an artist by name.
 */
data class MusicBrainzArtistSearchResponse(
        val artists: List<MusicBrainzArtist>?
)

/**
 * Describes a single URL relationship attached to an artist in MusicBrainz.
 * The [type] field tells us what kind of link it is (e.g. "wikipedia", "wikidata"),
 * and [url] holds the actual address.
 */
data class MusicBrainzUrlRelation(
        val type: String?,
        val url: MusicBrainzUrl?
)

/**
 * The URL resource inside a [MusicBrainzUrlRelation].
 */
data class MusicBrainzUrl(
        val resource: String?
)

/**
 * The full artist detail response from MusicBrainz when we request an artist by MBID
 * with tags and URL relations included. Each field maps directly to the JSON key
 * MusicBrainz uses, hence the [SerializedName] annotations.
 */
data class MusicBrainzArtistDetail(
        val id: String?,
        val name: String?,
        val disambiguation: String?,
        val type: String?,
        val country: String?,
        @SerializedName("life-span")
        val lifeSpan: MusicBrainzLifeSpan?,
        val tags: List<MusicBrainzTag>?,
        val relations: List<MusicBrainzUrlRelation>?
)

/**
 * The birth/formation and death/disbandment years for an artist.
 * Both fields can be a full date ("1988-06-14") or just a year ("1988").
 * We only care about the year portion, so we trim if needed at use-time.
 */
data class MusicBrainzLifeSpan(
        val begin: String?,
        val end: String?,
        val ended: Boolean = false
)

/**
 * A single genre or style tag attached to an artist. [count] is the net vote
 * score — higher means more community members agree this tag fits.
 */
data class MusicBrainzTag(
        val name: String?,
        val count: Int = 0
)

/**
 * The response shape from the Wikipedia REST API's "page summary" endpoint.
 * [extract] is the plain-text introductory paragraph — perfect for a bio blurb.
 * [thumbnail] holds the representative image if one exists.
 */
data class WikipediaPageSummary(
        val extract: String?,
        val thumbnail: WikipediaThumbnail?
)

/**
 * The thumbnail object inside a [WikipediaPageSummary].
 * [source] is the direct URL to the image we want to download.
 */
data class WikipediaThumbnail(
        val source: String?
)

/**
 * The part of the Wikidata API response we care about when resolving a Wikidata entity
 * to its English Wikipedia article title. The full response is deeply nested — this class
 * only models the path we actually walk: entities → {id} → sitelinks → enwiki → title.
 */
data class WikidataEntityResponse(
        val entities: Map<String, WikidataEntity>?
)

/** One entity inside the Wikidata response. */
data class WikidataEntity(
        val sitelinks: Map<String, WikidataSitelink>?
)

/**
 * A single sitelink entry. For our purposes the key is the site code (e.g. "enwiki")
 * and [title] is the Wikipedia article title we need to build a page URL.
 */
data class WikidataSitelink(
        val title: String?
)

