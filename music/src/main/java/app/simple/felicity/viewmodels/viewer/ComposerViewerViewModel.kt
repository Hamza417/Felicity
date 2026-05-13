package app.simple.felicity.viewmodels.viewer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.MusicBrainzArtistInfo
import app.simple.felicity.repository.models.PageData
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.repositories.MusicBrainzRepository
import app.simple.felicity.repository.sort.PageSort.sortedForComposerPage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * Loads and manages all data displayed on the Composer page. It fetches songs,
 * albums, and genres associated with the given composer from the repository and
 * keeps the list sorted according to the current page sort preference. Re-sorting
 * happens in memory without touching the database again.
 *
 * @author Hamza417
 */
@HiltViewModel(assistedFactory = ComposerViewerViewModel.Factory::class)
class ComposerViewerViewModel @AssistedInject constructor(
        @Assisted private val composer: Artist,
        private val audioRepository: AudioRepository,
        private val musicBrainzRepository: MusicBrainzRepository
) : ViewModel() {

    private val _data = MutableStateFlow<PageData?>(null)
    val data: StateFlow<PageData?> = _data.asStateFlow()

    private val _artistInfo = MutableStateFlow<MusicBrainzArtistInfo?>(null)

    /**
     * Profile fetched from MusicBrainz for this composer — bio, genre tags,
     * country, active years, etc. Stays null while the fetch is running or when
     * no matching entry exists in MusicBrainz.
     */
    val artistInfo: StateFlow<MusicBrainzArtistInfo?> = _artistInfo.asStateFlow()

    /** Unsorted songs stored so re-sorting never needs a database round-trip. */
    private var rawSongs: List<Audio> = emptyList()

    init {
        loadComposerData()
        loadArtistInfo()
    }

    private fun loadComposerData() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()

            audioRepository.getComposerPageData(composer)
                .catch { exception ->
                    Log.e(TAG, "Error loading composer data", exception)
                    emit(PageData())
                }
                .flowOn(Dispatchers.IO)
                .collect { pageData ->
                    val loadTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "loadComposerData: Loaded data for composer: ${composer.name}")
                    Log.d(TAG, "  - Songs: ${pageData.songs.size}")
                    Log.d(TAG, "  - Albums: ${pageData.albums.size}")
                    Log.d(TAG, "  - Genres: ${pageData.genres.size}")
                    Log.d(TAG, "  - Load time: $loadTime ms")

                    rawSongs = pageData.songs
                    _data.value = pageData.copy(songs = rawSongs.sortedForComposerPage())
                }
        }
    }

    /**
     * Re-sorts the cached song list using the current [app.simple.felicity.preferences.PagePreferences]
     * and re-emits [PageData] without hitting the database.
     */
    fun resort() {
        val current = _data.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            _data.value = current.copy(songs = rawSongs.sortedForComposerPage())
            Log.d(TAG, "resort: re-sorted ${rawSongs.size} songs for composer page")
        }
    }

    /**
     * Kicks off a background fetch of the composer's MusicBrainz profile.
     * The result lands in [artistInfo] once ready; until then it stays null.
     * If the user has disabled MusicBrainz in Library preferences, no request is made.
     */
    private fun loadArtistInfo() {
        if (!LibraryPreferences.isMusicBrainzEnabled()) {
            Log.d(TAG, "MusicBrainz is disabled, skipping fetch for: ${composer.name}")
            return
        }
        val name = composer.name ?: return
        viewModelScope.launch {
            val info = musicBrainzRepository.fetchArtistInfo(name)
            _artistInfo.value = info
            if (info != null) {
                Log.d(TAG, "Loaded MusicBrainz info for: $name (type=${info.type}, country=${info.country})")
            } else {
                Log.d(TAG, "No MusicBrainz info available for: $name")
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(composer: Artist): ComposerViewerViewModel
    }

    companion object {
        private const val TAG = "ComposerViewerViewModel"
    }
}
