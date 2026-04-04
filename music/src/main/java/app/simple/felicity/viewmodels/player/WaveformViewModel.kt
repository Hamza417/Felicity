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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

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

    /**
     * Returns a [LiveData] stream of normalized amplitude arrays.
     * Each emission contains one [Float] value per second of audio, in [0.0, 1.0].
     */
    fun getWaveformData(): LiveData<FloatArray> = waveformData

    // The Bouncer: Ensures only one native extraction runs at a time
    private val amplitudaMutex = Mutex()

    fun loadWaveform(audio: Audio) {
        // Prevent redundant loading
        if (audio.path == currentPath && waveformData.value?.isNotEmpty() == true) return

        // Track the current request to handle stale extractions
        currentPath = audio.path

        // Immediately leave the Main Thread so the UI never drops a frame
        viewModelScope.launch(Dispatchers.IO) {

            // Wait in line. If another song is currently decoding, this suspends
            // without blocking any Kotlin threads, protecting the C++ JNI bridge.
            amplitudaMutex.withLock {

                // Stale Request Check 1: Did the user skip while we waited for the lock?
                if (currentPath != audio.path) return@withLock

                try {
                    // Synchronous, blocking native extraction (Safe because we are on IO + Locked)
                    val rawAmplitudes = Amplituda(getApplication())
                        .processAudio(
                                audio.path,
                                Compress.withParams(Compress.PEAK, 1),
                                Cache.withParams(Cache.REUSE, audio.hash.toString())
                        )
                        .get() // Blocking call
                        .amplitudesAsList()

                    // Stale Request Check 2: Did the user skip while C++ was grinding?
                    if (currentPath != audio.path) return@withLock

                    if (rawAmplitudes.isEmpty()) {
                        waveformData.postValue(FloatArray(0))
                        return@withLock
                    }

                    // --- Deterministic Time-Based Downsampling ---

                    val barsPerSecond = 5
                    val expectedBars = ((audio.duration / 1000f) * barsPerSecond).toInt().coerceAtLeast(1)

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
                        FloatArray(0)
                    }

                    // Push the perfectly scaled array safely to the UI thread
                    waveformData.postValue(sampled)

                } catch (e: Exception) {
                    Log.e(TAG, "Amplituda extraction failed on IO thread for ${audio.title}", e)
                    if (currentPath == audio.path) {
                        waveformData.postValue(FloatArray(0))
                    }
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

    private fun postGhostData() {
        // Post a default array to trigger the UI to show the ghost waveform
        // should be the
        waveformData.postValue(FloatArray(1) { 0f })
    }

    companion object {
        private const val TAG = "WaveformViewModel"
    }
}

