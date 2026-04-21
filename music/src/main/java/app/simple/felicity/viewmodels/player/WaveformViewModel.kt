package app.simple.felicity.viewmodels.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.Audio
import com.linc.amplituda.Amplituda
import com.linc.amplituda.Cache
import com.linc.amplituda.Compress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random.Default.nextFloat

/**
 * ViewModel responsible for loading and exposing per-second amplitude data
 * for the currently playing audio track.
 *
 * Uses [Amplituda] to decode the raw PCM waveform and then downsamples the
 * result so that each element of the output array represents the peak amplitude
 * within a single one-second window. Values are normalized to [0.0, 1.0].
 *
 * @author Hamza417
 */
@HiltViewModel
class WaveformViewModel @Inject constructor(
        application: Application
) : WrappedViewModel(application) {

    private val waveformData: MutableLiveData<FloatArray> = MutableLiveData()
    private var currentPath: String? = null
    private var loadJob: Job? = null
    private var amplitudaInstance: Amplituda? = null

    /**
     * Returns a [LiveData] stream of normalized amplitude arrays.
     * Each emission contains one [Float] value per second of audio, in [0.0, 1.0].
     */
    fun getWaveformData(): LiveData<FloatArray> = waveformData

    fun loadWaveform(audio: Audio) {
        if (amplitudaInstance == null) {
            amplitudaInstance = Amplituda(getApplication())
        }

        // Prevent redundant loading
        if (audio.uri == currentPath && waveformData.value?.isNotEmpty() == true) return

        // Track the current request to handle stale extractions
        currentPath = audio.uri

        viewModelScope.launch(Dispatchers.IO) {
            if (currentPath != audio.uri) return@launch

            postFlatData(audio) // Show the ghost waveform immediately while we load the real one in the background

            try {
                val rawAmplitudes = amplitudaInstance!!
                    .processAudio(
                            audio.uri,
                            Compress.withParams(Compress.AVERAGE, BARS_PER_SECOND),
                            Cache.withParams(Cache.REUSE, audio.hash.toString())
                    )
                    .get() // Blocking call
                    .amplitudesAsList()

                if (currentPath != audio.uri) return@launch

                if (rawAmplitudes.isEmpty()) {
                    waveformData.postValue(getRandomGhostData(audio.duration)) // Fallback to ghost data if extraction yields nothing
                    return@launch
                }

                // --- Deterministic Time-Based Downsampling ---

                val expectedBars = ((audio.duration / 1000f) * BARS_PER_SECOND).toInt().coerceAtLeast(1)

                val chunkedAmplitudes = FloatArray(expectedBars)
                val chunkSize = rawAmplitudes.size.toFloat() / expectedBars

                for (i in 0 until expectedBars) {
                    val start = (i * chunkSize).toInt()
                    val end = ((i + 1) * chunkSize).toInt().coerceAtMost(rawAmplitudes.size)

                    var peakInChunk = 0
                    for (j in start until end) {
                        if (rawAmplitudes[j] > peakInChunk) {
                            peakInChunk = rawAmplitudes[j]
                        }
                    }
                    chunkedAmplitudes[i] = peakInChunk.toFloat()
                }

                // --- Final Normalization ---

                val maxPeak = chunkedAmplitudes.maxOrNull() ?: 1f
                val sampled = if (maxPeak > 0f) {
                    FloatArray(expectedBars) { i ->
                        chunkedAmplitudes[i] / maxPeak
                    }
                } else {
                    getRandomGhostData(audio.duration) // If all peaks are zero, return random ghost data to avoid a flatline
                }

                // Push the perfectly scaled array safely to the UI thread
                waveformData.postValue(sampled)

            } catch (e: Exception) {
                Log.e(TAG, "Amplituda extraction failed on IO thread for ${audio.title}", e)
                if (currentPath == audio.uri) {
                    postFlatData(audio) // Show the ghost waveform if extraction fails
                }
            }
        }
    }

    /**
     * Clears the current waveform data and cancels any pending load.
     * Call this when the user navigates to a song whose waveform has not yet been decoded.
     */
    fun resetWaveform() {
        loadJob?.cancel()
        currentPath = null
        waveformData.postValue(FloatArray(0))
    }

    private fun postFlatData(audio: Audio) {
        waveformData.postValue(getGhostData(audio.duration))
    }

    private fun getGhostData(durationMs: Long): FloatArray {
        val seconds = (durationMs / 1000).toInt().coerceAtLeast(1)
        return FloatArray(seconds) { 0.05f }
    }

    private fun getRandomGhostData(durationMs: Long): FloatArray {
        // MUST MATCH the density of your actual extraction function
        val expectedBars = ((durationMs / 1000f) * BARS_PER_SECOND).toInt().coerceAtLeast(1)

        return FloatArray(expectedBars) { i ->
            // Calculate normalized progress through the song (0.0 to 1.0)
            val progress = i.toFloat() / expectedBars

            // Create the "Song Shape" envelope using a Sine wave
            // sin(0) = 0.0 (Intro) -> sin(PI/2) = 1.0 (Middle) -> sin(PI) = 0.0 (Outro)
            val envelope = sin(progress * PI).toFloat()

            // Generate the random spikes (Noise)
            // We use a floor of 0.2f so the middle doesn't randomly have dead-silent spikes
            val noise = (nextFloat() * 0.8f) + 0.2f

            // Multiply the noise by the envelope
            // To keep it looking "ghostly" and subtle, we cap the absolute max height at 0.4f (40%)
            (envelope * noise) * 0.4f
        }
    }

    override fun onCleared() {
        super.onCleared()
        amplitudaInstance?.release()
    }

    companion object {
        private const val TAG = "WaveformViewModel"

        private const val BARS_PER_SECOND = 1
    }
}

