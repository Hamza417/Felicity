package app.simple.felicity.viewmodels.viewer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.PageData
import app.simple.felicity.repository.models.YearGroup
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.sort.PageSort.sortedForYearPage
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

@HiltViewModel(assistedFactory = YearViewerViewModel.Factory::class)
class YearViewerViewModel @AssistedInject constructor(
        @Assisted private val yearGroup: YearGroup,
        private val audioRepository: AudioRepository,
) : ViewModel() {

    private val _data = MutableStateFlow<PageData?>(null)
    val data: StateFlow<PageData?> = _data.asStateFlow()

    /** Raw unsorted songs fetched from the repository. Re-sorting does not require a DB trip. */
    private var rawSongs: List<Audio> = emptyList()

    init {
        loadYearData()
    }

    private fun loadYearData() {
        viewModelScope.launch {
            audioRepository.getYearPageData(yearGroup)
                .catch { exception ->
                    Log.e(TAG, "Error loading year data for: ${yearGroup.year}", exception)
                    emit(PageData())
                }
                .flowOn(Dispatchers.IO)
                .collect { pageData ->
                    Log.d(TAG, "loadYearData: Loaded ${pageData.songs.size} songs for year: ${yearGroup.year}")
                    rawSongs = pageData.songs
                    _data.value = pageData.copy(songs = rawSongs.sortedForYearPage())
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
            _data.value = current.copy(songs = rawSongs.sortedForYearPage())
            Log.d(TAG, "resort: re-sorted ${rawSongs.size} songs for year page")
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(yearGroup: YearGroup): YearViewerViewModel
    }

    companion object {
        private const val TAG = "YearViewerViewModel"
    }
}
