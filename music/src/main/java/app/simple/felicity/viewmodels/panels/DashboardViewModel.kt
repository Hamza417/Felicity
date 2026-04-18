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
import app.simple.felicity.viewmodels.panels.DashboardViewModel.Companion.RECOMMENDED_MAX_COUNT
import app.simple.felicity.viewmodels.panels.DashboardViewModel.Companion.RECOMMENDED_MOST_PLAYED_COUNT
import app.simple.felicity.viewmodels.panels.DashboardViewModel.Companion.RECOMMENDED_RECENTLY_PLAYED_COUNT
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Panel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
     * Incremented each time the user explicitly requests a new recommendation shuffle.
     * Including this value in the [combine] for the recommended flow ensures a reshuffle
     * even when the underlying data has not changed.
     */
    private val _recommendedRefreshTrigger = MutableStateFlow(0)

    /** Active coroutine job collecting the recommended combined flow. */
    private var recommendedJob: Job? = null

    /**
     * The complete list of all panel navigation elements displayed in the browse grid.
     */
    val allPanelPanels: List<Panel> = listOf(
            Panel(R.string.songs, R.drawable.ic_song_16dp),
            Panel(R.string.albums, R.drawable.ic_album_16dp),
            Panel(R.string.artists, R.drawable.ic_people_16dp),
            Panel(R.string.album_artists, R.drawable.ic_artist_16dp),
            Panel(R.string.genres, R.drawable.ic_piano_16dp),
            Panel(R.string.year, R.drawable.ic_date_range_16dp),
            Panel(R.string.playlists, R.drawable.ic_list_16dp),
            Panel(R.string.playing_queue, R.drawable.ic_queue_16dp),
            Panel(R.string.recently_added, R.drawable.ic_recently_added_16dp),
            Panel(R.string.recently_played, R.drawable.ic_history_16dp),
            Panel(R.string.most_played, R.drawable.ic_equalizer_16dp),
            Panel(R.string.favorites, R.drawable.ic_favorite_filled_16dp),
            Panel(R.string.folders, R.drawable.ic_folder_16dp),
            Panel(R.string.folders_hierarchy, R.drawable.ic_tree_16dp),
    )

    init {
        startRecentlyPlayedFlow()
        startRecentlyAddedFlow()
        startFavoritesFlow()
        startRecommendedFlow()
    }

    private fun startRecentlyPlayedFlow() {
        viewModelScope.launch {
            songStatRepository.getRecentlyPlayed()
                .catch { exception ->
                    Log.e(TAG, "Error loading recently played songs", exception)
                    emit(emptyList())
                }
                .flowOn(Dispatchers.IO)
                .collect { list ->
                    _recentlyPlayed.value = list
                    Log.d(TAG, "recentlyPlayed updated: ${list.size} songs")
                }
        }
    }

    private fun startRecentlyAddedFlow() {
        viewModelScope.launch {
            audioRepository.getRecentAudio()
                .catch { exception ->
                    Log.e(TAG, "Error loading recently added songs", exception)
                    emit(mutableListOf())
                }
                .flowOn(Dispatchers.IO)
                .collect { list ->
                    _recentlyAdded.value = list
                    Log.d(TAG, "recentlyAdded updated: ${list.size} songs")
                }
        }
    }

    private fun startFavoritesFlow() {
        viewModelScope.launch {
            audioRepository.getFavoriteAudio()
                .catch { exception ->
                    Log.e(TAG, "Error loading favorites", exception)
                    emit(emptyList())
                }
                .flowOn(Dispatchers.IO)
                .collect { list ->
                    _favorites.value = list
                    Log.d(TAG, "favorites updated: ${list.size} songs")
                }
        }
    }

    /**
     * Starts (or restarts) the recommended-grid flow by combining three reactive Room flows
     * with [_recommendedRefreshTrigger]. Any change in the audio table, the stats table, or
     * an explicit refresh request will trigger a full recompute of the recommendation list.
     *
     * The previous collection job is cancelled before a new one starts to avoid duplicate
     * emissions when [refreshRecommended] is called.
     */
    private fun startRecommendedFlow() {
        recommendedJob?.cancel()
        recommendedJob = viewModelScope.launch(Dispatchers.IO) {
            combine(
                    songStatRepository.getMostPlayed(),
                    songStatRepository.getRecentlyPlayed(),
                    audioRepository.getAllAudio(),
                    _recommendedRefreshTrigger
            ) { mostPlayed, recentlyPlayed, allAudio, _ ->
                computeRecommended(mostPlayed, recentlyPlayed, allAudio)
            }
                .catch { e -> Log.e(TAG, "Error loading recommended section", e) }
                .collect { songs ->
                    if (songs.isNotEmpty()) {
                        _recommended.value = songs
                        Log.d(TAG, "recommended updated: ${songs.size} songs")
                    }
                }
        }
    }

    /**
     * Pure function that derives the final recommended list from the three data sources.
     * Keeps the top [RECOMMENDED_MOST_PLAYED_COUNT] most-played songs, adds up to
     * [RECOMMENDED_RECENTLY_PLAYED_COUNT] recently-played songs that are not already
     * in the most-played set, then fills remaining slots from the full library using
     * [millerShuffle].
     *
     * @param mostPlayedList  Latest most-played list from the stats table.
     * @param recentlyPlayedList  Latest recently-played list from the stats table.
     * @param allAudio  Latest full audio library snapshot.
     * @return A shuffled list of up to [RECOMMENDED_MAX_COUNT] songs.
     */
    private fun computeRecommended(
            mostPlayedList: List<Audio>,
            recentlyPlayedList: List<Audio>,
            allAudio: List<Audio>
    ): List<Audio> {
        val mostPlayed = mostPlayedList.take(RECOMMENDED_MOST_PLAYED_COUNT)
        val mostPlayedIds = mostPlayed.map { it.id }.toHashSet()
        val recentlyPlayed = recentlyPlayedList
            .filterNot { it.id in mostPlayedIds }
            .take(RECOMMENDED_RECENTLY_PLAYED_COUNT)

        val composed = (mostPlayed + recentlyPlayed).distinctBy { it.id }

        return if (composed.size >= RECOMMENDED_MAX_COUNT) {
            composed.shuffled().take(RECOMMENDED_MAX_COUNT)
        } else {
            val existingIds = composed.map { it.id }.toHashSet()
            val filler = allAudio
                .filterNot { it.id in existingIds }
                .millerShuffle()
                .take(RECOMMENDED_MAX_COUNT - composed.size)
            (composed + filler).shuffled()
        }
    }

    /**
     * Triggers a fresh random selection of songs for the recommended grid.
     *
     * Increments [_recommendedRefreshTrigger] so the combined flow emits a new value
     * and [computeRecommended] runs again with the current data set, producing a new
     * shuffled result even when the underlying DB tables have not changed.
     */
    fun refreshRecommended() {
        _recommendedRefreshTrigger.value++
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
