package app.simple.felicity.viewmodels.main.genres

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.repositories.GenreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GenresViewModel(application: Application) : WrappedViewModel(application) {

    private val genreRepository by lazy {
        GenreRepository(applicationContext())
    }

    private val genres: MutableLiveData<List<Genre>> by lazy {
        MutableLiveData<List<Genre>>().also {
            setGenres()
        }
    }

    fun getGenresData(): LiveData<List<Genre>> {
        return genres
    }

    private fun setGenres() {
        viewModelScope.launch(Dispatchers.IO) {
            genres.postValue(genreRepository.fetchGenres())
        }
    }

    fun refreshGenresData() {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedGenres = genreRepository.fetchGenres()
            genres.postValue(updatedGenres)
        }
    }

    companion object {
        private const val TAG = "GenresViewModel"
    }
}