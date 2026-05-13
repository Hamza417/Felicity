package app.simple.felicity.repository.repositories

import android.content.Context
import android.util.Log
import app.simple.felicity.repository.database.dao.AlbumInfoCacheDao
import app.simple.felicity.repository.database.dao.ArtistInfoCacheDao
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.factories.TaggedSocketFactory
import app.simple.felicity.repository.models.MusicBrainzAlbumInfo
import app.simple.felicity.repository.models.MusicBrainzArtistDetail
import app.simple.felicity.repository.models.MusicBrainzArtistInfo
import app.simple.felicity.repository.models.MusicBrainzArtistSearchResponse
import app.simple.felicity.repository.models.MusicBrainzRecordingResult
import app.simple.felicity.repository.models.MusicBrainzRecordingSearchResponse
import app.simple.felicity.repository.models.MusicBrainzReleaseDetail
import app.simple.felicity.repository.models.MusicBrainzReleaseSearchResponse
import app.simple.felicity.repository.models.WikidataEntityResponse
import app.simple.felicity.repository.models.WikipediaPageSummary
import app.simple.felicity.repository.repositories.MusicBrainzRepository.Companion.CACHE_TTL_MS
import app.simple.felicity.shared.constants.AppConstants
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
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
@Suppress("SpellCheckingInspection")
@Singleton
class MusicBrainzRepository @Inject constructor(
        @param:ApplicationContext private val context: Context
) {

    // Define a unique tag for your MusicBrainz/Network requests
    private val MUSIC_NETWORK_TAG = 0x1001

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .socketFactory(TaggedSocketFactory(MUSIC_NETWORK_TAG))
            .build()
    }

    private val gson: Gson by lazy { Gson() }

    /**
     * Searches MusicBrainz for recordings (individual track performances) that match the
     * given title and artist. Returns up to 15 results ranked by relevance score.
     *
     * Unlike the artist/album fetch functions this one does not cache results — search
     * results are cheap to re-fetch and the user expects fresh matches every time.
     */
    suspend fun searchRecordings(trackName: String, artistName: String): List<MusicBrainzRecordingResult> {
        return withContext(Dispatchers.IO) {
            if (trackName.isBlank()) return@withContext emptyList()
            try {
                val query = buildString {
                    append("recording:\"$trackName\"")
                    if (artistName.isNotBlank()) append(" AND artist:\"$artistName\"")
                }
                val url = "https://musicbrainz.org/ws/2/recording/".toHttpUrlOrNull()
                    ?.newBuilder()
                    ?.addQueryParameter("query", query)
                    ?.addQueryParameter("limit", "15")
                    ?.addQueryParameter("fmt", "json")
                    ?.build() ?: return@withContext emptyList()

                val body = get(url.toString()) ?: return@withContext emptyList()
                gson.fromJson(body, MusicBrainzRecordingSearchResponse::class.java)
                    .recordings ?: emptyList()
            } catch (e: Exception) {
                Log.w(TAG, "Recording search failed for: $trackName", e)
                emptyList()
            }
        }
    }

    /**
     *
     * Cache-first strategy:
     * 1. If the local Room cache has a fresh row (younger than [CACHE_TTL_MS]), return it instantly.
     * 2. Otherwise, hit the MusicBrainz + Wikipedia APIs, save the result, and return it.
     *
     * Returns null when MusicBrainz has no record for this artist name.
     */
    suspend fun fetchArtistInfo(artistName: String): MusicBrainzArtistInfo? {
        return withContext(Dispatchers.IO) {
            if (artistName.isBlank()) return@withContext null

            val dao = dao()

            val cached = dao?.getByArtistName(artistName)
            if (cached != null && !isCacheStale(cached.fetchedAt)) {
                Log.d(TAG, "Cache hit for artist: $artistName (age ${System.currentTimeMillis() - cached.fetchedAt} ms)")
                return@withContext cached
            }

            val fetched = fetchFromNetwork(artistName)
            if (fetched != null) {
                dao?.upsert(fetched)
                Log.d(TAG, "Cached MusicBrainz info for: $artistName")
            }
            fetched
        }
    }

    private fun isCacheStale(fetchedAt: Long) =
        System.currentTimeMillis() - fetchedAt > CACHE_TTL_MS

    private fun dao(): ArtistInfoCacheDao? = try {
        AudioDatabase.getInstance(context).artistInfoCacheDao()
    } catch (e: Exception) {
        Log.w(TAG, "Could not access ArtistInfoCacheDao", e)
        null
    }

    private fun albumDao(): AlbumInfoCacheDao? = try {
        AudioDatabase.getInstance(context).albumInfoCacheDao()
    } catch (e: Exception) {
        Log.w(TAG, "Could not access AlbumInfoCacheDao", e)
        null
    }

    /**
     * Returns a complete [MusicBrainzAlbumInfo] for the given album and artist name.
     *
     * Uses the same cache-first strategy as [fetchArtistInfo]: if a fresh cached row
     * exists it is returned immediately, otherwise the MusicBrainz and Wikipedia APIs
     * are queried and the result is saved locally.
     *
     * Returns null when MusicBrainz has no matching release.
     */
    suspend fun fetchAlbumInfo(albumName: String, artistName: String): MusicBrainzAlbumInfo? {
        return withContext(Dispatchers.IO) {
            if (albumName.isBlank()) return@withContext null

            val key = "$albumName${KEY_SEPARATOR}$artistName"
            val dao = albumDao()

            val cached = dao?.getByAlbumKey(key)
            if (cached != null && !isCacheStale(cached.fetchedAt)) {
                Log.d(TAG, "Cache hit for album: $albumName by $artistName")
                return@withContext cached
            }

            val fetched = fetchAlbumFromNetwork(albumName, artistName, key)
            if (fetched != null) {
                dao?.upsert(fetched)
                Log.d(TAG, "Cached MusicBrainz info for album: $albumName")
            }
            fetched
        }
    }

    private fun fetchAlbumFromNetwork(albumName: String, artistName: String, key: String): MusicBrainzAlbumInfo? {
        val mbid = searchReleaseMbid(albumName, artistName) ?: run {
            Log.d(TAG, "No MBID found for album: $albumName by $artistName")
            return null
        }
        Log.d(TAG, "Resolved release MBID $mbid for album: $albumName")

        val detail = fetchReleaseDetail(mbid) ?: run {
            Log.d(TAG, "Could not fetch release detail for MBID: $mbid")
            return null
        }

        val wikipediaUrl = resolveWikipediaUrl(detail.relations)
        Log.d(TAG, "Wikipedia URL for album $albumName: $wikipediaUrl")

        val bio = wikipediaUrl?.let { fetchWikipediaBio(it) }

        val topTags = detail.tags
            ?.filter { !it.name.isNullOrBlank() }
            ?.sortedByDescending { it.count }
            ?.take(8)
            ?.mapNotNull { it.name }
            ?: emptyList()

        val labels = detail.labelInfo
            ?.mapNotNull { it.label?.name?.takeIf { n -> n.isNotBlank() } }
            ?.distinct()
            ?.take(4)
            ?: emptyList()

        return MusicBrainzAlbumInfo(
                albumKey = key,
                mbid = mbid,
                disambiguation = detail.disambiguation?.takeIf { it.isNotBlank() },
                releaseDate = detail.date?.takeIf { it.isNotBlank() },
                country = detail.country?.takeIf { it.isNotBlank() },
                status = detail.status?.takeIf { it.isNotBlank() },
                tags = topTags.joinToString(TAG_SEPARATOR),
                labels = labels.joinToString(TAG_SEPARATOR),
                bio = bio,
                wikipediaUrl = wikipediaUrl,
                fetchedAt = System.currentTimeMillis()
        )
    }

    private fun searchReleaseMbid(albumName: String, artistName: String): String? {
        return try {
            val query = buildString {
                append("release:\"$albumName\"")
                if (artistName.isNotBlank()) append(" AND artist:\"$artistName\"")
            }
            val url = "https://musicbrainz.org/ws/2/release/".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("query", query)
                ?.addQueryParameter("limit", "1")
                ?.addQueryParameter("fmt", "json")
                ?.build() ?: return null

            get(url.toString())?.let { body ->
                gson.fromJson(body, MusicBrainzReleaseSearchResponse::class.java)
                    .releases?.firstOrNull()?.id
            }
        } catch (e: Exception) {
            Log.w(TAG, "Release MBID search failed for: $albumName", e)
            null
        }
    }

    private fun fetchReleaseDetail(mbid: String): MusicBrainzReleaseDetail? {
        return try {
            val url = "https://musicbrainz.org/ws/2/release/$mbid".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("inc", "tags+url-rels+label-rels+labels")
                ?.addQueryParameter("fmt", "json")
                ?.build() ?: return null

            get(url.toString())?.let { body ->
                gson.fromJson(body, MusicBrainzReleaseDetail::class.java)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Release detail fetch failed for MBID: $mbid", e)
            null
        }
    }

    /**
     * Does the actual three-step network fetch:
     * MusicBrainz search → artist detail with tags → Wikipedia bio.
     */
    private fun fetchFromNetwork(artistName: String): MusicBrainzArtistInfo? {
        val mbid = searchArtistMbid(artistName) ?: run {
            Log.d(TAG, "No MBID found for artist: $artistName")
            return null
        }
        Log.d(TAG, "Resolved MBID $mbid for artist: $artistName")

        val detail = fetchArtistDetail(mbid) ?: run {
            Log.d(TAG, "Could not fetch detail for MBID: $mbid")
            return null
        }

        val wikipediaUrl = resolveWikipediaUrl(detail.relations)
        Log.d(TAG, "Wikipedia URL for $artistName: $wikipediaUrl")

        val bio = wikipediaUrl?.let { fetchWikipediaBio(it) }

        val topTags = detail.tags
            ?.filter { !it.name.isNullOrBlank() }
            ?.sortedByDescending { it.count }
            ?.take(8)
            ?.mapNotNull { it.name }
            ?: emptyList()

        return MusicBrainzArtistInfo(
                artistName = artistName,
                mbid = mbid,
                disambiguation = detail.disambiguation?.takeIf { it.isNotBlank() },
                type = detail.type?.takeIf { it.isNotBlank() },
                country = detail.country?.takeIf { it.isNotBlank() },
                beginYear = detail.lifeSpan?.begin?.take(4)?.takeIf { it.isNotBlank() },
                endYear = detail.lifeSpan?.end?.take(4)?.takeIf { it.isNotBlank() },
                ended = detail.lifeSpan?.ended ?: false,
                tags = topTags.joinToString(TAG_SEPARATOR),
                bio = bio,
                wikipediaUrl = wikipediaUrl,
                fetchedAt = System.currentTimeMillis()
        )
    }

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
                    .artists?.firstOrNull()?.id
            }
        } catch (e: Exception) {
            Log.w(TAG, "Artist MBID search failed for: $artistName", e)
            null
        }
    }

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

    private fun resolveWikipediaUrl(relations: List<app.simple.felicity.repository.models.MusicBrainzUrlRelation>?): String? {
        if (relations == null) return null
        val direct = relations.firstOrNull { it.type?.lowercase() == "wikipedia" }?.url?.resource
        if (direct != null) return direct
        val wikidataUrl = relations.firstOrNull { it.type?.lowercase() == "wikidata" }?.url?.resource
        return wikidataUrl?.let { resolveWikidataToWikipedia(it) }
    }

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
                val title = parsed.entities?.get(entityId)?.sitelinks?.get("enwiki")?.title
                title?.let { "https://en.wikipedia.org/wiki/${it.replace(' ', '_')}" }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wikidata resolution failed for: $wikidataUrl", e)
            null
        }
    }

    private fun fetchWikipediaBio(wikipediaUrl: String): String? {
        return try {
            val pageTitle = wikipediaUrl.trimEnd('/').substringAfterLast('/')
            if (pageTitle.isBlank()) return null
            val host = wikipediaUrl.toHttpUrlOrNull()?.host ?: "en.wikipedia.org"
            get("https://$host/api/rest_v1/page/summary/$pageTitle")?.let { body ->
                gson.fromJson(body, WikipediaPageSummary::class.java).extract?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wikipedia bio fetch failed for: $wikipediaUrl", e)
            null
        }
    }

    private fun get(url: String): String? {
        return try {
            val request = Request.Builder().url(url).header("User-Agent", AppConstants.MUSIC_BRAINZ_USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body.string()
                else {
                    Log.w(TAG, "HTTP ${response.code} for URL: $url"); null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network error for URL: $url", e)
            null
        }
    }

    companion object {
        private const val TAG = "MusicBrainzRepository"

        /** Cache entries older than 30 days are considered stale and will be re-fetched. */
        private const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000

        /** Separator used when joining/splitting the tags list in the database column. */
        private const val TAG_SEPARATOR = "|"

        /** Separator used to build the composite album cache key from album name and artist name. */
        private const val KEY_SEPARATOR = "|"
    }
}
