package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.PlaylistPreferences
import app.simple.felicity.repository.models.PlaylistWithSongs
import app.simple.felicity.repository.repositories.PlaylistRepository
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
 * ViewModel for the Playlists panel.
 *
 * <p>Loads playlists paired with their song lists from [PlaylistRepository] using
 * the with-songs query so the song count is always up to date without a
 * separate query. Sorting is performed in-memory to avoid extra database round-trips.</p>
 *
 * <p>M3U playlists are now managed automatically by the library scanner running in
 * the background — no user action needed. The scanner picks up M3U files on disk,
 * creates or refreshes playlists for them, and cleans up any that no longer exist.</p>
 *
 * @author Hamza417
 */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
        application: Application,
        private val playlistRepository: PlaylistRepository
) : WrappedViewModel(application) {

    private val _playlists = MutableStateFlow<List<PlaylistWithSongs>>(emptyList())
    val playlists: StateFlow<List<PlaylistWithSongs>> = _playlists.asStateFlow()

    /** Cached raw list kept for cheap in-memory re-sorts. */
    private var rawList: List<PlaylistWithSongs> = emptyList()

    private var loadJob: Job? = null

    init {
        loadPlaylists()
    }

    private fun loadPlaylists() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            playlistRepository.getAllPlaylistsWithSongs()
                .catch { e ->
                    Log.e(TAG, "Error loading playlists", e)
                    emit(emptyList())
                }
                .flowOn(Dispatchers.IO)
                .collect { list ->
                    rawList = list
                    _playlists.value = list.sorted()
                    Log.d(TAG, "Playlists loaded: ${list.size}")
                }
        }
    }

    private fun resort() {
        viewModelScope.launch(Dispatchers.Default) {
            _playlists.value = rawList.sorted()
        }
    }

    /**
     * Sorts the raw playlist-with-songs list according to the current [PlaylistPreferences]
     * sort field and direction, returning the sorted copy.
     */
    private fun List<PlaylistWithSongs>.sorted(): List<PlaylistWithSongs> {
        val ascending = PlaylistPreferences.getSortingStyle() == CommonPreferencesConstants.ASCENDING
        return when (PlaylistPreferences.getSongSort()) {
            CommonPreferencesConstants.BY_NAME ->
                if (ascending) sortedBy { it.playlist.name.lowercase() }
                else sortedByDescending { it.playlist.name.lowercase() }

            CommonPreferencesConstants.BY_DATE_ADDED ->
                if (ascending) sortedBy { it.playlist.dateCreated }
                else sortedByDescending { it.playlist.dateCreated }

            CommonPreferencesConstants.BY_DATE_MODIFIED ->
                if (ascending) sortedBy { it.playlist.dateModified }
                else sortedByDescending { it.playlist.dateModified }

            CommonPreferencesConstants.BY_NUMBER_OF_SONGS ->
                if (ascending) sortedBy { it.songs.size }
                else sortedByDescending { it.songs.size }

            else ->
                if (ascending) sortedBy { it.playlist.name.lowercase() }
                else sortedByDescending { it.playlist.name.lowercase() }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, s: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, s)
        when (s) {
            PlaylistPreferences.SONG_SORT,
            PlaylistPreferences.SORTING_STYLE -> resort()
        }
    }

    companion object {
        private const val TAG = "PlaylistsViewModel"
    }
}

