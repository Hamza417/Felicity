package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.repositories.SongStatRepository
import app.simple.felicity.repository.shuffle.Shuffle.millerShuffle
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Panel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Dashboard home screen.
 *
 * Provides data for the recommended grid section, the recently played section,
 * the recently added songs section, the favorites section, and the fixed lists
 * of panel navigation items displayed in the browse grid.
 *
 * @author Hamza417
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
        application: Application,
        private val audioRepository: AudioRepository,
        private val songStatRepository: SongStatRepository
) : WrappedViewModel(application) {

    private val _recentlyPlayed = MutableStateFlow<List<Audio>>(emptyList())

    /** Recently played songs flow ordered by last-played timestamp descending. */
    val recentlyPlayed: StateFlow<List<Audio>> = _recentlyPlayed.asStateFlow()

    private val _recentlyAdded = MutableStateFlow<List<Audio>>(emptyList())

    /** Recently added songs flow, ordered by date added descending. */
    val recentlyAdded: StateFlow<List<Audio>> = _recentlyAdded.asStateFlow()

    private val _favorites = MutableStateFlow<List<Audio>>(emptyList())

    /** Favorite songs flow. */
    val favorites: StateFlow<List<Audio>> = _favorites.asStateFlow()

    private val _recommended = MutableStateFlow<List<Audio>?>(null)

    /**
     * A random selection of songs fetched from the database on each explicit
     * refresh, used to populate the spanned art grid in the recommended section.
     */
    val recommended: StateFlow<List<Audio>?> = _recommended.asStateFlow()

    /**
     * The first seven panel navigation elements shown in the collapsed browse grid.
     * These represent the most commonly used sections of the app.
     */
    val firstPanelPanels: List<Panel> = listOf(
            Panel(R.string.songs, R.drawable.ic_song),
            Panel(R.string.albums, R.drawable.ic_album),
            Panel(R.string.artists, R.drawable.ic_artist),
            Panel(R.string.genres, R.drawable.ic_piano),
            Panel(R.string.favorites, R.drawable.ic_favorite_filled),
            Panel(R.string.playing_queue, R.drawable.ic_queue),
            Panel(R.string.recently_added, R.drawable.ic_recently_added),
            Panel(R.string.recently_played, R.drawable.ic_history),
            Panel(R.string.most_played, R.drawable.ic_equalizer)
    )

    /**
     * The complete list of all panel navigation elements revealed when the user
     * taps the expand button in the browse grid.
     */
    val allPanelPanels: List<Panel> = listOf(
            Panel(R.string.songs, R.drawable.ic_song),
            Panel(R.string.albums, R.drawable.ic_album),
            Panel(R.string.artists, R.drawable.ic_artist),
            Panel(R.string.genres, R.drawable.ic_piano),
            Panel(R.string.folders, R.drawable.ic_folder),
            Panel(R.string.folders_hierarchy, R.drawable.ic_tree),
            Panel(R.string.playing_queue, R.drawable.ic_queue),
            Panel(R.string.recently_added, R.drawable.ic_recently_added),
            Panel(R.string.year, R.drawable.ic_date_range),
            Panel(R.string.favorites, R.drawable.ic_favorite_filled),
            Panel(R.string.most_played, R.drawable.ic_equalizer),
            Panel(R.string.recently_played, R.drawable.ic_history),
            Panel(R.string.preferences, R.drawable.ic_settings)
    )

    init {
        loadRecentlyPlayed()
        loadRecentlyAdded()
        loadFavorites()
        loadRecommended()
    }

    private fun loadRecentlyPlayed() {
        viewModelScope.launch {
            songStatRepository.getRecentlyPlayed()
                .catch { exception ->
                    Log.e(TAG, "Error loading recently played songs", exception)
                    emit(emptyList())
                }
                .flowOn(Dispatchers.IO)
                .first()
                .also { list ->
                    _recentlyPlayed.value = list
                    Log.d(TAG, "loadRecentlyPlayed: ${list.size} songs loaded")
                }
        }
    }

    private fun loadRecentlyAdded() {
        viewModelScope.launch {
            audioRepository.getRecentAudio()
                .catch { exception ->
                    Log.e(TAG, "Error loading recently added songs", exception)
                    emit(mutableListOf())
                }
                .flowOn(Dispatchers.IO)
                .first()
                .also { list ->
                    _recentlyAdded.value = list
                    Log.d(TAG, "loadRecentlyAdded: ${list.size} songs loaded")
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
                .first()
                .also { list ->
                    _favorites.value = list
                    Log.d(TAG, "loadFavorites: ${list.size} favorites loaded")
                }
        }
    }

    private fun loadRecommended() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mostPlayed = songStatRepository.getMostPlayed()
                    .first()
                    .take(RECOMMENDED_MOST_PLAYED_COUNT)

                val recentlyPlayedIds = mostPlayed.map { it.id }.toHashSet()
                val recentlyPlayed = songStatRepository.getRecentlyPlayed()
                    .first()
                    .filterNot { it.id in recentlyPlayedIds }
                    .take(RECOMMENDED_RECENTLY_PLAYED_COUNT)

                val composed = (mostPlayed + recentlyPlayed).distinctBy { it.id }

                val songs = if (composed.size >= RECOMMENDED_MAX_COUNT) {
                    composed.shuffled().take(RECOMMENDED_MAX_COUNT)
                } else {
                    // Fill remaining slots from the full library using a random shuffle
                    val existingIds = composed.map { it.id }.toHashSet()
                    val filler = audioRepository.getAllAudioList()
                        .filterNot { it.id in existingIds }
                        .millerShuffle()
                        .take(RECOMMENDED_MAX_COUNT - composed.size)
                    (composed + filler).shuffled()
                }

                if (songs.isNotEmpty()) {
                    _recommended.value = songs
                }
                Log.d(TAG, "loadRecommended: posted ${songs.size} songs " +
                        "(${mostPlayed.size} most-played, ${recentlyPlayed.size} recently-played)")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading recommended section", e)
            }
        }
    }

    /**
     * Triggers a fresh random selection of songs for the recommended grid.
     *
     * Should be called when the user explicitly requests new recommendations
     * via the refresh button on the dashboard.
     */
    fun refreshRecommended() {
        loadRecommended()
    }

    companion object {
        private const val TAG = "DashboardViewModel"

        /** Total number of songs shown in the recommended spanned art grid. */
        private const val RECOMMENDED_MAX_COUNT = 6

        /** Number of slots in the recommended grid filled from the most-played list. */
        private const val RECOMMENDED_MOST_PLAYED_COUNT = 4

        /** Number of slots in the recommended grid filled from the recently-played list. */
        private const val RECOMMENDED_RECENTLY_PLAYED_COUNT = 2
    }
}
