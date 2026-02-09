package app.simple.felicity.viewmodels.viewer

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.models.CollectionPageData
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.repositories.AlbumRepository
import app.simple.felicity.repository.repositories.ArtistRepository
import app.simple.felicity.repository.repositories.GenreRepository
import app.simple.felicity.repository.repositories.SongRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = AlbumViewerViewModel.Factory::class)
class AlbumViewerViewModel @AssistedInject constructor(
        @Assisted private val album: Album,
        private val artistRepository: ArtistRepository,
        private val songRepository: SongRepository,
        private val genreRepository: GenreRepository,
        private val albumRepository: AlbumRepository) : ViewModel() {

    private val data: MutableLiveData<CollectionPageData> by lazy {
        MutableLiveData<CollectionPageData>().also {
            loadArtistSongs()
        }
    }

    fun getData(): LiveData<CollectionPageData> {
        return data
    }

    private fun loadArtistSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            // TODO - optimize time here
            val songs = songRepository.fetchSongsByAlbum(album.id)
            Log.d(TAG, "loadArtistSongs: Fetched ${songs.size} songs for album: ${album.name} in ${System.currentTimeMillis() - startTime} ms")
            val genres = genreRepository.fetchGenresForAlbum(album.id)
            Log.d(TAG, "loadArtistSongs: Fetched ${genres.size} genres for album: ${album.name} in ${System.currentTimeMillis() - startTime} ms")
            val artists = artistRepository.fetchAlbumArtists(album.id)
            Log.d(TAG, "loadArtistSongs: Fetched ${artists.size} artists for album: ${album.name} in ${System.currentTimeMillis() - startTime} ms")

            data.postValue(CollectionPageData(
                    songs = songs,
                    genres = genres,
                    artists = artists,
            ))
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(album: Album): AlbumViewerViewModel
    }

    companion object {
        private const val TAG = "AlbumViewerViewModel"
    }
}