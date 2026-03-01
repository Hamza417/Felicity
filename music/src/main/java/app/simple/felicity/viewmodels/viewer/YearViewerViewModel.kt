package app.simple.felicity.viewmodels.viewer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.repository.models.PageData
import app.simple.felicity.repository.models.YearGroup
import app.simple.felicity.repository.repositories.AudioRepository
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
                    _data.value = pageData
                }
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

