package app.simple.felicity.viewmodels.player

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.core.utils.FileUtils.toFile
import app.simple.felicity.decorations.lrc.model.LrcData
import app.simple.felicity.decorations.lrc.parser.LrcParser
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.managers.MediaManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
                lrcData.postValue(LrcData())
            }
        }
    }

    companion object {
        private const val TAG = "LyricsViewModel"
    }
}