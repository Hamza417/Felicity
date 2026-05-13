package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.ComposerPreferences
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.sort.ComposerSort.sortedComposers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps track of the composers list for the Composers panel. Loads data from the
 * repository, sorts it by the saved preference, and re-loads whenever the library
 * filter or sort order changes.
 *
 * @author Hamza417
 */
@HiltViewModel
class ComposersViewModel @Inject constructor(
        application: Application,
        private val audioRepository: AudioRepository
) : WrappedViewModel(application) {

    private val _composers = MutableStateFlow<MutableList<Artist>>(mutableListOf())
    val composers: StateFlow<MutableList<Artist>> = _composers.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            audioRepository.getAllComposersWithAggregation()
                .map { list -> list.sortedComposers() }
                .distinctUntilChanged()
                .catch { exception ->
                    Log.e(TAG, "Error loading composers", exception)
                    emit(emptyList())
                }
                .flowOn(Dispatchers.IO)
                .collect { sorted ->
                    _composers.value = sorted.toMutableList()
                    Log.d(TAG, "loadData: ${sorted.size} composers loaded")
                }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            ComposerPreferences.COMPOSER_SORT,
            ComposerPreferences.SORTING_STYLE,
            LibraryPreferences.MINIMUM_AUDIO_SIZE,
            LibraryPreferences.MINIMUM_AUDIO_LENGTH -> {
                loadData()
            }
        }
    }

    companion object {
        private const val TAG = "ComposersViewModel"
    }
}

