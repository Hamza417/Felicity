package app.simple.felicity.viewmodels.player

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.engine.processors.WaveformExtractor
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.Audio
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
 * Delegates decoding to [WaveformExtractor], which wraps a native miniaudio
 * implementation.  miniaudio reads the audio file in a single sequential pass
 * (no per-bar seek, no codec flush), making waveform extraction orders of
 * magnitude faster than the legacy seek-per-bar MediaCodec strategy.
 *
 * A flat ghost array is posted immediately so the UI has something to display
 * while the background extraction runs.  If the native decoder cannot open the
 * file (e.g., unsupported format), a sine-envelope ghost array is posted instead.
 * Values are normalized to [0.0, 1.0].
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
     * Lazily created extractor that wraps the miniaudio JNI bridge.
     * Stateless — safe to reuse across multiple [loadWaveform] calls.
     */
    private val waveformExtractor = WaveformExtractor()

    /**
     * Returns a [LiveData] stream of normalized amplitude arrays.
     * Each emission contains one [Float] value per second of audio in [0.0, 1.0].
     */
    fun getWaveformData(): LiveData<FloatArray> = waveformData

    /**
     * Loads waveform data for the given [audio] track.
     *
     * If the same path is already loaded and non-empty the call is a no-op.
     * A flat ghost array is posted immediately so the UI has a placeholder while
     * the miniaudio extraction runs on the IO dispatcher.
     *
     * @param audio The [Audio] whose waveform should be extracted.
     */
    fun loadWaveform(audio: Audio) {
        if (audio.path == currentPath && waveformData.value?.isNotEmpty() == true) return

        loadJob?.cancel()
        postFlatData(audio)
        currentPath = audio.path

        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val result = waveformExtractor.extractWaveform(
                    path = audio.path,
                    durationMs = audio.duration,
                    barsPerSecond = BARS_PER_SECOND
            )

            if (result != null) {
                waveformData.postValue(result)
            } else {
                /**
                 * Fallback: native decoder could not open the file (e.g., AAC/M4A).
                 * Post a sine-envelope ghost so the UI never shows an empty waveform.
                 */
                throw IllegalStateException("Waveform extraction failed for path: ${audio.path}")
                waveformData.postValue(getRandomGhostData(audio.duration))
            }
        }
    }

    /**
     * Clears the current waveform data and cancels any pending extraction.
     * Call this when navigating to a song whose waveform has not yet been decoded.
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
        val expectedBars = ((durationMs / 1000f) * BARS_PER_SECOND).toInt().coerceAtLeast(1)

        return FloatArray(expectedBars) { i ->
            /** Normalized progress through the track (0.0 to 1.0). */
            val progress = i.toFloat() / expectedBars

            /**
             * Sine-wave envelope: sin(0) = 0 (intro) → sin(PI/2) = 1 (middle)
             * → sin(PI) = 0 (outro), producing a natural-looking energy curve.
             */
            val envelope = sin(progress * PI).toFloat()

            /** Random noise with a minimum floor so the middle stays non-silent. */
            val noise = (nextFloat() * 0.8f) + 0.2f

            /** Cap the absolute max height at 0.4 (40%) to keep the ghost subtle. */
            (envelope * noise) * 0.4f
        }
    }

    companion object {

        private const val BARS_PER_SECOND = 1
    }
}
