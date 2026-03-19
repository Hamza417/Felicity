package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.HomePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SimpleHomeViewModel(application: Application) : WrappedViewModel(application) {

    private val homeData: MutableLiveData<MutableList<Element>> by lazy {
        MutableLiveData<MutableList<Element>>().apply {
            setHomeData()
        }
    }

    fun getHomeData(): LiveData<MutableList<Element>> {
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
            val defaultElements = listOf(
                    Element(R.string.songs, R.drawable.ic_song),
                    Element(R.string.albums, R.drawable.ic_album),
                    Element(R.string.artists, R.drawable.ic_artist),
                    Element(R.string.genres, R.drawable.ic_piano),
                    Element(R.string.folders, R.drawable.ic_folder),
                    Element(R.string.folders_hierarchy, R.drawable.ic_tree),
                    Element(R.string.playing_queue, R.drawable.ic_queue),
                    Element(R.string.recently_added, R.drawable.ic_recently_added),
                    Element(R.string.recently_played, R.drawable.ic_history),
                    Element(R.string.most_played, R.drawable.ic_equalizer),
                    Element(R.string.year, R.drawable.ic_date_range),
                    Element(R.string.favorites, R.drawable.ic_favorite_filled),
                    // Element(R.string.playlists, R.drawable.ic_list),
                    Element(R.string.preferences, R.drawable.ic_settings)
            )

            val savedOrder = HomePreferences.getHomeItemsOrder()
            val ordered = if (savedOrder.isBlank()) {
                defaultElements.toMutableList()
            } else {
                val idList = savedOrder.split(",").mapNotNull { it.trim().toIntOrNull() }
                val elementMap = defaultElements.associateBy { it.titleResId }
                val reordered = idList.mapNotNull { elementMap[it] }.toMutableList()
                // Add any new elements not yet in saved order
                defaultElements.filter { it.titleResId !in idList }.forEach { reordered.add(it) }
                reordered
            }

            homeData.postValue(ordered)
        }
    }

    /**
     * Replaces the current home item order with [elements] and persists it.
     * Called by the organize dialog after the user confirms a new arrangement.
     */
    fun updateOrder(elements: List<Element>) {
        val mutable = elements.toMutableList()
        homeData.postValue(mutable)
        saveOrder(mutable)
    }

    private fun saveOrder(elements: List<Element>) {
        viewModelScope.launch(Dispatchers.IO) {
            val order = elements.joinToString(",") { it.titleResId.toString() }
            HomePreferences.setHomeItemsOrder(order)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        // HOME_ITEMS_ORDER is only written by this ViewModel itself (via saveOrder).
        // There is no need to react to our own writes — the adapter already holds
        // the correct in-memory order. Re-calling setHomeData() would post a NEW
        // MutableList object that the adapter does not reference.
    }

    companion object {
        data class Element(val titleResId: Int, val iconResId: Int)
    }
}
