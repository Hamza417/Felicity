package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.repositories.SongStatRepository
import app.simple.felicity.repository.shuffle.Shuffle.millerShuffle
import app.simple.felicity.viewmodels.panels.HomeViewModel.Companion.RECOMMENDED_MAX_COUNT
import app.simple.felicity.viewmodels.panels.HomeViewModel.Companion.RECOMMENDED_MOST_PLAYED_COUNT
import app.simple.felicity.viewmodels.panels.HomeViewModel.Companion.RECOMMENDED_RECENTLY_PLAYED_COUNT
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
 * ViewModel for the ArtFlow home screen.
 *
 * Exposes four independent [StateFlow] streams — one per curated section (Favorites,
 * Recently Played, Most Played, Recently Added). Each stream is backed by a long-lived
 * Room flow so any addition or deletion in the audio database is immediately forwarded to
 * the UI without requiring a restart.
 *
 * Each section has its own [Job] so flows can be cancelled and restarted independently
 * when library-filter preferences change.
 *
 * @author Hamza417
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
        application: Application,
        private val audioRepository: AudioRepository,
        private val songStatRepository: SongStatRepository
) : WrappedViewModel(application) {

    private val _favorites = MutableStateFlow<List<Audio>>(emptyList())

    /** Favorite songs, re-emitted whenever the audio table changes. */
    val favorites: StateFlow<List<Audio>> = _favorites.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<Audio>>(emptyList())

    /** Recently-played songs ordered by last-played timestamp descending, re-emitted on stat table changes. */
    val recentlyPlayed: StateFlow<List<Audio>> = _recentlyPlayed.asStateFlow()

    private val _mostPlayed = MutableStateFlow<List<Audio>>(emptyList())

    /** Most-played songs ordered by play count descending, re-emitted on stat table changes. */
    val mostPlayed: StateFlow<List<Audio>> = _mostPlayed.asStateFlow()

    private val _recentlyAdded = MutableStateFlow<List<Audio>>(emptyList())

    /** Recently-added songs ordered by date-added descending, re-emitted on audio table changes. */
    val recentlyAdded: StateFlow<List<Audio>> = _recentlyAdded.asStateFlow()

    private val _recommended = MutableStateFlow<List<Audio>>(emptyList())

    /**
     * A random selection of songs fetched from the database on each explicit
     * refresh, used to populate the spanned art grid in the recommended section.
     */
    val recommended: StateFlow<List<Audio>> = _recommended.asStateFlow()

    private val _libraryStats = MutableStateFlow<Pair<Int, Long>?>(null)

    /**
     * A pair of (trackCount, totalDurationMs) so the header chip can show something like
     * "12,403 Tracks • 840 Hours". Updates automatically whenever the audio table changes.
     */
    val libraryStats: StateFlow<Pair<Int, Long>?> = _libraryStats.asStateFlow()

    /** Active coroutine job collecting the recommended combined flow. */
    private var recommendedJob: Job? = null

    /**
     * Incremented each time the user explicitly requests a new recommendation shuffle.
     * Including this value in the [combine] for the recommended flow ensures a reshuffle
     * even when the underlying data has not changed.
     */
    private val _recommendedRefreshTrigger = MutableStateFlow(0)

    private var favoritesJob: Job? = null
    private var recentlyPlayedJob: Job? = null
    private var mostPlayedJob: Job? = null
    private var recentlyAddedJob: Job? = null

    init {
        startFavoritesFlow()
        startRecentlyPlayedFlow()
        startMostPlayedFlow()
        startRecentlyAddedFlow()
        startRecommendedFlow()
        startLibraryStatsFlow()
    }

    private fun startFavoritesFlow() {
        favoritesJob?.cancel()
        favoritesJob = viewModelScope.launch {
            audioRepository.getFavoriteAudio()
                .catch { e -> Log.e(TAG, "Error loading favorites", e); emit(emptyList()) }
                .flowOn(Dispatchers.IO)
                .collect { list ->
                    _favorites.value = list.take(TAKE_COUNT)
                    Log.d(TAG, "favorites updated: ${list.size} songs")
                }
        }
    }

    private fun startRecentlyPlayedFlow() {
        recentlyPlayedJob?.cancel()
        recentlyPlayedJob = viewModelScope.launch {
            songStatRepository.getRecentlyPlayed()
                .catch { e -> Log.e(TAG, "Error loading recently played", e); emit(emptyList()) }
                .flowOn(Dispatchers.IO)
                .collect { list ->
                    _recentlyPlayed.value = list.take(TAKE_COUNT)
                    Log.d(TAG, "recentlyPlayed updated: ${list.size} songs")
                }
        }
    }

    private fun startMostPlayedFlow() {
        mostPlayedJob?.cancel()
        mostPlayedJob = viewModelScope.launch {
            songStatRepository.getMostPlayed()
                .catch { e -> Log.e(TAG, "Error loading most played", e); emit(emptyList()) }
                .flowOn(Dispatchers.IO)
                .collect { list ->
                    _mostPlayed.value = list.take(TAKE_COUNT)
                    Log.d(TAG, "mostPlayed updated: ${list.size} songs")
                }
        }
    }

    private fun startRecentlyAddedFlow() {
        recentlyAddedJob?.cancel()
        recentlyAddedJob = viewModelScope.launch {
            audioRepository.getRecentAudio()
                .catch { e -> Log.e(TAG, "Error loading recently added", e); emit(mutableListOf()) }
                .flowOn(Dispatchers.IO)
                .collect { list ->
                    _recentlyAdded.value = list.take(TAKE_COUNT)
                    Log.d(TAG, "recentlyAdded updated: ${list.size} songs")
                }
        }
    }

    /**
     * Starts (or restarts) the recommended-grid flow by combining three reactive Room flows
     * with [_recommendedRefreshTrigger]. Any change in the audio table, the stats table, or
     * an explicit refresh request will trigger a full recompute of the recommendation list.
     *
     * The previous collection job is cancelled before a new one starts to avoid duplicate
     * emissions if the trigger is incremented.
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
     * Watches the full audio library and keeps the library stats pair up to date.
     * Runs in the background so a fresh media scan instantly refreshes the header chip
     * without any manual intervention from the fragment.
     */
    private fun startLibraryStatsFlow() {
        viewModelScope.launch {
            audioRepository.getAllAudio()
                .catch { e -> Log.e(TAG, "Error computing library stats", e) }
                .flowOn(Dispatchers.IO)
                .collect { audioList ->
                    val totalMs = audioList.sumOf { it.duration }
                    _libraryStats.value = Pair(audioList.size, totalMs)
                    Log.d(TAG, "libraryStats updated: ${audioList.size} tracks")
                }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            LibraryPreferences.MINIMUM_AUDIO_SIZE,
            LibraryPreferences.MINIMUM_AUDIO_LENGTH -> {
                Log.d(TAG, "onSharedPreferenceChanged: Relevant preference changed, restarting flows")
                startFavoritesFlow()
                startRecentlyPlayedFlow()
                startMostPlayedFlow()
                startRecentlyAddedFlow()
            }
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
        private const val TAKE_COUNT = 18

        /**
         * Total number of songs to include in the recommended pool for the tile grid.
         * Set generously so there is always enough variety to fill every song tile slot,
         * even when some panel slots overflow into song slots.
         */
        private const val RECOMMENDED_MAX_COUNT = 30

        /** Number of slots in the recommended grid filled from the most-played list. */
        private const val RECOMMENDED_MOST_PLAYED_COUNT = 8

        /** Number of slots in the recommended grid filled from the recently-played list. */
        private const val RECOMMENDED_RECENTLY_PLAYED_COUNT = 6
    }
}