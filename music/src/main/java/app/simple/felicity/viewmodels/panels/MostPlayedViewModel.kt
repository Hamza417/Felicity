package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.models.AudioWithStat
import app.simple.felicity.repository.repositories.SongStatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Most Played panel.
 *
 * <p>Observes the {@code song_stats} table via [SongStatRepository] and emits the most
 * frequently played available songs ordered by play count descending. Each item is an
 * [AudioWithStat] so the UI can display the total number of times each song was played.</p>
 *
 * @author Hamza417
 */
@HiltViewModel
class MostPlayedViewModel @Inject constructor(
        application: Application,
        private val songStatRepository: SongStatRepository
) : WrappedViewModel(application) {

    private val _songs = MutableStateFlow<List<AudioWithStat>>(emptyList())
    val songs: StateFlow<List<AudioWithStat>> = _songs.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            songStatRepository.getMostPlayedWithStat()
                .catch { e ->
                    Log.e(TAG, "Error loading most played songs", e)
                    emit(emptyList())
                }
                .flowOn(Dispatchers.IO)
                .collect { list ->
                    _songs.value = list
                    Log.d(TAG, "loadData: ${list.size} most played songs loaded")
                }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            LibraryPreferences.MINIMUM_AUDIO_SIZE,
            LibraryPreferences.MINIMUM_AUDIO_LENGTH -> loadData()
        }
    }

    companion object {
        private const val TAG = "MostPlayedViewModel"
    }
}
