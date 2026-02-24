package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.models.ArtFlowData
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.repositories.AudioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
        application: Application,
        private val audioRepository: AudioRepository) : WrappedViewModel(application) {

    private val data: MutableLiveData<List<ArtFlowData<Any>>> by lazy {
        MutableLiveData<List<ArtFlowData<Any>>>().also {
            Log.d(TAG, "LiveData initialized")
            loadData()
        }
    }

    fun getData(): LiveData<List<ArtFlowData<Any>>> {
        return data
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Loading data...")

            try {
                val songsDeferred = async {
                    audioRepository.getAllAudio()
                        .catch { e -> Log.e(TAG, "Error loading songs", e); emit(mutableListOf()) }
                        .flowOn(Dispatchers.IO)
                        .first()
                        .shuffled()
                        .take(TAKE_COUNT)
                }

                val albumsDeferred = async {
                    audioRepository.getAllAlbumsWithAggregation()
                        .catch { e -> Log.e(TAG, "Error loading albums", e); emit(emptyList()) }
                        .flowOn(Dispatchers.IO)
                        .first()
                        .shuffled()
                        .take(TAKE_COUNT)
                }

                val artistsDeferred = async {
                    audioRepository.getAllArtistsWithAggregation()
                        .catch { e -> Log.e(TAG, "Error loading artists", e); emit(emptyList()) }
                        .flowOn(Dispatchers.IO)
                        .first()
                        .shuffled()
                        .take(TAKE_COUNT)
                }

                val genresDeferred = async {
                    audioRepository.getAllGenresWithAggregation()
                        .catch { e -> Log.e(TAG, "Error loading genres", e); emit(emptyList()) }
                        .flowOn(Dispatchers.IO)
                        .first()
                        .shuffled()
                        .take(TAKE_COUNT)
                }

                val songs = songsDeferred.await()
                val albums = albumsDeferred.await()
                val artists = artistsDeferred.await()
                val genres = genresDeferred.await()

                Log.d(TAG, "Songs: ${songs.size}, Albums: ${albums.size}, Artists: ${artists.size}, Genres: ${genres.size}")

                val artFlowData = mutableListOf<ArtFlowData<Any>>()
                if (songs.isNotEmpty()) {
                    artFlowData.add(ArtFlowData(R.string.songs, songs))
                }
                if (albums.isNotEmpty()) {
                    artFlowData.add(ArtFlowData(R.string.albums, albums))
                }
                if (artists.isNotEmpty()) {
                    artFlowData.add(ArtFlowData(R.string.artists, artists))
                }
                if (genres.isNotEmpty()) {
                    artFlowData.add(ArtFlowData(R.string.genres, genres))
                }

                Log.d(TAG, "Data loaded successfully: ${artFlowData.size} sections")
                data.postValue(artFlowData)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading home data", e)
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            LibraryPreferences.MINIMUM_AUDIO_SIZE,
            LibraryPreferences.MINIMUM_AUDIO_LENGTH -> {
                Log.d(TAG, "onSharedPreferenceChanged: Relevant preference changed, reloading data")
                loadData()
            }
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
        private const val TAKE_COUNT = 18
    }
}