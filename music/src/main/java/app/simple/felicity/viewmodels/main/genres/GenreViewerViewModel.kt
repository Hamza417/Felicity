package app.simple.felicity.viewmodels.main.genres

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song
import app.simple.felicity.repository.repositories.GenreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GenreViewerViewModel(application: Application, private val genre: Genre) : WrappedViewModel(application) {

    private val genreRepository by lazy {
        GenreRepository(application)
    }

    private val songs: MutableLiveData<List<Song>> by lazy {
        MutableLiveData<List<Song>>().also {
            loadGenreSongs()
        }
    }

    fun getSongs(): LiveData<List<Song>> {
        return songs
    }

    private fun loadGenreSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val genreSongs = genreRepository.fetchGenreSongs(genre.id)
            songs.postValue(genreSongs)
        }
    }
}