package app.simple.felicity.repository.covers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import app.simple.felicity.repository.factories.TaggedSocketFactory
import app.simple.felicity.repository.models.MusicBrainzArtistDetail
import app.simple.felicity.repository.models.MusicBrainzArtistSearchResponse
import app.simple.felicity.repository.models.WikidataEntityResponse
import app.simple.felicity.repository.models.WikipediaPageSummary
import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches artist images by going through the MusicBrainz open database.
 *
 * MusicBrainz doesn't host artist photos itself, but it keeps track of each artist's
 * Wikipedia page. We take advantage of that by doing a three-step lookup:
 *
 * 1. Ask MusicBrainz to find the artist and give us their unique ID (MBID).
 * 2. Ask MusicBrainz for the artist's Wikipedia page URL using that ID.
 * 3. Ask the Wikipedia REST API for the page's thumbnail image and download it.
 *
 * This is the standard way to get artist images through the MusicBrainz ecosystem,
 * and it works without any API key.
 *
 * The MusicBrainz API policy requires a descriptive User-Agent header so their
 * servers can identify the application making the request.
 *
 * @author Hamza417
 */
internal object MusicBrainzArtistCover {

    private const val TAG = "MusicBrainzArtistCover"

    /**
     * MusicBrainz requires every API client to identify itself with a User-Agent
     * that includes the application name, version, and a contact URL or email.
     * Requests without a proper User-Agent are rate-limited very aggressively.
     *
     * See: https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting
     */
    private const val USER_AGENT = "FelicityMusicPlayer/1.0 (https://github.com/Hamza417/Felicity)"
    private const val NETWORK_TAG = 0x1002

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .socketFactory(TaggedSocketFactory(NETWORK_TAG))
            .build()
    }

    private val gson: Gson by lazy { Gson() }

    /**
     * Tries to download an artist's photo from MusicBrainz → Wikipedia.
     * Returns null if any step fails or the artist has no linked Wikipedia page.
     *
     * @param artistName The display name of the artist to look up.
     * @return A [Bitmap] of the artist's Wikipedia thumbnail, or null on any failure.
     */
    fun fetchArtistImage(artistName: String): Bitmap? {
        if (artistName.isBlank()) return null

        val musicBrainzID = searchArtistMbid(artistName) ?: return null
        Log.d(TAG, "Found MBID $musicBrainzID for artist: $artistName")

        val wikipediaUrl = fetchWikipediaUrl(musicBrainzID) ?: run {
            Log.d(TAG, "No Wikipedia relation found for MBID: $musicBrainzID")
            return null
        }
        Log.d(TAG, "Found Wikipedia URL for $artistName: $wikipediaUrl")

        val imageUrl = fetchWikipediaThumbnail(wikipediaUrl) ?: run {
            Log.d(TAG, "No thumbnail found on Wikipedia page: $wikipediaUrl")
            return null
        }
        Log.d(TAG, "Downloading artist image from: $imageUrl")

        return downloadBitmap(imageUrl)
    }

    /**
     * Searches the MusicBrainz API for an artist by name and returns the MBID
     * (MusicBrainz Identifier) of the best match.
     *
     * MusicBrainz returns results ordered by a confidence score, so we just
     * take the first one, which is almost always the correct artist.
     *
     * @param artistName The artist name to search for.
     * @return The MBID string (a UUID), or null if nothing was found.
     */
    private fun searchArtistMbid(artistName: String): String? {
        return try {
            val url = "https://musicbrainz.org/ws/2/artist/".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("query", "artist:${artistName}")
                ?.addQueryParameter("limit", "1")
                ?.addQueryParameter("fmt", "json")
                ?.build() ?: return null

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "MusicBrainz artist search failed: HTTP ${response.code}")
                    return null
                }

                val body = response.body.string()
                val parsed = gson.fromJson(body, MusicBrainzArtistSearchResponse::class.java)
                parsed.artists?.firstOrNull()?.id
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error searching MusicBrainz for artist: $artistName", e)
            null
        }
    }

    /**
     * Looks up a specific artist by MBID and finds a Wikipedia page URL by scanning
     * through the artist's URL relations.
     *
     * MusicBrainz stores external links under "relations". Many artists only have a
     * Wikidata link rather than a direct Wikipedia link, so we check both:
     * - If a "wikipedia" relation exists, we use it straight away.
     * - If only a "wikidata" relation exists, we resolve it via the Wikidata API to
     *   find the matching English Wikipedia article title, then build a Wikipedia URL.
     *
     * @param mbid The MusicBrainz Identifier of the artist.
     * @return The Wikipedia page URL string, or null if no path to Wikipedia is found.
     */
    private fun fetchWikipediaUrl(mbid: String): String? {
        return try {
            val url = "https://musicbrainz.org/ws/2/artist/$mbid".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("inc", "url-rels")
                ?.addQueryParameter("fmt", "json")
                ?.build() ?: return null

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "MusicBrainz artist detail fetch failed: HTTP ${response.code}")
                    return null
                }

                val body = response.body.string()
                val detail = gson.fromJson(body, MusicBrainzArtistDetail::class.java)
                val relations = detail.relations ?: return null

                // Direct Wikipedia link — fastest path, use it immediately.
                val directWikipedia = relations
                    .firstOrNull { it.type?.lowercase() == "wikipedia" }
                    ?.url?.resource
                if (directWikipedia != null) return directWikipedia

                // Many artists only have a Wikidata link. Resolve it to a Wikipedia URL.
                val wikidataUrl = relations
                    .firstOrNull { it.type?.lowercase() == "wikidata" }
                    ?.url?.resource
                if (wikidataUrl != null) {
                    return resolveWikidataToWikipedia(wikidataUrl)
                }

                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching MusicBrainz artist detail for MBID: $mbid", e)
            null
        }
    }

    /**
     * Takes a Wikidata entity URL (e.g. https://www.wikidata.org/wiki/Q16767221) and
     * returns the matching English Wikipedia page URL by asking the Wikidata API for
     * the entity's sitelinks.
     *
     * The Wikidata entity ID (the "Q" number) is extracted from the URL path, then we
     * hit the Wikidata wbgetentities endpoint with `props=sitelinks` and look for the
     * "enwiki" entry whose title we turn back into a Wikipedia URL.
     *
     * @param wikidataUrl The full Wikidata entity URL.
     * @return The English Wikipedia page URL, or null if no sitelink is available.
     */
    private fun resolveWikidataToWikipedia(wikidataUrl: String): String? {
        return try {
            // The entity ID sits at the end of the path, e.g. "Q16767221"
            val entityId = wikidataUrl.trimEnd('/').substringAfterLast('/')
            if (!entityId.startsWith('Q')) {
                Log.w(TAG, "Unexpected Wikidata URL format: $wikidataUrl")
                return null
            }

            val url = "https://www.wikidata.org/w/api.php".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("action", "wbgetentities")
                ?.addQueryParameter("ids", entityId)
                ?.addQueryParameter("props", "sitelinks")
                ?.addQueryParameter("sitefilter", "enwiki")
                ?.addQueryParameter("format", "json")
                ?.build() ?: return null

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Wikidata API call failed: HTTP ${response.code}")
                    return null
                }

                val body = response.body.string()
                val parsed = gson.fromJson(body, WikidataEntityResponse::class.java)
                val title = parsed.entities
                    ?.get(entityId)
                    ?.sitelinks
                    ?.get("enwiki")
                    ?.title

                if (title != null) {
                    // Turn the article title into a full Wikipedia URL
                    "https://en.wikipedia.org/wiki/${title.replace(' ', '_')}"
                } else {
                    Log.d(TAG, "No enwiki sitelink found for Wikidata entity: $entityId")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving Wikidata URL to Wikipedia: $wikidataUrl", e)
            null
        }
    }

    /**
     * Calls the Wikipedia REST API's page-summary endpoint to get the thumbnail
     * image URL for a given Wikipedia article.
     *
     * We derive the page title from the Wikipedia URL by taking the last path segment,
     * then hit the summary endpoint which gives us a compact JSON object containing
     * the thumbnail image URL if one exists.
     *
     * @param wikipediaUrl The full Wikipedia page URL (e.g. https://en.wikipedia.org/wiki/Adele).
     * @return A direct image URL string, or null if no thumbnail is available.
     */
    private fun fetchWikipediaThumbnail(wikipediaUrl: String): String? {
        return try {
            // Extract the page title from the URL path, e.g. "Adele" from ".../wiki/Adele"
            val pageTitle = wikipediaUrl.trimEnd('/').substringAfterLast('/')
            if (pageTitle.isBlank()) return null

            // Build the Wikipedia REST summary URL. We pick the language from the host
            // (e.g. "en" from "en.wikipedia.org") so non-English pages also work.
            val host = wikipediaUrl.toHttpUrlOrNull()?.host ?: "en.wikipedia.org"
            val summaryUrl = "https://$host/api/rest_v1/page/summary/$pageTitle"
                .toHttpUrlOrNull() ?: return null

            val request = Request.Builder()
                .url(summaryUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Wikipedia summary fetch failed: HTTP ${response.code}")
                    return null
                }

                val body = response.body.string()
                val summary = gson.fromJson(body, WikipediaPageSummary::class.java)
                summary.thumbnail?.source
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching Wikipedia thumbnail for: $wikipediaUrl", e)
            null
        }
    }

    /**
     * Downloads a raw image from the given URL and decodes it into a [Bitmap].
     *
     * @param imageUrl The direct URL to the image file.
     * @return A decoded [Bitmap], or null if the download or decoding fails.
     */
    private fun downloadBitmap(imageUrl: String): Bitmap? {
        return try {
            val url = imageUrl.toHttpUrlOrNull() ?: return null

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Image download failed: HTTP ${response.code}")
                    return null
                }

                val bytes = response.body.bytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error downloading image from: $imageUrl", e)
            null
        }
    }
}

