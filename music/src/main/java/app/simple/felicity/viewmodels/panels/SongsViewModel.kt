package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.preferences.SongsPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.sort.SongSort.sorted
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

@HiltViewModel
class SongsViewModel @Inject constructor(
        application: Application,
        private val audioRepository: AudioRepository) : WrappedViewModel(application) {

    private var carouselPosition = 0

    private val _songs = MutableStateFlow<List<Audio>>(emptyList())
    val songs: StateFlow<List<Audio>> = _songs.asStateFlow()

    /** Raw unordered list from the database, kept so we can re-sort without a DB round-trip. */
    private var rawAudioList: List<Audio> = emptyList()

    private var loadJob: Job? = null

    init {
        loadData()
    }

    private fun loadData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            audioRepository.getAllAudio()
                .catch { exception ->
                    Log.e(TAG, "Error loading songs", exception)
                    emit(mutableListOf())
                }
                .flowOn(Dispatchers.IO)
                .collect { audioList ->
                    rawAudioList = audioList
                    val sortedSongs = audioList.sorted()
                    _songs.value = sortedSongs
                    Log.d(TAG, "loadData: ${sortedSongs.size} songs loaded")
                }
        }
    }

    private fun resort() {
        viewModelScope.launch(Dispatchers.Default) {
            val sortedSongs = rawAudioList.sorted()
            _songs.value = sortedSongs
            Log.d(TAG, "resort: ${sortedSongs.size} songs re-sorted")
        }
    }

    fun setCarouselPosition(position: Int) {
        carouselPosition = position
    }

    fun getCarouselPosition(): Int {
        return carouselPosition
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, s: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, s)
        when (s) {
            SongsPreferences.SONG_SORT,
            SongsPreferences.SORTING_STYLE -> {
                // Only the sort order changed — re-sort the cached list, no DB query needed.
                resort()
            }
            LibraryPreferences.MINIMUM_AUDIO_SIZE,
            LibraryPreferences.MINIMUM_AUDIO_LENGTH -> {
                // Filter criteria changed — need a fresh query from the database.
                loadData()
            }
        }
    }

    companion object {
        private const val TAG = "SongsViewModel"
    }
}