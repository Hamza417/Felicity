package app.simple.felicity.viewmodels.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.Audio
import com.linc.amplituda.Amplituda
import com.linc.amplituda.Compress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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

    fun loadWaveform(audio: Audio) {
        // Prevent redundant loading
        if (audio.path == currentPath && waveformData.value?.isNotEmpty() == true) return

        // Track the current request to handle "cancellation" manually
        currentPath = audio.path

        try {
            Amplituda(getApplication())
                .processAudio(
                        audio.path,
                        // We keep native compression to save memory crossing the JNI bridge,
                        // but we no longer rely on it for the final UI bar count.
                        Compress.withParams(Compress.PEAK, 1)
                )
                .get(
                        { result ->
                            // Stale Data Check
                            if (currentPath != audio.path) return@get

                            val rawAmplitudes = result.amplitudesAsList()

                            if (rawAmplitudes.isEmpty()) {
                                waveformData.postValue(FloatArray(0))
                                return@get
                            }

                            // --- THE FIX: Deterministic Time-Based Downsampling ---

                            // Define exactly how dense you want the waveform to look.
                            // 4 to 6 bars per second usually looks great for a horizontally scrolling seekbar.
                            val barsPerSecond = 5

                            // Calculate the exact number of bars this specific song should have
                            val expectedBars = ((audio.duration / 1000f) * barsPerSecond).toInt().coerceAtLeast(1)

                            val chunkedAmplitudes = FloatArray(expectedBars)
                            val chunkSize = rawAmplitudes.size.toFloat() / expectedBars

                            // Group the unpredictable Amplituda data into our fixed time chunks
                            for (i in 0 until expectedBars) {
                                val start = (i * chunkSize).toInt()
                                val end = ((i + 1) * chunkSize).toInt().coerceAtMost(rawAmplitudes.size)

                                var peakInChunk = 0
                                // Find the loudest peak in this specific time window
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

                            // Push the perfectly scaled array to the UI
                            waveformData.postValue(sampled)
                        },
                        { exception ->
                            Log.w(TAG, "Amplituda decode warning for ${audio.path}: ${exception.message}")
                            if (currentPath == audio.path) {
                                waveformData.postValue(FloatArray(0))
                            }
                        }
                )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Amplituda for ${audio.title}", e)
            if (currentPath == audio.path) {
                waveformData.postValue(FloatArray(0))
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

