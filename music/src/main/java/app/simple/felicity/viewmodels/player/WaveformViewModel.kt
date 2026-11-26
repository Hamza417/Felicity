package app.simple.felicity.viewmodels.player

import android.app.Application
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.WaveformData
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
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random.Default.nextFloat

/**
 * ViewModel responsible for loading and exposing per-second amplitude data
 * for the currently playing audio track.
 *
 * Uses a DB-first strategy: when a track changes, the ViewModel checks the
 * {@code waveform_data} table first. If a row is already there, the stored samples
 * are used immediately without touching the audio file. Only when no cached
 * row exists does the ViewModel call [Amplituda] to decode the file, after
 * which the result is saved to the database so the next play is instant.
 *
 * Values are normalized to [0.0, 1.0], with one entry per second of audio.
 *
 * @author Hamza417
 */
@HiltViewModel
class WaveformViewModel @Inject constructor(
        application: Application
) : WrappedViewModel(application) {

    private val waveformData: MutableLiveData<FloatArray> = MutableLiveData()
    private var currentUri: String? = null
    private var loadJob: Job? = null

    /**
     * Returns a [LiveData] stream of normalized amplitude arrays.
     * Each emission contains one [Float] value per second of audio, in [0.0, 1.0].
     */
    fun getWaveformData(): LiveData<FloatArray> = waveformData

    fun loadWaveform(audio: Audio) {
        // Prevent redundant loading
        if (audio.uri == currentUri && waveformData.value?.isNotEmpty() == true) return

        // Track the current request; the `currentUri` check below only guards
        // requests within this ViewModel instance (a new player screen = a new instance).
        currentUri = audio.uri

        // Cancel a decode started earlier in this same ViewModel. Rapid navigation
        // across screens cannot be cancelled this way; the process-wide [decodeMutex]
        // below is what actually stops blocking decodes from piling up.
        loadJob?.cancel()

        postFlatData(audio) // Show the ghost waveform immediately while we load the real one

        ensureContext(getApplication())

        loadJob = viewModelScope.launch(Dispatchers.IO) {
            decodeMutex.withLock {
                // Only guards re-loads within this same ViewModel. Across screens the
                // mutex is what prevents concurrent native decodes.
                if (currentUri != audio.uri) return@withLock

                runCatching {
                val dao = AudioDatabase.getInstance(getApplication()).waveformDao()

                // DB-first: try to get the cached samples before touching the audio file
                val cached = dao.getWaveformByHash(audio.hash)
                if (cached != null) {
                    val samples = cached.toFloatArray()
                    if (samples.isNotEmpty() && currentUri == audio.uri) {
                        waveformData.postValue(samples)
                        return@runCatching
                    }
                }

                // Nothing in the DB yet — decode the file and save the result.
                // Still stale after acquiring the lock? Don't spend native work on an
                // off-screen song (the common case during rapid navigation).
                if (currentUri != audio.uri) return@runCatching

                val parcelFileDescriptor = getApplication<Application>()
                    .contentResolver.openFileDescriptor(audio.uri.toUri(), "r")
                    ?: throw IllegalArgumentException("Unable to open URI: ${audio.uri}")

                try {
                    val rawAmplitudes = getAmplituda()
                        .processAudio(
                                parcelFileDescriptor,
                                Compress.withParams(Compress.AVERAGE, BARS_PER_SECOND),
                                Cache.withParams(Cache.REUSE, audio.hash.toString())
                        )
                        .get() // Blocking call — serialized by decodeMutex
                        .amplitudesAsList()

                    parcelFileDescriptor.close()

                    if (currentUri != audio.uri) return@runCatching

                    if (rawAmplitudes.isEmpty()) {
                        waveformData.postValue(getRandomGhostData(audio.duration)) // Fallback to ghost data if extraction yields nothing
                        return@runCatching
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

                    // Persist to DB so the next play of this track skips decoding entirely
                    dao.insertWaveform(WaveformData.fromFloatArray(audio.hash, sampled))

                    // Push the perfectly scaled array safely to the UI thread
                    waveformData.postValue(sampled)

                } catch (e: Exception) {
                    Log.e(TAG, "Amplituda extraction failed on IO thread for ${audio.title}", e)
                    if (currentUri == audio.uri) {
                        postFlatData(audio) // Show the ghost waveform if extraction fails
                    }
                } finally {
                    try {
                        parcelFileDescriptor.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to close ParcelFileDescriptor for ${audio.uri}", e)
                    }
                }
                }.getOrElse {
                    Log.e(TAG, "Unexpected error in loadWaveform for ${audio.title}", it)
                    if (currentUri == audio.uri) {
                        postFlatData(audio) // Show the ghost waveform on any unexpected error
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
        currentUri = null
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
        loadJob?.cancel()
        super.onCleared()
        // The Amplituda instance is shared process-wide (see [sharedAmplituda]); an
        // individual player screen going away must not release it for everyone else.
    }

    companion object {
        private const val TAG = "WaveformViewModel"

        private const val BARS_PER_SECOND = 1

        /**
         * Serializes waveform decoding across every ViewModel in the process.
         *
         * Amplituda's `processAudio(...).get()` is a blocking native call that can take
         * seconds. Each player screen creates a fresh ViewModel; without this lock,
         * quickly entering/leaving the player launched 100+ concurrent decodes, saturating
         * the IO dispatcher and triggering ANRs (`AmplitudaProcessingTask.get`) plus
         * `GcWatcher.finalize()` timeouts from the resulting allocation storm. With the
         * lock at most one decode runs at a time; an already-cached track is picked up
         * from the DB when a queued request finally gets the lock.
         */
        private val decodeMutex = Mutex()

        /** Single shared decoder — avoids one native Amplituda instance per player visit. */
        @Volatile
        private var sharedAmplituda: Amplituda? = null

        private fun getAmplituda(): Amplituda {
            return sharedAmplituda ?: synchronized(this) {
                sharedAmplituda ?: Amplituda(appContext).also { sharedAmplituda = it }
            }
        }

        /** Application context captured once for the lazily-created shared decoder. */
        private lateinit var appContext: Application

        /**
         * Seed the process-wide decoder context. Called from [loadWaveform] with the
         * ViewModel's application before the shared instance is first created.
         */
        private fun ensureContext(app: Application) {
            if (!::appContext.isInitialized) appContext = app
        }
    }
}
