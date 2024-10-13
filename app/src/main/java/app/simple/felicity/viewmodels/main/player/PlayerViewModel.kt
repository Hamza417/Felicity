package app.simple.felicity.viewmodels.main.player

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.normal.Audio
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

        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
    }
}
