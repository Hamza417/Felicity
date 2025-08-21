package app.simple.felicity.viewmodels.main.preferences

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.simple.felicity.core.R as CoreR
import app.simple.felicity.decoration.R as DecorationR

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
                            title = CoreR.string.appearance,
                            description = CoreR.string.appearance_desc,
                            icon = DecorationR.drawable.ic_water_drop
                    ),
                    Preference(
                            title = CoreR.string.user_interface,
                            description = CoreR.string.user_interface_desc,
                            icon = DecorationR.drawable.ic_carousel
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