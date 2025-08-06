package app.simple.felicity.viewmodels.main.genres

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.models.CollectionPageData
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.repositories.ArtistRepository
import app.simple.felicity.repository.repositories.GenreRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = GenreViewerViewModel.Factory::class)
class GenreViewerViewModel @AssistedInject constructor(
        @Assisted val genre: Genre,
        private val genreRepository: GenreRepository,
        private val artistRepository: ArtistRepository
) : ViewModel() {

    private val data: MutableLiveData<CollectionPageData> by lazy {
        MutableLiveData<CollectionPageData>().also {
            loadGenreSongs()
        }
    }

    fun getData(): LiveData<CollectionPageData> {
        return data
    }

    private fun loadGenreSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val genreSongs = genreRepository.fetchGenreSongs(genre.id)
            val albums = genreRepository.fetchAlbumsInGenre(genre.id)
            val artists = genreRepository.fetchArtistsInGenre(genre.id).mapNotNull {
                artistRepository.fetchArtistDetails(it)
            }

            data.postValue(CollectionPageData(genreSongs, albums, artists))
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(genre: Genre): GenreViewerViewModel
    }

    companion object {

    }
}