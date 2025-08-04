package app.simple.felicity.viewmodels.main.genres

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song
import app.simple.felicity.repository.repositories.ArtistRepository
import app.simple.felicity.repository.repositories.GenreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GenreViewerViewModel(application: Application, private val genre: Genre) : WrappedViewModel(application) {

    private val genreRepository by lazy {
        GenreRepository(application)
    }

    private val artistRepository by lazy {
        ArtistRepository(application)
    }

    private val data: MutableLiveData<GenreData> by lazy {
        MutableLiveData<GenreData>().also {
            loadGenreSongs()
        }
    }

    fun getData(): LiveData<GenreData> {
        return data
    }

    private fun loadGenreSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val genreSongs = genreRepository.fetchGenreSongs(genre.id)
            val albums = genreRepository.fetchAlbumsInGenre(genre.id)
            val artists = genreRepository.fetchArtistsInGenre(genre.id).mapNotNull {
                artistRepository.fetchArtistDetails(it)
            }

            data.postValue(GenreData(genreSongs, albums, artists))
        }
    }

    companion object {
        data class GenreData(
                val songs: List<Song>,
                val albums: List<Album>,
                val artists: List<Artist>
        ) {
            override fun toString(): String {
                return "GenreSongs(songs=$songs, albums=$albums)"
            }
        }
    }
}