package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Element
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Dashboard home screen.
 *
 * Provides data for the recently played section (substituted with recently added songs
 * until a dedicated history database is available), the recently added songs section,
 * the favorites section, and the fixed list of seven panel navigation items displayed
 * in the browse grid.
 *
 * @author Hamza417
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
        application: Application,
        private val audioRepository: AudioRepository
) : WrappedViewModel(application) {

    private val _recentlyPlayed = MutableStateFlow<List<Audio>>(emptyList())

    /**
     * Recently played songs flow. Uses recently added songs as a substitute
     * since a dedicated history database has not been implemented yet.
     */
    val recentlyPlayed: StateFlow<List<Audio>> = _recentlyPlayed.asStateFlow()

    private val _recentlyAdded = MutableStateFlow<List<Audio>>(emptyList())

    /** Recently added songs flow, ordered by date added descending. */
    val recentlyAdded: StateFlow<List<Audio>> = _recentlyAdded.asStateFlow()

    private val _favorites = MutableStateFlow<List<Audio>>(emptyList())

    /** Favorite songs flow. */
    val favorites: StateFlow<List<Audio>> = _favorites.asStateFlow()

    /**
     * Fixed list of seven panel navigation elements shown in the browse grid.
     * These represent the most commonly used sections of the app.
     */
    val panelItems: List<Element> = listOf(
            Element(R.string.songs, R.drawable.ic_song),
            Element(R.string.albums, R.drawable.ic_album),
            Element(R.string.artists, R.drawable.ic_artist),
            Element(R.string.genres, R.drawable.ic_piano),
            Element(R.string.favorites, R.drawable.ic_favorite_filled),
            Element(R.string.playing_queue, R.drawable.ic_queue),
            Element(R.string.recently_added, R.drawable.ic_recently_added)
    )

    init {
        loadRecentSongs()
        loadFavorites()
    }

    private fun loadRecentSongs() {
        viewModelScope.launch {
            audioRepository.getRecentAudio()
                .catch { exception ->
                    Log.e(TAG, "Error loading recent songs", exception)
                    emit(mutableListOf())
                }
                .flowOn(Dispatchers.IO)
                .collect { list ->
                    _recentlyPlayed.value = list
                    _recentlyAdded.value = list
                    Log.d(TAG, "loadRecentSongs: ${list.size} songs loaded")
                }
        }
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            audioRepository.getFavoriteAudio()
                .catch { exception ->
                    Log.e(TAG, "Error loading favorites", exception)
                    emit(emptyList())
                }
                .flowOn(Dispatchers.IO)
                .collect { list ->
                    _favorites.value = list
                    Log.d(TAG, "loadFavorites: ${list.size} favorites loaded")
                }
        }
    }

    companion object {
        private const val TAG = "DashboardViewModel"
    }
}

