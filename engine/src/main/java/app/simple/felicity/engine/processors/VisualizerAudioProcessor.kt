package app.simple.felicity.engine.processors

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
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
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class VisualizerAudioProcessor : BaseAudioProcessor() {

    interface VisualizerListener {
        fun onSpectrumDataCaptured(bands: FloatArray)
    }

    private var listener: VisualizerListener? = null

    val bandCount: Int = BAND_COUNT
    private val fftSize = FFT_SIZE

    private val sampleBuffer = FloatArray(fftSize)
    private var bufferIndex = 0

    // Hanning window pre-computed once to reduce spectral leakage.
    private val window = FloatArray(fftSize) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
    }

    private val fftReal = FloatArray(fftSize)
    private val fftImag = FloatArray(fftSize)
    private val bandEdges = IntArray(BAND_COUNT + 1)

    // switch between Scientific RMS and Visualizer Peak Math
    // true -> Visualizer mode: treble boost and dynamic range compression for a more
    //         visually impactful spectrum.
    // false -> Scientific mode: pure RMS magnitude for accurate frequency analysis,
    //          ideal for audio-reactive visuals or precise metering.
    @Volatile
    var isVisualizerOptimized: Boolean = true

    /**
     * switch between Scientific RMS and Visualizer Peak Math
     * true -> Visualizer mode: treble boost and dynamic range compression for a more
     *         visually impactful spectrum.
     * false -> Scientific mode: pure RMS magnitude for accurate frequency analysis,
     *          ideal for audio-reactive visuals or precise metering.
     */
    fun setOptimizedMode(optimized: Boolean) {
        this.isVisualizerOptimized = optimized
    }

    init {
        computeBandEdges(DEFAULT_SAMPLE_RATE)
    }

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

        // Enforce strict monotonicity.
        for (i in 1..BAND_COUNT) {
            if (bandEdges[i] <= bandEdges[i - 1]) {
                bandEdges[i] = bandEdges[i - 1] + 1
            }
        }
        for (i in 1..BAND_COUNT) {
            bandEdges[i] = bandEdges[i].coerceAtMost(halfSize - 1)
        }
    }

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

        val outputBuffer = replaceOutputBuffer(remaining)
        inputBuffer.mark()

        val encoding = inputAudioFormat.encoding

        // FIXED: Stereo frames mean 4 bytes for 16-bit, 8 bytes for Float
        val frameSize = if (encoding == C.ENCODING_PCM_16BIT) 4 else 8

        while (inputBuffer.remaining() >= frameSize) {
            val leftSample: Float
            val rightSample: Float

            if (encoding == C.ENCODING_PCM_16BIT) {
                leftSample = inputBuffer.short.toFloat() / 32768f
                rightSample = inputBuffer.short.toFloat() / 32768f
            } else {
                leftSample = inputBuffer.float
                rightSample = inputBuffer.float
            }

            // Mix stereo to true mono for accurate frequency analysis
            val monoSample = (leftSample + rightSample) / 2f

            sampleBuffer[bufferIndex] = monoSample
            bufferIndex++

            if (bufferIndex >= fftSize) {
                if (listener != null) {
                    processAndEmit()
                }
                bufferIndex = 0
            }
        }

        inputBuffer.reset()
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    private fun processAndEmit() {
        for (i in 0 until fftSize) {
            fftReal[i] = sampleBuffer[i] * window[i]
            fftImag[i] = 0f
        }

        fft(fftReal, fftImag)

        val bands = FloatArray(BAND_COUNT)

        for (band in 0 until BAND_COUNT) {
            val startBin = bandEdges[band]
            val endBin = (bandEdges[band + 1]).coerceAtLeast(startBin + 1)

            if (isVisualizerOptimized) {
                // --- VISUALIZER OPTIMIZED MODE ---
                var maxMagnitude = 0f
                for (bin in startBin until endBin) {
                    val re = fftReal[bin]
                    val im = fftImag[bin]
                    val mag = sqrt(re * re + im * im)
                    if (mag > maxMagnitude) {
                        maxMagnitude = mag
                    }
                }

                // Treble boost and dynamic range compression
                val frequencyWeight = 1f + (band.toFloat() / BAND_COUNT) * 3.0f
                bands[band] = sqrt(maxMagnitude * frequencyWeight)

            } else {
                // --- SCIENTIFIC RMS MODE ---
                var sumSq = 0f
                var count = 0
                for (bin in startBin until endBin) {
                    val re = fftReal[bin]
                    val im = fftImag[bin]
                    sumSq += re * re + im * im
                    count++
                }
                bands[band] = if (count > 0) sqrt(sumSq / count) else 0f
            }
        }

        listener?.onSpectrumDataCaptured(bands)
    }

    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
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
        private const val FFT_SIZE = 4096 // Drop to 1024 if it causes stuttering on old devices
        private const val BAND_COUNT = 40
        private const val DEFAULT_SAMPLE_RATE = 44_100
    }
}