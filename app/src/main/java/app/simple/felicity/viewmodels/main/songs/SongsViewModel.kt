package app.simple.felicity.viewmodels.main.songs

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.normal.Audio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SongsViewModel(application: Application) : WrappedViewModel(application) {

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
            val audios = AudioDatabase.getInstance(applicationContext())?.audioDao()?.getAllAudio()
            songs.postValue(audios as ArrayList<Audio>?)
        }
    }
}
