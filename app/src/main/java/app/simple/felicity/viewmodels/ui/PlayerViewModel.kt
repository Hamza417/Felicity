package app.simple.felicity.viewmodels.ui

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.loaders.MediaLoader
import app.simple.felicity.models.Audio
import app.simple.felicity.preferences.MusicPreferences
import app.simple.felicity.utils.ArrayUtils.toArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : WrappedViewModel(application) {

    private val data: MutableLiveData<ArrayList<Audio>> by lazy {
        MutableLiveData<ArrayList<Audio>>().also {
            loadData()
        }
    }

    fun getSongs(): MutableLiveData<ArrayList<Audio>> {
        return data
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            data.postValue(MediaLoader.getCurrentMediaList(MusicPreferences.getMediaMusicCategory(), applicationContext())!!.toArrayList())
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            MusicPreferences.media_music_category -> {
                loadData()
            }
        }
    }
}