package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.AlbumPreferences
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.sort.AlbumSort.sorted
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
class AlbumsViewModel @Inject constructor(
        application: Application,
        private val audioRepository: AudioRepository
) : WrappedViewModel(application) {

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            audioRepository.getAllAlbumsWithAggregation()
                .map { albumList -> albumList.sorted() }
                .distinctUntilChanged()  // Prevent identical consecutive emissions
                .catch { exception ->
                    Log.e(TAG, "Error loading albums", exception)
                    emit(emptyList())
                }
                .flowOn(Dispatchers.IO)
                .collect { sortedAlbums ->
                    _albums.value = sortedAlbums
                    Log.d(TAG, "loadData: ${sortedAlbums.size} albums loaded")
                }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            AlbumPreferences.ALBUM_SORT, AlbumPreferences.SORTING_STYLE -> {
                loadData()
            }
        }
    }

    companion object {
        private const val TAG = "AlbumsViewModel"
    }
}