package app.simple.felicity.viewmodels.viewer

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.PageData
import app.simple.felicity.repository.models.Song
import app.simple.felicity.repository.repositories.ArtistRepository
import app.simple.felicity.repository.repositories.GenreRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = GenreViewerViewModel.Factory::class)
class GenreViewerViewModel @AssistedInject constructor(
        @Assisted val genre: Genre,
        private val genreRepository: GenreRepository,
        private val artistRepository: ArtistRepository
) : ViewModel() {

    private val data: MutableLiveData<PageData> by lazy {
        MutableLiveData<PageData>().also {
            loadGenreSongs()
        }
    }

    fun getData(): LiveData<PageData> {
        return data
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadGenreSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()

            val genreSongsDeferred = async { genreRepository.fetchGenreSongs(genre.id) }
            val albumsDeferred = async { genreRepository.fetchAlbumsInGenre(genre.id) }
            val artistsDeferred = async {
                val artistIds = genreRepository.fetchArtistsInGenre(genre.id).toSet()
                artistRepository.fetchArtists().filter { it.id in artistIds }
            }

            val results = awaitAll(
                    genreSongsDeferred,
                    albumsDeferred,
                    artistsDeferred
            )

            val genreSongs = results[0] as List<Song>
            val albums = results[1] as List<Album>
            val artists = results[2] as List<Artist>

            val end = System.currentTimeMillis()
            Log.d("GenreViewerViewModel", "All fetches took ${end - start} ms")

            // data.postValue(CollectionPageData(genreSongs, albums, artists))
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(genre: Genre): GenreViewerViewModel
    }

    companion object {

    }
}