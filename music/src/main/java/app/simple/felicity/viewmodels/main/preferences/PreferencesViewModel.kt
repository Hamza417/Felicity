package app.simple.felicity.viewmodels.main.preferences

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PreferencesViewModel(application: Application) : WrappedViewModel(application) {

    private val preference: MutableLiveData<List<Preference>> by lazy {
        MutableLiveData<List<Preference>>().also {
            loadPreferences()
        }
    }

    fun getPreferences(): LiveData<List<Preference>> {
        return preference
    }

    private fun loadPreferences() {
        viewModelScope.launch(Dispatchers.Default) {
            val preferences = listOf(
                    Preference(
                            title = R.string.appearance,
                            description = R.string.appearance_desc,
                            icon = R.drawable.ic_water_drop
                    ),
                    Preference(
                            title = R.string.user_interface,
                            description = R.string.user_interface_desc,
                            icon = R.drawable.ic_carousel
                    ),
                    Preference(
                            title = R.string.behavior,
                            description = R.string.behavior_desc,
                            icon = R.drawable.ic_behavior
                    ),
                    Preference(
                            title = R.string.audio,
                            description = R.string.audio_desc,
                            icon = R.drawable.ic_volume_up
                    ),
            )

            preference.postValue(preferences)
        }
    }

    companion object {
        data class Preference(
                val title: Int,
                val description: Int,
                val icon: Int,
        )
    }
}