package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.AlbumArtistPreferences
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.sort.AlbumArtistSort.sortedAlbumArtists
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
 * Keeps track of the album artists list for the Album Artists panel.
 * Loads data from the repository, sorts it by the saved preference,
 * and re-loads whenever something relevant changes (library filter, sort order, etc.).
 *
 * @author Hamza417
 */
@HiltViewModel
class AlbumArtistsViewModel @Inject constructor(
        application: Application,
        private val audioRepository: AudioRepository
) : WrappedViewModel(application) {

    private val _albumArtists = MutableStateFlow<MutableList<Artist>>(mutableListOf())
    val albumArtists: StateFlow<MutableList<Artist>> = _albumArtists.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            audioRepository.getAllAlbumArtistsWithAggregation()
                .map { list -> list.sortedAlbumArtists() }
                .distinctUntilChanged()
                .catch { exception ->
                    Log.e(TAG, "Error loading album artists", exception)
                    emit(emptyList())
                }
                .flowOn(Dispatchers.IO)
                .collect { sorted ->
                    _albumArtists.value = sorted.toMutableList()
                    Log.d(TAG, "loadData: ${sorted.size} album artists loaded")
                }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            AlbumArtistPreferences.ALBUM_ARTIST_SORT,
            AlbumArtistPreferences.SORTING_STYLE,
            LibraryPreferences.MINIMUM_AUDIO_SIZE,
            LibraryPreferences.MINIMUM_AUDIO_LENGTH -> {
                loadData()
            }
        }
    }

    companion object {
        private const val TAG = "AlbumArtistsViewModel"
    }
}

