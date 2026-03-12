package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.FavoritesPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.sort.FavoritesSort.sortedFavorites
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Favorites panel.
 * Loads favorite songs from the repository and applies [FavoritesPreferences]-driven
 * sort/order without a round-trip to the database when only the sort changes.
 *
 * @author Hamza417
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
        application: Application,
        private val audioRepository: AudioRepository
) : WrappedViewModel(application) {

    private val _favorites = MutableStateFlow<List<Audio>>(emptyList())
    val favorites: StateFlow<List<Audio>> = _favorites.asStateFlow()

    /** Raw list from the database kept for cheap in-memory re-sorts. */
    private var rawFavoriteList: List<Audio> = emptyList()

    private var loadJob: Job? = null

    init {
        loadFavorites()
    }

    private fun loadFavorites() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            audioRepository.getFavoriteAudio()
                .catch { e ->
                    Log.e(TAG, "Error loading favorites", e)
                    emit(emptyList())
                }
                .flowOn(Dispatchers.IO)
                .collect { list ->
                    rawFavoriteList = list
                    _favorites.value = list.sortedFavorites()
                    Log.d(TAG, "Favorites loaded: ${list.size} songs")
                }
        }
    }

    /**
     * Re-sorts the cached list without querying the database again.
     * Called when only the sort field or direction changes.
     */
    private fun resort() {
        viewModelScope.launch(Dispatchers.Default) {
            _favorites.value = rawFavoriteList.sortedFavorites()
            Log.d(TAG, "resort: ${_favorites.value.size} favorites re-sorted")
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, s: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, s)
        when (s) {
            FavoritesPreferences.SONG_SORT,
            FavoritesPreferences.SORTING_STYLE -> resort()
        }
    }

    companion object {
        private const val TAG = "FavoritesViewModel"
    }
}
