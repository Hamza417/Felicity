package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.shuffle.Shuffle.millerShuffle
import app.simple.felicity.viewmodels.panels.DashboardViewModel.Companion.RANDOMIZER_DELAY
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Element
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * Provides data for the recommended grid section, the recently played section
 * (substituted with recently added songs until a dedicated history database is
 * available), the recently added songs section, the favorites section, and the
 * fixed lists of panel navigation items displayed in the browse grid.
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

    private val _recommended = MutableStateFlow<List<Audio>?>(null)

    /**
     * A fresh random selection of 5-9 songs fetched from the database on each
     * [RANDOMIZER_DELAY] cycle, used to populate the spanned art grid in the
     * recommended section.
     */
    val recommended: StateFlow<List<Audio>?> = _recommended.asStateFlow()

    /**
     * The first seven panel navigation elements shown in the collapsed browse grid.
     * These represent the most commonly used sections of the app.
     */
    val firstPanelItems: List<Element> = listOf(
            Element(R.string.songs, R.drawable.ic_song),
            Element(R.string.albums, R.drawable.ic_album),
            Element(R.string.artists, R.drawable.ic_artist),
            Element(R.string.genres, R.drawable.ic_piano),
            Element(R.string.favorites, R.drawable.ic_favorite_filled),
            Element(R.string.playing_queue, R.drawable.ic_queue),
            Element(R.string.recently_added, R.drawable.ic_recently_added)
    )

    /**
     * The complete list of all panel navigation elements revealed when the user
     * taps the expand button in the browse grid.
     */
    val allPanelItems: List<Element> = listOf(
            Element(R.string.songs, R.drawable.ic_song),
            Element(R.string.albums, R.drawable.ic_album),
            Element(R.string.artists, R.drawable.ic_artist),
            Element(R.string.genres, R.drawable.ic_piano),
            Element(R.string.folders, R.drawable.ic_folder),
            Element(R.string.folders_hierarchy, R.drawable.ic_tree),
            Element(R.string.playing_queue, R.drawable.ic_queue),
            Element(R.string.recently_added, R.drawable.ic_recently_added),
            Element(R.string.year, R.drawable.ic_date_range),
            Element(R.string.favorites, R.drawable.ic_favorite_filled),
            Element(R.string.preferences, R.drawable.ic_settings)
    )

    init {
        loadRecentSongs()
        loadFavorites()
        loadRecommended()
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

    private fun loadRecommended() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val count = (RECOMMENDED_MIN_COUNT..RECOMMENDED_MAX_COUNT).random()
                    val songs = audioRepository.getAllAudioList()
                        .millerShuffle()
                        .take(RECOMMENDED_MAX_COUNT)
                    if (songs.isNotEmpty()) {
                        @Suppress("UNCHECKED_CAST")
                        _recommended.value = songs
                    }
                    Log.d(TAG, "loadRecommended: posted ${songs.size} songs")
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading recommended section", e)
                }
                delay(RANDOMIZER_DELAY)
            }
        }
    }

    companion object {
        private const val TAG = "DashboardViewModel"

        /** Minimum number of songs fetched for the recommended spanned art grid per cycle. */
        private const val RECOMMENDED_MIN_COUNT = 5

        /** Maximum number of songs fetched for the recommended spanned art grid per cycle. */
        private const val RECOMMENDED_MAX_COUNT = 6

        /**
         * Interval in milliseconds between each recommended grid refresh cycle.
         * The ViewModel drives the periodic fetch so the fragment only observes.
         */
        private const val RANDOMIZER_DELAY = 10_000L
    }
}
