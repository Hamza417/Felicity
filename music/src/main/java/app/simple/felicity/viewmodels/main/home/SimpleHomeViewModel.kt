package app.simple.felicity.viewmodels.main.home

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.models.Element
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SimpleHomeViewModel(application: Application) : WrappedViewModel(application) {

    private val homeData: MutableLiveData<List<Element>> by lazy {
        MutableLiveData<List<Element>>().apply {
            setHomeData()
        }
    }

    fun getHomeData(): LiveData<List<Element>> {
        return homeData
    }

    private fun setHomeData() {
        viewModelScope.launch(Dispatchers.IO) {
            // Simulate data fetching
            val elements = listOf(
                    Element(R.string.songs, R.drawable.ic_music),
                    Element(R.string.albums, R.drawable.ic_album),
                    Element(R.string.artists, R.drawable.ic_artist)
            )
            homeData.postValue(elements)
        }
    }
}