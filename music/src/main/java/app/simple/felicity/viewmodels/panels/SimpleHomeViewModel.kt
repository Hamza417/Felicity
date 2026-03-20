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

    private val homeData: MutableLiveData<MutableList<Any>> by lazy {
        MutableLiveData<MutableList<Any>>().apply {
            setHomeData()
        }
    }

    fun getHomeData(): LiveData<MutableList<Any>> {
        return homeData
    }

    /**
     * Forces a reload of the home data from preferences.
     * Use this after resetting the item order so the list reflects the default arrangement.
     */
    fun reloadHomeData() {
        setHomeData()
    }

    private fun setHomeData() {
        viewModelScope.launch(Dispatchers.IO) {
            val defaultPanels = listOf(
                    Group(R.string.library),
                    Panel(R.string.songs, R.drawable.ic_song),
                    Panel(R.string.albums, R.drawable.ic_album),
                    Panel(R.string.artists, R.drawable.ic_artist),
                    Panel(R.string.genres, R.drawable.ic_piano),
                    Panel(R.string.year, R.drawable.ic_date_range),
                    Group(R.string.activity),
                    Panel(R.string.playing_queue, R.drawable.ic_queue),
                    Panel(R.string.recently_added, R.drawable.ic_recently_added),
                    Panel(R.string.recently_played, R.drawable.ic_history),
                    Panel(R.string.most_played, R.drawable.ic_equalizer),
                    Panel(R.string.favorites, R.drawable.ic_favorite_filled),
                    Group(R.string.files),
                    Panel(R.string.folders, R.drawable.ic_folder),
                    Panel(R.string.folders_hierarchy, R.drawable.ic_tree),
                    // Element(R.string.playlists, R.drawable.ic_list),
                    // Group(R.string.general),
                    // Panel(R.string.preferences, R.drawable.ic_settings)
            )

            homeData.postValue(defaultPanels.toMutableList())
        }
    }

    companion object {
        data class Panel(val titleResId: Int, val iconResId: Int)
        data class Group(val titleResId: Int)
    }
}
