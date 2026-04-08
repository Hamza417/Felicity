package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.PlaylistPreferences
import app.simple.felicity.repository.models.Playlist
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
 * Loads and sorts playlists from [PlaylistRepository] using [PlaylistPreferences]-driven
 * sort/order without a round-trip to the database when only the sort changes.
 *
 * @author Hamza417
 */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
        application: Application,
        private val playlistRepository: PlaylistRepository
) : WrappedViewModel(application) {

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    /** Cached raw list kept for cheap in-memory re-sorts. */
    private var rawList: List<Playlist> = emptyList()

    private var loadJob: Job? = null

    init {
        loadPlaylists()
    }

    private fun loadPlaylists() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            playlistRepository.getAllPlaylistsPinned()
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
            Log.d(TAG, "resort: ${_playlists.value.size} playlists re-sorted")
        }
    }

    /**
     * Sorts the raw playlist list according to the current [PlaylistPreferences] sort
     * field and direction, returning the sorted copy.
     */
    private fun List<Playlist>.sorted(): List<Playlist> {
        val ascending = PlaylistPreferences.getSortingStyle() == CommonPreferencesConstants.ASCENDING
        return when (PlaylistPreferences.getSongSort()) {
            CommonPreferencesConstants.BY_NAME -> if (ascending) sortedBy { it.name.lowercase() }
            else sortedByDescending { it.name.lowercase() }

            CommonPreferencesConstants.BY_DATE_ADDED -> if (ascending) sortedBy { it.dateCreated }
            else sortedByDescending { it.dateCreated }

            CommonPreferencesConstants.BY_DATE_MODIFIED -> if (ascending) sortedBy { it.dateModified }
            else sortedByDescending { it.dateModified }

            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> this // Count sorting is done post-load

            else -> if (ascending) sortedBy { it.name.lowercase() }
            else sortedByDescending { it.name.lowercase() }
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

