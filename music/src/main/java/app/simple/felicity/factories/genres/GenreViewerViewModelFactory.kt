package app.simple.felicity.factories.genres

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.viewmodels.main.genres.GenreViewerViewModel

class GenreViewerViewModelFactory(private val genre: Genre) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!

        when {
            modelClass.isAssignableFrom(GenreViewerViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                return GenreViewerViewModel(application, genre) as T
            }
            else -> {
                throw IllegalArgumentException("Nope, Wrong Viewmodel!!")
            }
        }
    }
}