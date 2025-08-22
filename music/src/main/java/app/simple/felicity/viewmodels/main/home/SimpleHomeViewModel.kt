package app.simple.felicity.viewmodels.main.home

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.core.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.simple.felicity.decoration.R as Decor

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
                    Element(R.string.songs, Decor.drawable.ic_song),
                    Element(R.string.albums, Decor.drawable.ic_album),
                    Element(R.string.artists, Decor.drawable.ic_artist),
                    Element(R.string.genres, Decor.drawable.ic_piano),
                    Element(R.string.playlists, Decor.drawable.ic_list),
                    Element(R.string.preferences, Decor.drawable.ic_settings)
            )

            homeData.postValue(elements)
        }
    }

    companion object {
        data class Element(val titleResId: Int, val iconResId: Int)
    }
}
