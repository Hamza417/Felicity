package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.UserInterfacePreferences
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
            val defaultPanels = mutableListOf<Any>()

            defaultPanels.add(Group(R.string.library))
            defaultPanels.add(Panel(R.string.songs, R.drawable.ic_song))
            defaultPanels.add(Panel(R.string.albums, R.drawable.ic_album))
            defaultPanels.add(Panel(R.string.artists, R.drawable.ic_people))
            defaultPanels.add(Panel(R.string.album_artists, R.drawable.ic_artist))
            defaultPanels.add(Panel(R.string.genres, R.drawable.ic_piano))
            defaultPanels.add(Panel(R.string.year, R.drawable.ic_date_range))
            defaultPanels.add(Panel(R.string.playlists, R.drawable.ic_list))
            defaultPanels.add(Group(R.string.activity))
            defaultPanels.add(Panel(R.string.playing_queue, R.drawable.ic_queue))
            defaultPanels.add(Panel(R.string.recently_added, R.drawable.ic_recently_added))
            defaultPanels.add(Panel(R.string.recently_played, R.drawable.ic_history))
            defaultPanels.add(Panel(R.string.most_played, R.drawable.ic_equalizer))
            if (UserInterfacePreferences.isLikeIconInsteadOfThumb()) {
                defaultPanels.add(Panel(R.string.favorites, R.drawable.ic_thumb_up))
            } else {
                defaultPanels.add(Panel(R.string.favorites, R.drawable.ic_favorite_filled))
            }
            defaultPanels.add(Group(R.string.files))
            defaultPanels.add(Panel(R.string.folders, R.drawable.ic_folder))
            defaultPanels.add(Panel(R.string.folders_hierarchy, R.drawable.ic_tree))

            homeData.postValue(defaultPanels.toMutableList())
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            UserInterfacePreferences.LIKE_ICON_INSTEAD_OF_HEART -> setHomeData()
        }
    }

    companion object {
        data class Panel(val titleResId: Int, val iconResId: Int)
        data class Group(val titleResId: Int)
    }
}
