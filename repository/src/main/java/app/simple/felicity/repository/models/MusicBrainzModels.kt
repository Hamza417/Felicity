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
 * The artist detail response that MusicBrainz sends when we request a specific artist
 * by MBID with URL relations included. We only need the relations list here.
 */
data class MusicBrainzArtistDetail(
        @SerializedName("relations")
        val relations: List<MusicBrainzUrlRelation>?
)

/**
 * The response shape from the Wikipedia REST API's "page summary" endpoint.
 * We only care about the thumbnail image, so everything else is ignored.
 */
data class WikipediaPageSummary(
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

