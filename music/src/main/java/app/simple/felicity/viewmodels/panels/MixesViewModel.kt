package app.simple.felicity.viewmodels.panels

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.AudioRepository
import app.simple.felicity.repository.repositories.SongStatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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
        private val audioRepository: AudioRepository,
        private val songStatRepository: SongStatRepository
) : WrappedViewModel(application) {

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

    private val _mixes = MutableStateFlow<List<Mix>>(emptyList())

    /** The current mixes. Mixes with no songs are omitted. */
    val mixes: StateFlow<List<Mix>> = _mixes.asStateFlow()

    private val refreshTrigger = MutableStateFlow(0)
    private var appliedTrigger = -1

    init {
        startMixesFlow()
    }

    /**
     * Combines the library and stats flows so the mixes are built as soon as data is available,
     * but only recomputes them again when [refresh] bumps the trigger.
     */
    private fun startMixesFlow() {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                    songStatRepository.getNeverPlayed(),
                    songStatRepository.getRecentlyPlayed(),
                    songStatRepository.getMostPlayed(),
                    audioRepository.getAllAudio(),
                    refreshTrigger
            ) { neverPlayed, recentlyPlayed, mostPlayed, allAudio, trigger ->
                if (_mixes.value.isNotEmpty() && trigger == appliedTrigger) return@combine null

                // Discover prefers never played songs; once everything has been played it
                // falls back to songs that are not among the recently played ones.
                val discoverPool = neverPlayed.ifEmpty {
                    val recentIds = recentlyPlayed.map { it.id }.toHashSet()
                    allAudio.filterNot { it.id in recentIds }
                }

                trigger to listOf(
                        Mix(MixType.RECOMMENDED, R.string.recommended, allAudio.shuffled().take(MIX_SIZE)),
                        Mix(MixType.DISCOVER, R.string.discover, discoverPool.shuffled().take(MIX_SIZE)),
                        Mix(MixType.RANDOM_PICKS, R.string.random_picks, allAudio.shuffled().take(MIX_SIZE)),
                        Mix(MixType.QUEUE, R.string.queue_mix, buildQueueMix(mostPlayed, allAudio))
                ).filter { it.songs.isNotEmpty() }
            }
                .catch { e -> Log.e(TAG, "Error loading mixes", e) }
                .collect { result ->
                    if (result == null) return@collect
                    appliedTrigger = result.first
                    _mixes.value = result.second
                    Log.d(TAG, "mixes updated: ${result.second.size}")
                }
        }
    }

    /**
     * Builds a ready-to-play queue from the user's play activity. The most played songs are
     * ranked, the artists behind them are weighted by that rank, and the queue is filled with
     * songs from the top artists in the library. Returns an empty list when there is no play
     * history yet.
     */
    private fun buildQueueMix(mostPlayed: List<Audio>, allAudio: List<Audio>): List<Audio> {
        val artistWeights = HashMap<String, Int>()
        mostPlayed.forEachIndexed { index, audio ->
            val artist = audio.artist?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
            artistWeights[artist] = (artistWeights[artist] ?: 0) + (mostPlayed.size - index)
        }

        val topArtists = artistWeights.entries
            .sortedByDescending { it.value }
            .take(QUEUE_ARTISTS)
            .map { it.key }
            .toHashSet()

        if (topArtists.isEmpty()) return emptyList()

        return allAudio.filter { it.artist in topArtists }.shuffled().take(MIX_SIZE)
    }

    /** Generates a fresh shuffle of every mix. */
    fun refresh() {
        refreshTrigger.value++
    }

    companion object {
        private const val TAG = "MixesViewModel"

        /** Number of songs in each mix. */
        private const val MIX_SIZE = 25

        /** Number of top artists the queue mix draws from. */
        private const val QUEUE_ARTISTS = 5
    }
}
