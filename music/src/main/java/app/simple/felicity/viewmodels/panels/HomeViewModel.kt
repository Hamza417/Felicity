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
import app.simple.felicity.repository.repositories.SongStatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the ArtFlow home screen.
 *
 * <p>Loads curated song collections (Favorites, Recently Played, Most Played, and Recently Added)
 * to populate the image-slider rows on the home screen. Each section is represented as an
 * [ArtFlowData] wrapping a list of [app.simple.felicity.repository.models.Audio] objects.
 *
 * @author Hamza417
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
        application: Application,
        private val audioRepository: AudioRepository,
        private val songStatRepository: SongStatRepository
) : WrappedViewModel(application) {

    private val data: MutableLiveData<List<ArtFlowData<Any>>> by lazy {
        MutableLiveData<List<ArtFlowData<Any>>>().also {
            Log.d(TAG, "LiveData initialized")
            loadData()
        }
    }

    /**
     * Returns the [LiveData] stream of curated home sections.
     *
     * @return Observable list of [ArtFlowData] sections for the home slider.
     */
    fun getData(): LiveData<List<ArtFlowData<Any>>> = data

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Loading home data…")

            try {
                val favoritesDeferred = async {
                    audioRepository.getFavoriteAudio()
                        .catch { e -> Log.e(TAG, "Error loading favorites", e); emit(emptyList()) }
                        .flowOn(Dispatchers.IO)
                        .first()
                        .take(TAKE_COUNT)
                }

                val recentlyPlayedDeferred = async {
                    songStatRepository.getRecentlyPlayed()
                        .catch { e -> Log.e(TAG, "Error loading recently played", e); emit(emptyList()) }
                        .flowOn(Dispatchers.IO)
                        .first()
                        .take(TAKE_COUNT)
                }

                val mostPlayedDeferred = async {
                    songStatRepository.getMostPlayed()
                        .catch { e -> Log.e(TAG, "Error loading most played", e); emit(emptyList()) }
                        .flowOn(Dispatchers.IO)
                        .first()
                        .take(TAKE_COUNT)
                }

                val recentlyAddedDeferred = async {
                    audioRepository.getRecentAudio()
                        .catch { e -> Log.e(TAG, "Error loading recently added", e); emit(mutableListOf()) }
                        .flowOn(Dispatchers.IO)
                        .first()
                        .take(TAKE_COUNT)
                }

                val favorites = favoritesDeferred.await()
                val recentlyPlayed = recentlyPlayedDeferred.await()
                val mostPlayed = mostPlayedDeferred.await()
                val recentlyAdded = recentlyAddedDeferred.await()

                Log.d(TAG, "Favorites: ${favorites.size}, RecentlyPlayed: ${recentlyPlayed.size}, " +
                        "MostPlayed: ${mostPlayed.size}, RecentlyAdded: ${recentlyAdded.size}")

                val artFlowData = mutableListOf<ArtFlowData<Any>>()
                if (favorites.isNotEmpty()) {
                    artFlowData.add(ArtFlowData(R.string.favorites, favorites))
                }
                if (recentlyPlayed.isNotEmpty()) {
                    artFlowData.add(ArtFlowData(R.string.recently_played, recentlyPlayed))
                }
                if (mostPlayed.isNotEmpty()) {
                    artFlowData.add(ArtFlowData(R.string.most_played, mostPlayed))
                }
                if (recentlyAdded.isNotEmpty()) {
                    artFlowData.add(ArtFlowData(R.string.recently_added, recentlyAdded))
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