package app.simple.felicity.repository.repositories

import android.util.Log
import app.simple.felicity.repository.models.MusicBrainzArtistDetail
import app.simple.felicity.repository.models.MusicBrainzArtistInfo
import app.simple.felicity.repository.models.MusicBrainzArtistSearchResponse
import app.simple.felicity.repository.models.WikidataEntityResponse
import app.simple.felicity.repository.models.WikipediaPageSummary
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single source of truth for anything MusicBrainz-related.
 *
 * Given just an artist name, this repository walks through the MusicBrainz open database
 * to collect a rich profile: what type of act they are, where they are from, when they
 * were active, what genres the community tagged them with, and a short biography pulled
 * from their linked Wikipedia page.
 *
 * All network calls run on [Dispatchers.IO] — callers don't need to worry about
 * threading; just call the suspend functions from any coroutine context.
 *
 * @author Hamza417
 */
@Singleton
class MusicBrainzRepository @Inject constructor() {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val gson: Gson by lazy { Gson() }

    /**
     * Builds a complete [MusicBrainzArtistInfo] for the given artist name.
     *
     * The lookup goes through three APIs in order:
     * 1. MusicBrainz search — find the best-matching MBID.
     * 2. MusicBrainz artist detail — type, country, lifespan, tags, URL relations.
     * 3. Wikipedia/Wikidata — fetch the biography text via the artist's linked page.
     *
     * Returns null when MusicBrainz has no record for this artist name.
     *
     * @param artistName The display name of the artist.
     */
    suspend fun fetchArtistInfo(artistName: String): MusicBrainzArtistInfo? {
        return withContext(Dispatchers.IO) {
            if (artistName.isBlank()) return@withContext null

            val mbid = searchArtistMbid(artistName) ?: run {
                Log.d(TAG, "No MBID found for artist: $artistName")
                return@withContext null
            }
            Log.d(TAG, "Resolved MBID $mbid for artist: $artistName")

            val detail = fetchArtistDetail(mbid) ?: run {
                Log.d(TAG, "Could not fetch detail for MBID: $mbid")
                return@withContext null
            }

            // Resolve the Wikipedia URL — try a direct link first, then Wikidata.
            val wikipediaUrl = resolveWikipediaUrl(detail.relations)
            Log.d(TAG, "Wikipedia URL for $artistName: $wikipediaUrl")

            // Pull bio text from Wikipedia's page-summary endpoint if we have a URL.
            val bio = wikipediaUrl?.let { fetchWikipediaBio(it) }

            // Keep only the top 8 tags by vote count so the UI doesn't become a wall of text.
            val topTags = detail.tags
                ?.filter { !it.name.isNullOrBlank() }
                ?.sortedByDescending { it.count }
                ?.take(8)
                ?.mapNotNull { it.name }
                ?: emptyList()

            MusicBrainzArtistInfo(
                    name = detail.name ?: artistName,
                    disambiguation = detail.disambiguation?.takeIf { it.isNotBlank() },
                    type = detail.type?.takeIf { it.isNotBlank() },
                    country = detail.country?.takeIf { it.isNotBlank() },
                    beginYear = detail.lifeSpan?.begin?.take(4)?.takeIf { it.isNotBlank() },
                    endYear = detail.lifeSpan?.end?.take(4)?.takeIf { it.isNotBlank() },
                    ended = detail.lifeSpan?.ended ?: false,
                    tags = topTags,
                    bio = bio,
                    wikipediaUrl = wikipediaUrl
            )
        }
    }

    /**
     * Searches the MusicBrainz artist index by name and returns the MBID of the
     * best match. MusicBrainz returns results ranked by a confidence score, so the
     * first entry is almost always the right one.
     */
    private fun searchArtistMbid(artistName: String): String? {
        return try {
            val url = "https://musicbrainz.org/ws/2/artist/".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("query", "artist:$artistName")
                ?.addQueryParameter("limit", "1")
                ?.addQueryParameter("fmt", "json")
                ?.build() ?: return null

            get(url.toString())?.let { body ->
                gson.fromJson(body, MusicBrainzArtistSearchResponse::class.java)
                    .artists
                    ?.firstOrNull()
                    ?.id
            }
        } catch (e: Exception) {
            Log.w(TAG, "Artist MBID search failed for: $artistName", e)
            null
        }
    }

    /**
     * Fetches the full artist record from MusicBrainz, asking for tags and URL relations
     * in the same request so we only need one round-trip for all the metadata we want.
     */
    private fun fetchArtistDetail(mbid: String): MusicBrainzArtistDetail? {
        return try {
            val url = "https://musicbrainz.org/ws/2/artist/$mbid".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("inc", "tags+url-rels")
                ?.addQueryParameter("fmt", "json")
                ?.build() ?: return null

            get(url.toString())?.let { body ->
                gson.fromJson(body, MusicBrainzArtistDetail::class.java)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Artist detail fetch failed for MBID: $mbid", e)
            null
        }
    }

    /**
     * Looks at the artist's URL relations to find a path to Wikipedia.
     * First checks for a direct "wikipedia" link; falls back to resolving
     * a "wikidata" link through the Wikidata API when no direct link exists.
     */
    private fun resolveWikipediaUrl(relations: List<app.simple.felicity.repository.models.MusicBrainzUrlRelation>?): String? {
        if (relations == null) return null

        val direct = relations
            .firstOrNull { it.type?.lowercase() == "wikipedia" }
            ?.url?.resource
        if (direct != null) return direct

        val wikidataUrl = relations
            .firstOrNull { it.type?.lowercase() == "wikidata" }
            ?.url?.resource
        return wikidataUrl?.let { resolveWikidataToWikipedia(it) }
    }

    /**
     * Calls the Wikidata API with an entity URL (e.g. https://www.wikidata.org/wiki/Q16767221)
     * and returns the corresponding English Wikipedia page URL by reading the enwiki sitelink.
     */
    private fun resolveWikidataToWikipedia(wikidataUrl: String): String? {
        return try {
            val entityId = wikidataUrl.trimEnd('/').substringAfterLast('/')
            if (!entityId.startsWith('Q')) return null

            val url = "https://www.wikidata.org/w/api.php".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("action", "wbgetentities")
                ?.addQueryParameter("ids", entityId)
                ?.addQueryParameter("props", "sitelinks")
                ?.addQueryParameter("sitefilter", "enwiki")
                ?.addQueryParameter("format", "json")
                ?.build() ?: return null

            get(url.toString())?.let { body ->
                val parsed = gson.fromJson(body, WikidataEntityResponse::class.java)
                val title = parsed.entities
                    ?.get(entityId)
                    ?.sitelinks
                    ?.get("enwiki")
                    ?.title
                title?.let { "https://en.wikipedia.org/wiki/${it.replace(' ', '_')}" }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wikidata resolution failed for: $wikidataUrl", e)
            null
        }
    }

    /**
     * Calls the Wikipedia REST API page-summary endpoint and returns the introductory
     * paragraph (the `extract` field) as the artist's bio text.
     *
     * The extract is already plain text — no HTML stripping needed.
     */
    private fun fetchWikipediaBio(wikipediaUrl: String): String? {
        return try {
            val pageTitle = wikipediaUrl.trimEnd('/').substringAfterLast('/')
            if (pageTitle.isBlank()) return null

            val host = wikipediaUrl.toHttpUrlOrNull()?.host ?: "en.wikipedia.org"
            val summaryUrl = "https://$host/api/rest_v1/page/summary/$pageTitle"

            get(summaryUrl)?.let { body ->
                gson.fromJson(body, WikipediaPageSummary::class.java)
                    .extract
                    ?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wikipedia bio fetch failed for: $wikipediaUrl", e)
            null
        }
    }

    /**
     * A thin wrapper around OkHttp that attaches the required MusicBrainz User-Agent
     * to every outgoing request and returns the response body as a string.
     *
     * MusicBrainz aggressively rate-limits clients that don't send a proper User-Agent,
     * so this must never be skipped.
     *
     * @param url The full URL to fetch.
     * @return Response body string, or null on any HTTP or I/O error.
     */
    private fun get(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body.string() else {
                    Log.w(TAG, "HTTP ${response.code} for URL: $url")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network error for URL: $url", e)
            null
        }
    }

    companion object {
        private const val TAG = "MusicBrainzRepository"

        /**
         * MusicBrainz requires every API client to identify itself. Without this header
         * the server will return HTTP 429 (Too Many Requests) almost immediately.
         * See: https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting
         */
        private const val USER_AGENT = "Felicity/1.0 (https://github.com/Hamza417/Felicity)"
    }
}

