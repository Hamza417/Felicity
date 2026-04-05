package app.simple.felicity.viewmodels.player

import android.app.Application
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.viewmodels.player.WaveformViewModel.Companion.PCM_BUFFERS_PER_BAR
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random.Default.nextFloat

/**
 * ViewModel responsible for loading and exposing per-second amplitude data
 * for the currently playing audio track.
 *
 * Decodes the audio using [android.media.MediaCodec] with a seek-per-bar strategy:
 * for each visual bar the extractor is repositioned to the bar's start timestamp and
 * the codec pipeline is flushed, so only a small number of PCM output buffers are
 * decoded per bar instead of the entire file. Values are normalized to [0.0, 1.0].
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

    /**
     * Loads waveform data for the given [audio] track.
     * If the same path is already loaded and non-empty, the call is a no-op.
     * A flat ghost array is posted immediately so the UI has something to display
     * while the background extraction runs.
     *
     * @param audio The [Audio] whose waveform should be extracted.
     */
    fun loadWaveform(audio: Audio) {
        // Prevent redundant loading
        if (audio.path == currentPath && waveformData.value?.isNotEmpty() == true) return

        postFlatData(audio)

        // Track the current request to handle stale extractions
        currentPath = audio.path

        // Immediately leave the Main Thread so the UI never drops a frame
        viewModelScope.launch(Dispatchers.IO) {

            // Push the perfectly scaled array safely to the UI thread
            waveformData.postValue(extractWaveformOptimized(audio.path, audio.duration) ?: getRandomGhostData(audio.duration))
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

    /**
     * Extracts waveform amplitude data from the audio file at [audioPath] using a seek-per-bar
     * strategy: for each visual bar the extractor is repositioned to the bar's start time and
     * the codec is flushed, so only [PCM_BUFFERS_PER_BAR] output buffers (≈ 20–50 ms each) are
     * decoded per bar instead of the entire file duration. This yields roughly a 10–20× speedup
     * over full linear decoding for typical song lengths.
     *
     * @param audioPath  Absolute path to the audio file.
     * @param durationMs Total track duration in milliseconds.
     * @return Normalized amplitude array ([0.0, 1.0] per bar), or null on error.
     */
    suspend fun extractWaveformOptimized(audioPath: String, durationMs: Long): FloatArray? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(audioPath)

            // Locate the first audio track in the container.
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex < 0 || format == null) return@withContext null
            extractor.selectTrack(audioTrackIndex)

            val expectedBars = ((durationMs / 1000f) * BARS_PER_SECOND).toInt().coerceAtLeast(1)
            val finalAmplitudes = FloatArray(expectedBars)

            // Duration in microseconds, and how long each bar window spans.
            val durationUs = durationMs * 1_000L
            val barDurationUs = durationUs / expectedBars.toLong()

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()

            // Reusable PCM buffer; grown lazily if the codec hands us a larger output.
            var chunkArray = ShortArray(8192)

            // True once EOS is signaled by the codec; remaining bars are pre-filled and we stop.
            var eos = false

            for (barIndex in 0 until expectedBars) {
                if (!isActive || eos) break

                // Seek the extractor to the start of this bar's time window, then flush the
                // codec so no stale PCM from the previous bar bleeds through.
                val seekTimeUs = barIndex.toLong() * barDurationUs
                extractor.seekTo(seekTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                codec.flush()

                var peak = 0f
                var outputsReceived = 0
                var inputEOS = false
                var iterations = 0

                // Inner feed-and-drain loop: stop once we have enough sampled PCM output.
                while (isActive && outputsReceived < PCM_BUFFERS_PER_BAR) {
                    if (++iterations > MAX_ITERATIONS_PER_BAR) break

                    // Submit one compressed frame to the decoder.
                    if (!inputEOS) {
                        val inputIndex = codec.dequeueInputBuffer(5_000L)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)!!
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEOS = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    // Drain one PCM output buffer.
                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 5_000L)
                    if (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                        val shortBuffer = outputBuffer.asShortBuffer()
                        val limit = shortBuffer.limit()

                        if (chunkArray.size < limit) chunkArray = ShortArray(limit)
                        shortBuffer.get(chunkArray, 0, limit)

                        // Skip the very first output buffer per bar: after a flush + seek, the
                        // decoder needs one warm-up frame before producing stable PCM data.
                        if (outputsReceived > 0) {
                            for (i in 0 until limit step SAMPLE_STRIDE) {
                                val sample = chunkArray[i]
                                val abs = if (sample < 0) -sample.toInt() else sample.toInt()
                                val normalized = abs.toFloat() / Short.MAX_VALUE
                                if (normalized > peak) peak = normalized
                            }
                        }

                        codec.releaseOutputBuffer(outputIndex, false)
                        outputsReceived++

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            // Pre-fill any remaining bars with this last peak and exit.
                            for (remaining in barIndex until expectedBars) {
                                finalAmplitudes[remaining] = peak
                            }
                            eos = true
                            break
                        }
                    }
                }

                if (!eos) finalAmplitudes[barIndex] = peak
            }

            // Normalize the full array to [0.0, 1.0].
            val maxPeak = finalAmplitudes.maxOrNull() ?: 1f
            if (maxPeak > 0f) {
                for (i in finalAmplitudes.indices) {
                    finalAmplitudes[i] /= maxPeak
                }
            }

            return@withContext finalAmplitudes

        } catch (e: Exception) {
            return@withContext null
        } finally {
            codec?.stop()
            codec?.release()
            extractor.release()
        }
    }

    companion object {
        private const val TAG = "WaveformViewModel"

        private const val BARS_PER_SECOND = 1

        /**
         * Number of decoded PCM output buffers sampled per bar window.
         * The first buffer is a warm-up frame (skipped); the remaining ones are peak-scanned.
         * One output buffer ≈ 20–50 ms of audio, so four buffers cover ≈ 60–150 ms per bar.
         */
        private const val PCM_BUFFERS_PER_BAR = 4

        /**
         * Step size when iterating over PCM samples inside a decoded output buffer.
         * A value of 8 means every 8th sample is inspected, which is sufficient for peak
         * detection since musical transients span many consecutive samples.
         */
        private const val SAMPLE_STRIDE = 8

        /**
         * Hard cap on the feed-drain iterations per bar to prevent an infinite loop if
         * the codec stalls (e.g., buffers unavailable under load).
         */
        private const val MAX_ITERATIONS_PER_BAR = 60
    }
}

