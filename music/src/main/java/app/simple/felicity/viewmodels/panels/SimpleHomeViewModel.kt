package app.simple.felicity.viewmodels.panels

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
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
                    Element(R.string.songs, R.drawable.ic_song),
                    Element(R.string.albums, R.drawable.ic_album),
                    Element(R.string.artists, R.drawable.ic_artist),
                    Element(R.string.genres, R.drawable.ic_piano),
                    Element(R.string.folders, R.drawable.ic_folder),
                    Element(R.string.folders_hierarchy, R.drawable.ic_tree),
                    // Element(R.string.playlists, R.drawable.ic_list),
                    Element(R.string.preferences, R.drawable.ic_settings)
            )

            homeData.postValue(elements)
        }
    }

    companion object {
        data class Element(val titleResId: Int, val iconResId: Int)
    }
}