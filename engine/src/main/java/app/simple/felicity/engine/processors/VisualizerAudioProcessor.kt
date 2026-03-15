package app.simple.felicity.engine.processors

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import app.simple.felicity.engine.processors.VisualizerAudioProcessor.Companion.BAND_COUNT
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * An [AudioProcessor] that performs a real-time FFT on the audio stream and delivers
 * 40 logarithmically-spaced frequency band magnitudes (bass → treble) to a [VisualizerListener].
 *
 * The processor is transparent — it passes the audio through unchanged while extracting
 * spectrum data as a side effect. A 1024-sample Hanning-windowed FFT is computed once per
 * capture window. The resulting magnitudes are mapped to [BAND_COUNT] log-spaced bands and
 * normalized to [0..1] before dispatch.
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class VisualizerAudioProcessor : BaseAudioProcessor() {

    /**
     * Callback interface delivered on the audio thread.
     * Implementations must be thread-safe; switch to the main thread before touching UI.
     */
    interface VisualizerListener {
        /**
         * Called once per FFT window (~23 ms at 44100 Hz) with fresh spectrum data.
         *
         * @param bands [BAND_COUNT] magnitude values in [0..1], index 0 = bass, last = treble.
         */
        fun onSpectrumDataCaptured(bands: FloatArray)
    }

    private var listener: VisualizerListener? = null

    /** Number of output frequency bands delivered to the listener. */
    val bandCount: Int = BAND_COUNT

    private val fftSize = FFT_SIZE

    /** PCM sample accumulation buffer (mono-mixed). */
    private val sampleBuffer = FloatArray(fftSize)
    private var bufferIndex = 0

    /** Hanning window pre-computed once to reduce spectral leakage. */
    private val window = FloatArray(fftSize) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
    }

    /** In-place FFT work arrays (real part and imaginary part). */
    private val fftReal = FloatArray(fftSize)
    private val fftImag = FloatArray(fftSize)

    /**
     * Bin index boundaries for each of the [BAND_COUNT] frequency bands.
     * Index position n holds the first FFT bin of band n; position [BAND_COUNT] is the exclusive end.
     */
    private val bandEdges = IntArray(BAND_COUNT + 1)

    init {
        computeBandEdges(DEFAULT_SAMPLE_RATE)
    }

    /**
     * Recomputes [bandEdges] for [BAND_COUNT] logarithmically-spaced bands
     * spanning 20 Hz to 20 kHz at the given [sampleRate].
     *
     * After the initial log computation a second pass enforces strict monotonicity so
     * every band always owns at least one unique FFT bin. Without this pass the lowest
     * several bands collapse to bin 1 and visually move in lockstep.
     */
    private fun computeBandEdges(sampleRate: Int) {
        val nyquist = sampleRate / 2.0
        val minFreq = 20.0
        val maxFreq = minOf(20_000.0, nyquist)
        val halfSize = fftSize / 2

        val minBin = (minFreq / nyquist * halfSize).coerceAtLeast(1.0)
        val maxBin = (maxFreq / nyquist * halfSize).coerceAtMost((halfSize - 1).toDouble())
        val ratio = (maxBin / minBin).pow(1.0 / BAND_COUNT)

        for (i in 0..BAND_COUNT) {
            bandEdges[i] = (minBin * ratio.pow(i.toDouble())).toInt().coerceIn(1, halfSize - 1)
        }

        // Enforce strict monotonicity: each edge must be greater than the previous one.
        // This guarantees every band maps to at least one unique FFT bin.
        for (i in 1..BAND_COUNT) {
            if (bandEdges[i] <= bandEdges[i - 1]) {
                bandEdges[i] = bandEdges[i - 1] + 1
            }
        }
        // Clamp any edges pushed past the nyquist boundary back into range.
        for (i in 1..BAND_COUNT) {
            bandEdges[i] = bandEdges[i].coerceAtMost(halfSize - 1)
        }
    }

    /**
     * Attaches or removes the spectrum listener.
     *
     * @param listener The listener to notify, or null to detach.
     */
    fun setListener(listener: VisualizerListener?) {
        this.listener = listener
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
                inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        ) {
            computeBandEdges(inputAudioFormat.sampleRate)
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Acquire an output buffer of the same size — audio passes through untouched.
        val outputBuffer = replaceOutputBuffer(remaining)

        // Mark the position so we can rewind after peeking at samples.
        inputBuffer.mark()

        val encoding = inputAudioFormat.encoding
        val channelCount = inputAudioFormat.channelCount
        // Bytes consumed by one PCM sample value (one channel, one frame position).
        val sampleSize = if (encoding == C.ENCODING_PCM_16BIT) 2 else 4
        // Bytes consumed by a full interleaved audio frame (all channels combined).
        // Reading a complete frame at once and averaging to mono ensures the FFT sees
        // the correct Nyquist frequency. Without this, interleaved stereo data is
        // treated as a mono stream at double the apparent sample rate, which aliases
        // high-frequency content downward and causes treble bands to stay flat.
        val frameSize = sampleSize * channelCount

        while (inputBuffer.remaining() >= frameSize) {
            // Down-mix all channels to mono by averaging them.
            var sum = 0f
            repeat(channelCount) {
                sum += if (encoding == C.ENCODING_PCM_16BIT) {
                    inputBuffer.short.toFloat() / 32768f
                } else {
                    inputBuffer.float
                }
            }
            sampleBuffer[bufferIndex] = sum / channelCount
            bufferIndex++

            if (bufferIndex >= fftSize) {
                if (listener != null) {
                    processAndEmit()
                }
                bufferIndex = 0
            }
        }

        // Rewind and pass the original, unmodified data downstream.
        inputBuffer.reset()
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    /**
     * Applies a Hanning window to [sampleBuffer], computes the FFT, then maps the magnitude
     * spectrum to [BAND_COUNT] logarithmic bands and dispatches them to the [listener].
     */
    private fun processAndEmit() {
        // Apply Hanning window to reduce spectral leakage.
        for (i in 0 until fftSize) {
            fftReal[i] = sampleBuffer[i] * window[i]
            fftImag[i] = 0f
        }

        fft(fftReal, fftImag)

        val bands = FloatArray(BAND_COUNT)

        for (band in 0 until BAND_COUNT) {
            val startBin = bandEdges[band]
            val endBin = (bandEdges[band + 1]).coerceAtLeast(startBin + 1)
            var sumSq = 0f
            var count = 0

            for (bin in startBin until endBin) {
                val re = fftReal[bin]
                val im = fftImag[bin]
                sumSq += re * re + im * im
                count++
            }

            // Raw RMS magnitude per band — no scale or clamp applied here.
            // The view layer normalizes these values dynamically against a smoothed peak,
            // so the processor should deliver honest linear amplitudes.
            val rms = if (count > 0) sqrt(sumSq / count) else 0f
            bands[band] = rms
        }

        listener?.onSpectrumDataCaptured(bands)
    }

    /**
     * In-place Cooley-Tukey radix-2 DIT FFT.
     *
     * Operates on arrays whose length is a power of two. The result overwrites [re] and [im].
     *
     * @param re Real input/output array.
     * @param im Imaginary input/output array (set to 0 for a purely real input signal).
     */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var tmp = re[i]; re[i] = re[j]; re[j] = tmp
                tmp = im[i]; im[i] = im[j]; im[j] = tmp
            }
        }

        // Butterfly passes.
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val ang = (-2.0 * PI / len).toFloat()
            val wBaseRe = cos(ang.toDouble()).toFloat()
            val wBaseIm = sin(ang.toDouble()).toFloat()

            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f

                for (k in 0 until halfLen) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + halfLen] * curRe - im[i + k + halfLen] * curIm
                    val vIm = re[i + k + halfLen] * curIm + im[i + k + halfLen] * curRe

                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + halfLen] = uRe - vRe
                    im[i + k + halfLen] = uIm - vIm

                    val newCurRe = curRe * wBaseRe - curIm * wBaseIm
                    curIm = curRe * wBaseIm + curIm * wBaseRe
                    curRe = newCurRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    companion object {
        /** Size of the FFT window in samples. Must be a power of two. */
        private const val FFT_SIZE = 4096
        private const val BAND_COUNT = 40
        private const val DEFAULT_SAMPLE_RATE = 44_100
    }
}