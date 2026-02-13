package app.simple.felicity.viewmodels.player

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.core.utils.FileUtils.toFile
import app.simple.felicity.decorations.lrc.model.LrcData
import app.simple.felicity.decorations.lrc.parser.LrcParser
import app.simple.felicity.decorations.lrc.parser.LyricsParseException
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.LrcLibResponse
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class LyricsViewModel @Inject constructor(application: Application) : WrappedViewModel(application) {

    private val lrcData: MutableLiveData<LrcData> by lazy {
        MutableLiveData<LrcData>().also {
            loadLrcData()
        }
    }

    fun getLrcData(): LiveData<LrcData> {
        return lrcData
    }

    fun loadLrcData() {
        viewModelScope.launch(Dispatchers.IO) { // Use IO for file operations
            val song = MediaManager.getCurrentSong()?.path?.substringBeforeLast(".")
            val lrcFile = song.plus(".lrc").toFile()
            if (lrcFile.exists()) {
                val lrcDataLoaded = LrcParser().parse(lrcFile.readText())
                lrcData.postValue(lrcDataLoaded)
            } else {
                try {
                    LrcParser().parse(fetchLrcBySearch(
                            trackName = MediaManager.getCurrentSong()?.title ?: "",
                            artistName = MediaManager.getCurrentSong()?.artist ?: "",
                    ) ?: "").also {
                        lrcData.postValue(it)
                        writeLrcToFile(it.toString(), lrcFile.absolutePath) // Cache for future use
                    }
                } catch (e: LyricsParseException) {
                    e.printStackTrace()
                    lrcData.postValue(LrcData()) // Post null on error
                }
            }
        }
    }

    private val client = OkHttpClient()
    private val gson = Gson()

    /**
     * Fetches the synced lyrics (.lrc) for a specific track.
     * Returns the LRC string content if found, or null if not found/error.
     *
     * @param trackName The title of the song.
     * @param artistName The artist's name.
     */
    suspend fun fetchLrcBySearch(
            trackName: String,
            artistName: String
    ): String? {
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
                    .header("User-Agent", "LrcFetchBot/1.0") // Recommended by docs [cite: 10]
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null

                    val responseBody = response.body?.string() ?: return@use null

                    val listType = object : TypeToken<List<LrcLibResponse>>() {}.type
                    val results: List<LrcLibResponse> = gson.fromJson(responseBody, listType)
                    val bestMatch = results.firstOrNull { !it.syncedLyrics.isNullOrBlank() }

                    return@use bestMatch?.syncedLyrics
                }
            } catch (e: IOException) {
                e.printStackTrace()
                return@withContext null
            }
        }
    }

    suspend fun writeLrcToFile(lrcContent: String, outputPath: String) {
        withContext(Dispatchers.IO) {
            try {
                outputPath.toFile().writeText(lrcContent)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        private const val TAG = "LyricsViewModel"
    }
}