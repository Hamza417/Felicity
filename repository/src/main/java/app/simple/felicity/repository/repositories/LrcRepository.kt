package app.simple.felicity.repository.repositories

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.core.net.toUri
import app.simple.felicity.repository.metadata.LyricsMetaHelper
import app.simple.felicity.repository.models.LrcLibResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one-stop shop for anything lyrics-related: fetching them from the internet,
 * saving them to disk, and loading them back when the song plays.
 *
 * Because audio tracks are now addressed by content URIs (SAF), we can't just
 * slap a ".lrc" extension on the file path and call it a day — content URIs are
 * not real file-system paths. Instead, we store every sidecar file in the app's
 * private storage under a folder called "lyrics", using a Base64-encoded version
 * of the audio URI as the filename. That keeps things fast, avoids any permission
 * headaches, and means the lyrics stay around even if the user moves the audio file.
 *
 * @author Hamza417
 */
@Singleton
class LrcRepository @Inject constructor(
        @param:ApplicationContext private val context: Context
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * The folder inside the app's private storage where all LRC and TXT sidecar
     * files live. It is created lazily the first time we need to write something.
     */
    private val lyricsDir: File by lazy {
        File(context.filesDir, "lyrics").also { it.mkdirs() }
    }

    /**
     * Turns an audio URI (which can be a long, slash-heavy content URI) into a
     * safe filename by Base64-encoding it. We use URL_SAFE to avoid '+' and '/',
     * and NO_PADDING to drop trailing '=' signs that some file systems dislike.
     */
    private fun uriToFileName(audioUri: String, extension: String): String {
        val encoded = Base64.encodeToString(
                audioUri.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        return "$encoded.$extension"
    }

    /**
     * Search for available lyrics for a track.
     * Returns a list of all available lyrics matches from LrcLib.
     *
     * @param trackName The title of the song.
     * @param artistName The artist's name.
     * @return List of [LrcLibResponse] objects containing available lyrics, or empty list if none found.
     */
    suspend fun searchLyrics(
            trackName: String,
            artistName: String
    ): Result<List<LrcLibResponse>> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = HttpUrl.Builder()
                    .scheme("https")
                    .host("lrclib.net")
                    .addPathSegment("api")
                    .addPathSegment("search")
                    .addQueryParameter("track_name", trackName)
                    .addQueryParameter("artist_name", artistName)

                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .header("User-Agent", USER_AGENT)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                                IOException("Failed to search lyrics: ${response.code}")
                        )
                    }

                    val responseBody = response.body?.string()
                        ?: return@withContext Result.failure(
                                IOException("Empty response body")
                        )

                    val listType = object : TypeToken<List<LrcLibResponse>>() {}.type
                    val results: List<LrcLibResponse> = gson.fromJson(responseBody, listType)

                    // Keep only entries that actually have synced lyrics — plain text is handled separately.
                    val filteredResults = results.filter { !it.syncedLyrics.isNullOrBlank() }
                    return@withContext Result.success(filteredResults)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching lyrics", e)
                return@withContext Result.failure(e)
            }
        }
    }

    /**
     * Fetch specific lyrics by ID.
     *
     * @param lrcId The ID of the lyrics entry on LrcLib.
     * @return The [LrcLibResponse] object, or null if not found.
     */
    suspend fun fetchLyricsById(lrcId: Int): Result<LrcLibResponse?> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = HttpUrl.Builder()
                    .scheme("https")
                    .host("lrclib.net")
                    .addPathSegment("api")
                    .addPathSegment("get")
                    .addPathSegment(lrcId.toString())

                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .header("User-Agent", USER_AGENT)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                                IOException("Failed to fetch lyrics: ${response.code}")
                        )
                    }

                    val responseBody = response.body?.string()
                        ?: return@withContext Result.failure(
                                IOException("Empty response body")
                        )

                    val result: LrcLibResponse = gson.fromJson(responseBody, LrcLibResponse::class.java)
                    return@withContext Result.success(result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching lyrics by ID", e)
                return@withContext Result.failure(e)
            }
        }
    }

    /**
     * Save LRC content as a sidecar file in the app's private lyrics folder.
     * The file is named after a Base64 encoding of the audio URI so that moving
     * the audio file somewhere else doesn't orphan its lyrics.
     *
     * @param lrcContent The LRC content to save.
     * @param audioUri The content URI (or path) of the audio file.
     * @return [Result] wrapping the [File] we wrote to on success, or the exception on failure.
     */
    suspend fun saveLrcToFile(lrcContent: String, audioUri: String): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val lrcFile = File(lyricsDir, uriToFileName(audioUri, "lrc"))
                lrcFile.writeText(lrcContent)
                Log.d(TAG, "LRC saved successfully to internal storage: ${lrcFile.name}")
                return@withContext Result.success(lrcFile)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving LRC for URI: $audioUri", e)
                return@withContext Result.failure(e)
            }
        }
    }

    /**
     * Save plain-text lyrics as a TXT sidecar file in the app's private lyrics folder.
     *
     * @param textContent The plain lyrics content to save.
     * @param audioUri The content URI (or path) of the audio file.
     * @return [Result] wrapping the [File] we wrote to on success, or the exception on failure.
     */
    suspend fun saveTxtToFile(textContent: String, audioUri: String): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val txtFile = File(lyricsDir, uriToFileName(audioUri, "txt"))
                txtFile.writeText(textContent)
                Log.d(TAG, "TXT lyrics saved successfully to internal storage: ${txtFile.name}")
                return@withContext Result.success(txtFile)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving TXT lyrics for URI: $audioUri", e)
                return@withContext Result.failure(e)
            }
        }
    }

    /**
     * Load LRC content for the given audio URI. Checks three places in order:
     *
     * 1. Our app-private internal storage (where we save downloaded/edited LRC files).
     * 2. A sibling `.lrc` file sitting next to the audio file inside the same SAF tree
     *    (the user may have placed it there manually — we try to open it via the content
     *    resolver by swapping the audio extension for ".lrc" in the URI).
     * 3. Lyrics embedded directly in the audio file's tags as a last resort.
     *
     * @param audioUri The content URI (or path) of the audio file.
     * @return [Result] wrapping the LRC string if found, null if none exists, or
     *         the exception if something went wrong.
     */
    suspend fun loadLrcFromFile(audioUri: String): Result<String?> {
        return withContext(Dispatchers.IO) {
            try {
                // Check 1: our internal private store — fastest path for anything we've saved before.
                val lrcFile = File(lyricsDir, uriToFileName(audioUri, "lrc"))
                if (lrcFile.exists()) {
                    return@withContext Result.success(lrcFile.readText())
                }

                // Check 2: sibling .lrc file in the SAF tree. This lets users drop an .lrc
                // file next to their music and have it picked up automatically — very handy!
                // We build the sibling URI by replacing the audio extension with ".lrc".
                if (audioUri.startsWith("content://")) {
                    val siblingUri = audioUri.substringBeforeLast('.') + ".lrc"
                    try {
                        context.contentResolver.openInputStream(siblingUri.toUri())?.use { stream ->
                            val content = stream.bufferedReader().readText()
                            if (content.isNotBlank()) {
                                return@withContext Result.success(content)
                            }
                        }
                    } catch (_: Exception) {
                        // The sibling does not exist or isn't accessible — totally normal, keep going.
                        Log.d(TAG, "No sibling .lrc found in SAF tree for: $audioUri")
                    }
                }

                // Check 3: lyrics baked right into the audio file's tags (USLT, ©lyr, etc.).
                // If we find them we also write a sidecar so the next play skips this scan entirely.
                val embedded = LyricsMetaHelper.extractEmbeddedLyrics(context, audioUri)
                if (!embedded.isNullOrBlank()) {
                    // Save as a .lrc sidecar so future loads hit Check 1 and return instantly.
                    try {
                        val lrcFile = File(lyricsDir, uriToFileName(audioUri, "lrc"))
                        lrcFile.writeText(embedded)
                        Log.d(TAG, "Embedded lyrics cached as sidecar for: $audioUri")
                    } catch (cacheEx: Exception) {
                        // Caching is best-effort — not finding it next time is a minor annoyance,
                        // not a catastrophe. We still return the embedded lyrics right now.
                        Log.w(TAG, "Could not cache embedded lyrics to disk: ${cacheEx.message}")
                    }
                    return@withContext Result.success(embedded)
                }

                return@withContext Result.success(null)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading LRC for URI: $audioUri", e)
                return@withContext Result.failure(e)
            }
        }
    }

    /**
     * Load plain-text lyrics from the TXT sidecar file for the given audio URI.
     *
     * @param audioUri The content URI (or path) of the audio file.
     * @return [Result] wrapping the plain-text string if found, null if none exists.
     */
    suspend fun loadTxtFromFile(audioUri: String): Result<String?> {
        return withContext(Dispatchers.IO) {
            try {
                val txtFile = File(lyricsDir, uriToFileName(audioUri, "txt"))
                if (txtFile.exists()) {
                    Result.success(txtFile.readText())
                } else {
                    Result.success(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading TXT lyrics for URI: $audioUri", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Check if an LRC sidecar file already exists for the given audio URI.
     *
     * @param audioUri The content URI (or path) of the audio file.
     * @return true if we have a saved LRC file for this URI, false otherwise.
     */
    fun lrcFileExists(audioUri: String): Boolean {
        return File(lyricsDir, uriToFileName(audioUri, "lrc")).exists()
    }

    /**
     * Delete both the LRC and TXT sidecar files for the given audio URI.
     * Quietly ignores the case where a file does not exist — there's nothing to delete.
     *
     * @param audioUri The content URI (or path) of the audio file.
     */
    fun deleteLrcFile(audioUri: String) {
        val lrcFile = File(lyricsDir, uriToFileName(audioUri, "lrc"))
        if (lrcFile.exists()) {
            if (lrcFile.delete()) {
                Log.d(TAG, "LRC sidecar deleted for URI: $audioUri")
            } else {
                Log.e(TAG, "Failed to delete LRC sidecar for URI: $audioUri")
            }
        }

        val txtFile = File(lyricsDir, uriToFileName(audioUri, "txt"))
        if (txtFile.exists()) {
            if (txtFile.delete()) {
                Log.d(TAG, "TXT sidecar deleted for URI: $audioUri")
            } else {
                Log.e(TAG, "Failed to delete TXT sidecar for URI: $audioUri")
            }
        }
    }

    companion object {
        private const val TAG = "LrcRepository"
        private const val USER_AGENT = "Felicity Music Player (https://github.com/Hamza417/Felicity)"
        private const val LYRICS_DIR = "lyrics"

        /**
         * Deletes any internally-stored LRC and TXT sidecar files for the given
         * [audioUri] without needing a Hilt-injected instance. Useful from places
         * like a delete confirmation flow where you just want to clean up sidecar
         * files on the fly without wiring up the full repository.
         *
         * @param context  Any context — we only need [Context.filesDir] from it.
         * @param audioUri The content URI (or path) of the audio file.
         */
        fun deleteSidecarsStatic(context: Context, audioUri: String) {
            val lyricsDir = File(context.filesDir, LYRICS_DIR)
            if (!lyricsDir.exists()) return
            val encoded = Base64.encodeToString(
                    audioUri.toByteArray(Charsets.UTF_8),
                    Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            File(lyricsDir, "$encoded.lrc").delete()
            File(lyricsDir, "$encoded.txt").delete()
        }
    }
}

