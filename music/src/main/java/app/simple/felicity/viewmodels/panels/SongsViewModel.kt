package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.SongsPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.sort.SongSort.sorted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SongsViewModel @Inject constructor(
        application: Application,
        private val audioRepository: AudioRepository) : WrappedViewModel(application) {

    private var carouselPosition = 0

    private val _songs = MutableStateFlow<List<Audio>>(emptyList())
    val songs: StateFlow<List<Audio>> = _songs.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            audioRepository.getAllAudio()
                .map { audioList -> audioList.sorted() }
                .catch { exception ->
                    Log.e(TAG, "Error loading songs", exception)
                    emit(emptyList())
                }
                .flowOn(Dispatchers.IO)
                .collect { sortedSongs ->
                    _songs.value = sortedSongs
                    Log.d(TAG, "loadData: ${sortedSongs.size} songs loaded")
                }
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
            SongsPreferences.SONG_SORT, SongsPreferences.SORTING_STYLE -> {
                loadData()
            }
        }
    }

    companion object {
        private const val TAG = "SongsViewModel"
    }
}