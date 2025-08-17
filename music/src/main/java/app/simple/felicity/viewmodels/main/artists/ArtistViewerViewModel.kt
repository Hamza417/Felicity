package app.simple.felicity.viewmodels.main.artists

import android.app.Application
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.models.CollectionPageData
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Song
import app.simple.felicity.repository.repositories.AlbumRepository
import app.simple.felicity.repository.repositories.ArtistRepository
import app.simple.felicity.repository.repositories.GenreRepository
import app.simple.felicity.repository.repositories.SongRepository
import app.simple.felicity.repository.utils.SongUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ArtistViewerViewModel.Factory::class)
class ArtistViewerViewModel @AssistedInject constructor(
        @Assisted private val artist: Artist,
        private val artistRepository: ArtistRepository,
        private val songRepository: SongRepository,
        private val genreRepository: GenreRepository,
        private val albumRepository: AlbumRepository,
        application: Application) : WrappedViewModel(application) {

    private val data: MutableLiveData<CollectionPageData> by lazy {
        MutableLiveData<CollectionPageData>().also {
            loadArtistSongs()
        }
    }

    private val imageUris: MutableLiveData<List<Uri>> by lazy {
        MutableLiveData<List<Uri>>()
    }

    fun getData(): LiveData<CollectionPageData> {
        return data
    }

    fun getImageUris(): LiveData<List<Uri>> {
        return imageUris
    }

    private fun loadArtistSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = songRepository.fetchSongByArtist(artist.id)
            val albums = albumRepository.fetchAlbumsFromArtist(artist.id)
            val genres = genreRepository.fetchGenreByArtist(artist.id)
            val artists = artistRepository.fetchCollaboratorArtists(artist)

            data.postValue(CollectionPageData(
                    songs = songs,
                    albums = albums,
                    genres = genres,
                    artists = artists,
            ))

            loadSongImages(songs)
        }
    }

    private fun loadSongImages(songs: List<Song>) {
        val uris = songs.mapNotNull { song ->
            SongUtils.getArtworkUri(getApplication(), song.albumId, song.id)
        }.distinct()

        imageUris.postValue(uris)
    }

    @AssistedFactory
    interface Factory {
        fun create(artist: Artist): ArtistViewerViewModel
    }

    companion object {
        private const val TAG = "ArtistViewerViewModel"
    }
}