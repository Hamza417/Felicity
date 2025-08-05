package app.simple.felicity.viewmodels.main.artists

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.models.CollectionPageData
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.repositories.AlbumRepository
import app.simple.felicity.repository.repositories.ArtistRepository
import app.simple.felicity.repository.repositories.GenreRepository
import app.simple.felicity.repository.repositories.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistViewerViewModel @Inject constructor(
        savedStateHandle: SavedStateHandle,
        private val artistRepository: ArtistRepository,
        private val songRepository: SongRepository,
        private val genreRepository: GenreRepository,
        private val albumRepository: AlbumRepository) : ViewModel() {

    private val artist: Artist by lazy {
        savedStateHandle[BundleConstants.ARTIST]
            ?: throw IllegalArgumentException("Artist not found in saved state")
    }

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
            val songs = songRepository.fetchSongByArtist(artist.id)
            val albums = albumRepository.fetchAlbumsFromArtist(artist.id)
            val genres = genreRepository.fetchGenreByArtist(artist.id)
            val artists = artistRepository.fetchOtherArtistsForCollaboration(artist)

            data.postValue(CollectionPageData(
                    songs = songs,
                    albums = albums,
                    genres = genres,
                    artists = artists,
            ))
        }
    }

    companion object {
        private const val TAG = "ArtistViewerViewModel"
    }
}