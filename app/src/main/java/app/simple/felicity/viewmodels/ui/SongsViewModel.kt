package app.simple.felicity.viewmodels.ui

import android.app.Application
import android.database.Cursor
import android.provider.MediaStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.loaders.MediaLoader.loadAudios
import app.simple.felicity.models.normal.Audio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SongsViewModel(application: Application) : WrappedViewModel(application) {

    private var cursor: Cursor? = null
    private val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0"

    private val songs: MutableLiveData<ArrayList<Audio>> by lazy {
        MutableLiveData<ArrayList<Audio>>().also {
            loadData()
        }
    }

    fun getSongs(): LiveData<ArrayList<Audio>> {
        return songs
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            songs.postValue(applicationContext().loadAudios())
        }
    }
}
