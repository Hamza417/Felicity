package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.PlaylistPreferences
import app.simple.felicity.repository.models.PlaylistWithSongs
import app.simple.felicity.repository.repositories.M3uRepository
import app.simple.felicity.repository.repositories.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
 * separate query. Sorting is performed in-memory to avoid round-trips.</p>
 *
 * <p>Also handles importing M3U playlist files via [importM3u], delegating the
 * actual file parsing and database work to [M3uRepository]. The result is surfaced
 * through [importResult] so the UI can react — either by showing a success message
 * or by handling any errors gracefully.</p>
 *
 * @author Hamza417
 */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
        application: Application,
        private val playlistRepository: PlaylistRepository,
        private val m3uRepository: M3uRepository
) : WrappedViewModel(application) {

    /**
     * Represents the outcome of an M3U import attempt, whether it went well or
     * decided to throw a tantrum.
     */
    sealed class ImportState {
        /** The import is currently in progress — please hold. */
        object Loading : ImportState()

        /**
         * The import finished successfully.
         *
         * @param result Details about what was imported.
         */
        data class Success(val result: M3uRepository.ImportResult) : ImportState()

        /**
         * Something went wrong during the import.
         *
         * @param error The exception that caused the failure.
         */
        data class Error(val error: Throwable) : ImportState()
    }

    private val _playlists = MutableStateFlow<List<PlaylistWithSongs>>(emptyList())
    val playlists: StateFlow<List<PlaylistWithSongs>> = _playlists.asStateFlow()

    /** Cached raw list kept for cheap in-memory re-sorts. */
    private var rawList: List<PlaylistWithSongs> = emptyList()

    private var loadJob: Job? = null

    private val _importResult = MutableSharedFlow<ImportState>(extraBufferCapacity = 1)

    /**
     * Emits [ImportState] events during and after an M3U import. The UI should
     * observe this to show progress indicators and result messages.
     */
    val importResult: SharedFlow<ImportState> = _importResult.asSharedFlow()

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
     * Kicks off an M3U import from the given file URI. The result lands in
     * [importResult] as an [ImportState] — [ImportState.Loading] first, then
     * [ImportState.Success] or [ImportState.Error] depending on what happened.
     *
     * <p>This is safe to call from the main thread; the heavy lifting runs on
     * the IO dispatcher inside [M3uRepository].</p>
     *
     * @param uri The content URI of the M3U file chosen by the user.
     */
    fun importM3u(uri: Uri) {
        viewModelScope.launch {
            _importResult.emit(ImportState.Loading)
            val result = m3uRepository.importFromUri(uri)
            result.fold(
                    onSuccess = { importData ->
                        Log.d(TAG, "M3U import succeeded: ${importData.playlistName}, ${importData.totalTracks} tracks")
                        _importResult.emit(ImportState.Success(importData))
                    },
                    onFailure = { error ->
                        Log.e(TAG, "M3U import failed", error)
                        _importResult.emit(ImportState.Error(error))
                    }
            )
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
