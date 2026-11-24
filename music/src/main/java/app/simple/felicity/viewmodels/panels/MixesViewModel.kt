package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.engines.OfflineRecommendationEngine
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.SongStatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Holds the auto-generated mixes (Discover, Random Picks and Queue Mix). It is requested with an
 * activity scope so the dashboard cards and the mix panels always show the same songs.
 *
 * Mixes are generated once the library has songs and stay stable until [refresh] is called,
 * so a mix never reshuffles while the user is browsing it.
 *
 * @author Hamza417
 */
@HiltViewModel
class MixesViewModel @Inject constructor(
        application: Application,
        songStatRepository: SongStatRepository
) : WrappedViewModel(application) {

    /** The kinds of auto-generated mixes. */
    /** The kinds of auto-generated mixes. */
    enum class MixType { RECOMMENDED, DISCOVER, RANDOM_PICKS, QUEUE }

    /**
     * An auto-generated playlist.
     *
     * @param type Which kind of mix this is.
     * @param titleRes String resource used as the mix title.
     * @param songs The non-empty list of songs in the mix, already shuffled.
     */
    data class Mix(val type: MixType, val titleRes: Int, val songs: List<Audio>)

    private val refreshTrigger = MutableStateFlow(0)
    private var appliedTrigger = -1
    private var cachedMixes = emptyList<Mix>()

    private val recommendationEngine = OfflineRecommendationEngine()

    /** The current mixes. Mixes with no songs are omitted. */
    val mixes: StateFlow<List<Mix>> = combine(
            songStatRepository.getAllAudioWithStat(),
            refreshTrigger
    ) { library, trigger ->
        // If we already have mixes and the trigger hasn't changed, 
        // we ignore the library update so it doesn't reshuffle.
        if (trigger == appliedTrigger && cachedMixes.isNotEmpty()) {
            return@combine cachedMixes
        }

        val newMixes = listOf(
                Mix(MixType.RECOMMENDED, R.string.recommended, recommendationEngine.recommendOverallMix(library).map { it.audio }),
                Mix(MixType.DISCOVER, R.string.discover, recommendationEngine.recommendDiscover(library).map { it.audio }),
                Mix(MixType.RANDOM_PICKS, R.string.random_picks, recommendationEngine.recommendRandomPicks(library).map { it.audio })
        ).filter { it.songs.isNotEmpty() }

        appliedTrigger = trigger
        cachedMixes = newMixes
        newMixes
    }
        .catch { e -> Log.e(TAG, "Error loading mixes", e); emit(emptyList()) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Generates a fresh shuffle of every mix. */
    fun refresh() {
        refreshTrigger.value++
    }

    companion object {
        private const val TAG = "MixesViewModel"
    }
}
