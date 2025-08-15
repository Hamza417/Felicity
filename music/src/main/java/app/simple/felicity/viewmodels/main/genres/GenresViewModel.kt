package app.simple.felicity.viewmodels.main.genres

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.repositories.GenreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GenresViewModel @Inject constructor(private val genreRepository: GenreRepository) : ViewModel() {

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
            Log.d(TAG, "Fetching genres from repository")
            genres.postValue(genreRepository.fetchGenres())
        }
    }

    companion object {
        private const val TAG = "GenresViewModel"
    }
}