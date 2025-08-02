package app.simple.felicity.viewmodels.main.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.repository.repositories.AlbumRepository
import app.simple.felicity.repository.repositories.GenreRepository
import app.simple.felicity.repository.repositories.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
        application: Application,
        private val songRepository: SongRepository,
        private val albumRepository: AlbumRepository,
        private val genreRepository: GenreRepository) : WrappedViewModel(application) {

    private val _data: MutableSharedFlow<List<ArtFlowData<Any>>> = MutableSharedFlow(replay = 1)
    val data: SharedFlow<List<ArtFlowData<Any>>> = _data.asSharedFlow()

    init {
        Log.d(TAG, "HomeViewModel initialized")
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Loading data...")

            val songs = songRepository.fetchSongs().shuffled().take(TAKE_COUNT)
            val albums = albumRepository.fetchAlbums().shuffled().take(TAKE_COUNT)
            val genres = genreRepository.fetchGenres().shuffled().take(TAKE_COUNT)
            val recentlyAdded = songRepository.fetchRecentSongs(TAKE_COUNT)

            Log.d(TAG, "Songs count: ${songs.size}, Albums count: ${albums.size}")

            val artFlowData = mutableListOf<ArtFlowData<Any>>()
            artFlowData.add(ArtFlowData(R.string.songs, songs))
            artFlowData.add(ArtFlowData(R.string.albums, albums))
            artFlowData.add(ArtFlowData(R.string.genres, genres))
            if (recentlyAdded.isNotEmpty()) {
                artFlowData.add(ArtFlowData(R.string.recently_added, recentlyAdded))
            }

            _data.emit(artFlowData)
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
        private const val TAKE_COUNT = 18
    }
}
