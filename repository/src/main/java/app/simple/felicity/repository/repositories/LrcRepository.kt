package app.simple.felicity.repository.repositories

import android.util.Log
import app.simple.felicity.repository.models.LrcLibResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
 * Repository for handling LRC lyrics fetching and storage.
 * Provides methods to search for lyrics, fetch specific lyrics, and save them to storage.
 */
@Singleton
class LrcRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Search for available lyrics for a track.
     * Returns a list of all available lyrics matches from LrcLib.
     *
     * @param trackName The title of the song.
     * @param artistName The artist's name.
     * @return List of LrcLibResponse objects containing available lyrics, or empty list if none found.
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

                    // Filter out results without synced lyrics
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
     * This can be used to get a specific lyrics entry from LrcLib.
     *
     * @param lrcId The ID of the lyrics to fetch.
     * @return The LrcLibResponse object, or null if not found.
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
     * Save LRC content to a file as a sidecar file next to the audio file.
     *
     * @param lrcContent The LRC content to save.
     * @param audioFilePath The path to the audio file.
     * @return Result indicating success or failure.
     */
    suspend fun saveLrcToFile(lrcContent: String, audioFilePath: String): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                // Create .lrc file path by replacing audio extension with .lrc
                val lrcFilePath = audioFilePath.substringBeforeLast(".") + ".lrc"
                val lrcFile = File(lrcFilePath)

                // Write the LRC content to file
                lrcFile.writeText(lrcContent)

                Log.d(TAG, "LRC saved successfully: ${lrcFile.absolutePath}")
                return@withContext Result.success(lrcFile)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving LRC to file", e)
                return@withContext Result.failure(e)
            }
        }
    }

    /**
     * Load LRC content from a sidecar file.
     *
     * @param audioFilePath The path to the audio file.
     * @return The LRC content as a string, or null if the file doesn't exist.
     */
    suspend fun loadLrcFromFile(audioFilePath: String): Result<String?> {
        return withContext(Dispatchers.IO) {
            try {
                val lrcFilePath = audioFilePath.substringBeforeLast(".") + ".lrc"
                val lrcFile = File(lrcFilePath)

                if (lrcFile.exists()) {
                    val content = lrcFile.readText()
                    return@withContext Result.success(content)
                } else {
                    return@withContext Result.success(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading LRC from file", e)
                return@withContext Result.failure(e)
            }
        }
    }

    /**
     * Check if an LRC file exists for the given audio file.
     *
     * @param audioFilePath The path to the audio file.
     * @return True if an LRC file exists, false otherwise.
     */
    fun lrcFileExists(audioFilePath: String): Boolean {
        val lrcFilePath = audioFilePath.substringBeforeLast(".") + ".lrc"
        return File(lrcFilePath).exists()
    }

    companion object {
        private const val TAG = "LrcRepository"
        private const val USER_AGENT = "Felicity Music Player (https://github.com/Hamza417/Felicity)"
    }
}

