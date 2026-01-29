package app.simple.felicity.viewmodels.main.player

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.core.utils.FileUtils.toFile
import app.simple.felicity.decorations.lrc.model.LrcData
import app.simple.felicity.decorations.lrc.parser.LrcParser
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.LrcFile
import app.simple.felicity.repository.models.Song
import app.simple.felicity.repository.repositories.LrcRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

@HiltViewModel
class LyricsViewModel @Inject constructor(
        application: Application,
        private val lrcRepository: LrcRepository
) : WrappedViewModel(application) {

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
            val lrcs: List<LrcFile> = lrcRepository.scanAllPersistedTrees()
            val songPath = MediaManager.getCurrentSong()?.path?.toFile()?.name?.substringBeforeLast(".").plus(".lrc")

            lrcs.forEach { lrcFile ->
                Log.d(TAG, "Found LRC File: ${lrcFile.name} at ${lrcFile.uri}")
                if (lrcFile.name == songPath) {
                    if (isSameDirectory(MediaManager.getCurrentSong()!!, lrcFile.parentId)) {
                        val lrcContent = readLrcFromUri(getApplication(), lrcFile.uri)
                        if (lrcContent != null) {
                            val lrcDataParsed = LrcParser().parse(lrcContent)
                            lrcData.postValue(lrcDataParsed)
                            return@launch
                        }
                    }
                }
            }
        }
    }

    fun readLrcFromUri(context: Context, uri: Uri): String? {
        val stringBuilder = StringBuilder()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stringBuilder.append(line).append('\n')
                        line = reader.readLine()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        return stringBuilder.toString()
    }

    private fun isSameDirectory(song: Song, lrcParentId: String): Boolean {
        // Convert "/storage/emulated/0/Music/Russian/song.mp3"
        // to "Music/Russian"
        val songRelativePath = song.path
            .substringAfter("/storage/emulated/0/") // Remove internal root
            .substringBeforeLast("/")               // Remove filename

        // Convert "primary:Music/Russian"
        // to "Music/Russian"
        val lrcRelativePath = lrcParentId
            .substringAfter(":") // Remove "primary" or SD card ID

        return songRelativePath.equals(lrcRelativePath, ignoreCase = true)
    }

    companion object {
        private const val TAG = "LyricsViewModel"
    }
}