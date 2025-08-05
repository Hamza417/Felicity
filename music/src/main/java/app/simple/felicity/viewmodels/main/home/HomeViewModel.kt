package app.simple.felicity.viewmodels.main.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.repository.repositories.AlbumRepository
import app.simple.felicity.repository.repositories.ArtistRepository
import app.simple.felicity.repository.repositories.GenreRepository
import app.simple.felicity.repository.repositories.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
        application: Application,
        private val songRepository: SongRepository,
        private val albumRepository: AlbumRepository,
        private val genreRepository: GenreRepository,
        private val artistRepository: ArtistRepository) : WrappedViewModel(application) {

    private val data: MutableLiveData<List<ArtFlowData<Any>>> by lazy {
        MutableLiveData<List<ArtFlowData<Any>>>().also {
            Log.d(TAG, "LiveData initialized")
            loadData()
        }
    }

    fun getData(): LiveData<List<ArtFlowData<Any>>> {
        return data
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Loading data...")

            val songs = songRepository.fetchSongs().shuffled().take(TAKE_COUNT)
            val albums = albumRepository.fetchAlbums().shuffled().take(TAKE_COUNT)
            val artists = artistRepository.fetchArtists().shuffled().take(TAKE_COUNT)
            val genres = genreRepository.fetchGenres().shuffled().take(TAKE_COUNT)
            val recentlyAdded = songRepository.fetchRecentSongs(TAKE_COUNT)

            Log.d(TAG, "Songs count: ${songs.size}, Albums count: ${albums.size}")

            val artFlowData = mutableListOf<ArtFlowData<Any>>()
            artFlowData.add(ArtFlowData(R.string.songs, songs))
            artFlowData.add(ArtFlowData(R.string.albums, albums))
            artFlowData.add(ArtFlowData(R.string.artists, artists))
            artFlowData.add(ArtFlowData(R.string.genres, genres))
            if (recentlyAdded.isNotEmpty()) {
                artFlowData.add(ArtFlowData(R.string.recently_added, recentlyAdded))
            }

            Log.d(TAG, "Data loaded successfully")
            data.postValue(artFlowData)
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
        private const val TAKE_COUNT = 18
    }
}
