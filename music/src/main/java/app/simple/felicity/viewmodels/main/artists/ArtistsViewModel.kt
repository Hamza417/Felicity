package app.simple.felicity.viewmodels.main.artists

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.ArtistPreferences
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.repositories.ArtistRepository
import app.simple.felicity.repository.sort.ArtistSort.sorted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistsViewModel @Inject constructor(
        application: Application,
        private val artistRepository: ArtistRepository) : WrappedViewModel(application) {

    private val albums: MutableLiveData<List<Artist>> by lazy {
        MutableLiveData<List<Artist>>().also {
            fetchArtists()
        }
    }

    fun getArtists(): LiveData<List<Artist>> {
        return albums
    }

    private fun fetchArtists() {
        viewModelScope.launch(Dispatchers.IO) {
            albums.postValue(artistRepository.fetchArtists().sorted())
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            ArtistPreferences.ARTIST_SORT, ArtistPreferences.SORTING_STYLE -> {
                fetchArtists()
            }
        }
    }
}
