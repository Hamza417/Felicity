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

    private var favoritesJob: Job? = null
    private var recentlyPlayedJob: Job? = null
    private var mostPlayedJob: Job? = null
    private var recentlyAddedJob: Job? = null

    init {
        startFavoritesFlow()
        startRecentlyPlayedFlow()
        startMostPlayedFlow()
        startRecentlyAddedFlow()
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
    }
}