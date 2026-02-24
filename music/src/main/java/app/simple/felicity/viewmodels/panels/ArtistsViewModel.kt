package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.ArtistPreferences
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.sort.ArtistSort.sorted
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

@HiltViewModel
class ArtistsViewModel @Inject constructor(
        application: Application,
        private val audioRepository: AudioRepository
) : WrappedViewModel(application) {

    private val _artists = MutableStateFlow<MutableList<Artist>>(mutableListOf())
    val artists: StateFlow<MutableList<Artist>> = _artists.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            audioRepository.getAllArtistsWithAggregation()
                .map { artistList -> artistList.sorted() }
                .distinctUntilChanged()  // Prevent identical consecutive emissions
                .catch { exception ->
                    Log.e(TAG, "Error loading artists", exception)
                    emit(emptyList())
                }
                .flowOn(Dispatchers.IO)
                .collect { sortedArtists ->
                    _artists.value = sortedArtists as MutableList<Artist>
                    Log.d(TAG, "loadData: ${sortedArtists.size} artists loaded")
                }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            ArtistPreferences.ARTIST_SORT,
            ArtistPreferences.SORTING_STYLE,
            LibraryPreferences.MINIMUM_AUDIO_SIZE,
            LibraryPreferences.MINIMUM_AUDIO_LENGTH -> {
                loadData()
            }
        }
    }

    companion object {
        private const val TAG = "ArtistsViewModel"
    }
}