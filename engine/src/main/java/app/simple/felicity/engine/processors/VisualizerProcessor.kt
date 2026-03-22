package app.simple.felicity.engine.processors

import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import app.simple.felicity.engine.processors.VisualizerProcessor.Companion.BAND_COUNT
import app.simple.felicity.engine.processors.VisualizerProcessor.Companion.FFT_SIZE
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow

/**
 * An [AudioProcessor] that uses a PFFFT-backed native FFT to compute 40 logarithmically-spaced
 * frequency band magnitudes and delivers them via a lock-free twin-buffer mechanism.
 *
 * On each full [FFT_SIZE]-sample window the processor calls [nativeProcessInto], which
 * applies a Hann window, executes the PFFFT real-forward transform, and writes per-band
 * magnitudes directly into the current back buffer. The [AtomicBoolean] inside
 * [DirectOutput] is then toggled to promote the back buffer to front, and
 * [View.postInvalidate] is called on the registered view — all without allocating a
 * single object on the audio hot path.
 *
 * A legacy [VisualizerListener] interface is retained for backward-compatibility; it is
 * only invoked when no [DirectOutput] is connected (i.e., [setDirectOutput] has not been
 * called). In that case one [FloatArray] is allocated per FFT window to preserve the
 * existing listener contract.
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class VisualizerProcessor : BaseAudioProcessor() {

    // Listener interface (legacy path)

    /** Callback interface used by the legacy flow-based path when no direct output is set. */
    interface VisualizerListener {
        /** Called from the audio thread with [BAND_COUNT] raw FFT-derived band magnitudes. */
        fun onSpectrumDataCaptured(bands: FloatArray)
    }

    private var listener: VisualizerListener? = null

    // Band & FFT configuration

    val bandCount: Int = BAND_COUNT
    private val fftSize = FFT_SIZE

    /** Circular buffer that accumulates mono PCM samples before each FFT window. */
    private val sampleBuffer = FloatArray(fftSize)
    private var bufferIndex = 0

    /** Logarithmically-spaced bin boundaries: `bandEdges[k]..bandEdges[k+1]` is the range for band k. */
    private val bandEdges = IntArray(BAND_COUNT + 1)

    /**
     * When true, PFFFT computes peak magnitude plus a treble boost for visual impact.
     * When false, pure per-band RMS is computed for accurate frequency analysis.
     */
    @Volatile
    var isVisualizerOptimized: Boolean = true

    /** Switches between visualizer-optimized (true) and scientific RMS (false) mode. */
    fun setOptimizedMode(optimized: Boolean) {
        isVisualizerOptimized = optimized
    }

    // Native context

    /** Opaque pointer to the native `FFTContext`; 0 if the context was not created or was destroyed. */
    private var nativeHandle: Long = 0L

    // Direct twin-buffer output

    /**
     * Immutable snapshot of the direct-output connection.
     *
     * Stored as a single [Volatile] reference so the audio thread always sees a
     * fully constructed object — never a partially initialized one.
     */
    private class DirectOutput(
            val bufA: FloatArray,
            val bufB: FloatArray,
            val isAFront: AtomicBoolean,
            val view: WeakReference<View>
    )

    @Volatile
    private var directOutput: DirectOutput? = null

    // Lifecycle

    init {
        computeBandEdges(DEFAULT_SAMPLE_RATE)
        nativeHandle = nativeCreate(FFT_SIZE)
        if (nativeHandle != 0L) {
            nativeSetBandEdges(nativeHandle, bandEdges, BAND_COUNT)
        }
    }

    // Public API

    /**
     * Registers a [VisualizerListener] for the legacy non-direct output path.
     * Ignored while a direct output is connected.
     *
     * @param listener Listener to receive band magnitudes, or null to unregister.
     */
    fun setListener(listener: VisualizerListener?) {
        this.listener = listener
    }

    /**
     * Establishes a lock-free direct connection between this processor and the visualizer view.
     *
     * After this call, [processAndEmit] writes FFT band magnitudes straight into the back
     * buffer (determined by [isAFront]), atomically flips [isAFront], and calls
     * [View.postInvalidate] on [view] — bypassing coroutines, SharedFlow, and any other
     * intermediate dispatch entirely.
     *
     * Must be called from the main thread. Safe to call again with a new view reference
     * (e.g., after a fragment recreation) — the previous connection is atomically replaced.
     *
     * @param bufA     Pre-allocated [FloatArray] of size [BAND_COUNT] for the A buffer.
     * @param bufB     Pre-allocated [FloatArray] of size [BAND_COUNT] for the B buffer.
     * @param isAFront [AtomicBoolean] tracking which buffer is currently the front.
     * @param view     Visualizer [View] to be invalidated after each write.
     */
    fun setDirectOutput(
            bufA: FloatArray,
            bufB: FloatArray,
            isAFront: AtomicBoolean,
            view: View
    ) {
        directOutput = DirectOutput(bufA, bufB, isAFront, WeakReference(view))
    }

    /**
     * Removes the direct output connection.
     *
     * Should be called in [android.view.View.onDetachedFromWindow] or the host
     * fragment's [androidx.fragment.app.Fragment.onDestroyView] to prevent the audio
     * thread from holding a stale view reference.
     */
    fun clearDirectOutput() {
        directOutput = null
    }

    // AudioProcessor overrides

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
                inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        ) {
            computeBandEdges(inputAudioFormat.sampleRate)
            if (nativeHandle == 0L) {
                nativeHandle = nativeCreate(FFT_SIZE)
            }
            if (nativeHandle != 0L) {
                nativeSetBandEdges(nativeHandle, bandEdges, BAND_COUNT)
            }
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

            // Downmix stereo to mono for accurate frequency analysis.
            sampleBuffer[bufferIndex] = (leftSample + rightSample) / 2f
            bufferIndex++

            if (bufferIndex >= fftSize) {
                processAndEmit()
                bufferIndex = 0
            }
        }

        inputBuffer.reset()
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    override fun onReset() {
        super.onReset()
        bufferIndex = 0
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
    }

    // Core FFT dispatch

    /**
     * Executes one FFT window and routes the result to either the direct twin-buffer
     * output or the legacy listener, depending on which is connected.
     *
     * Direct path (zero allocations on audio thread):
     *  1. Determine the back buffer from [DirectOutput.isAFront].
     *  2. Call [nativeProcessInto] — C++ writes magnitudes in-place via
     *     `GetFloatArrayElements` + `ReleaseFloatArrayElements` with mode 0.
     *  3. Atomically flip [DirectOutput.isAFront] to promote back to front.
     *  4. Call [View.postInvalidate] to schedule a UI redraw.
     *
     * Legacy path (one [FloatArray] allocation per window):
     *  - Compute magnitudes into a fresh array and deliver via [VisualizerListener].
     */
    private fun processAndEmit() {
        if (nativeHandle == 0L) return

        val out = directOutput
        if (out != null) {
            // Direct path: write into back buffer, swap, trigger redraw.
            val backBuf = if (out.isAFront.get()) out.bufB else out.bufA
            nativeProcessInto(nativeHandle, sampleBuffer, backBuf, isVisualizerOptimized)
            out.isAFront.set(!out.isAFront.get())
            out.view.get()?.postInvalidate()
        } else {
            val l = listener ?: return
            // Legacy path: allocate one array per window (listener may hold the ref async).
            val bands = FloatArray(BAND_COUNT)
            nativeProcessInto(nativeHandle, sampleBuffer, bands, isVisualizerOptimized)
            l.onSpectrumDataCaptured(bands)
        }
    }

    // Band-edge computation

    /**
     * Computes logarithmically-spaced bin boundaries for [BAND_COUNT] bands spanning
     * 20 Hz to min(20 kHz, Nyquist) at the given [sampleRate].
     *
     * Results are stored in [bandEdges] (length [BAND_COUNT] + 1) with strict
     * monotonicity enforced so every band covers at least one FFT bin.
     *
     * @param sampleRate Sample rate in Hz used to derive the Nyquist frequency.
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

        // Enforce strict monotonicity — each band must cover at least one bin.
        for (i in 1..BAND_COUNT) {
            if (bandEdges[i] <= bandEdges[i - 1]) bandEdges[i] = bandEdges[i - 1] + 1
        }
        for (i in 1..BAND_COUNT) {
            bandEdges[i] = bandEdges[i].coerceAtMost(halfSize - 1)
        }
    }

    // JNI declarations

    /**
     * Allocates a PFFFT context for a real FFT of [fftSize] samples, pre-computing the
     * Hann window. Returns an opaque handle (pointer cast to Long), or 0 on failure.
     */
    private external fun nativeCreate(fftSize: Int): Long

    /**
     * Copies [bandCount] + 1 bin boundaries from [bandEdges] into the native context.
     * Must be called once after [nativeCreate] and again on every sample-rate change.
     */
    private external fun nativeSetBandEdges(handle: Long, bandEdges: IntArray, bandCount: Int)

    /**
     * Applies the Hann window to [rawSamples], runs the PFFFT real forward transform,
     * maps bins to frequency bands, and writes the results directly into [bandBuffer]
     * using `GetFloatArrayElements` + `ReleaseFloatArrayElements` with mode 0.
     *
     * Zero heap allocations. Safe to call from the audio thread.
     */
    private external fun nativeProcessInto(
            handle: Long,
            rawSamples: FloatArray,
            bandBuffer: FloatArray,
            isOptimized: Boolean
    )

    /** Frees all native resources associated with [handle]. */
    private external fun nativeDestroy(handle: Long)

    // Companion

    companion object {
        init {
            System.loadLibrary("felicity_audio_engine")
        }

        /** FFT window size — drop to 1024 if it causes stuttering on older devices. */
        const val FFT_SIZE = 4096

        /** Number of frequency bands produced per window. */
        const val BAND_COUNT = 40

        private const val DEFAULT_SAMPLE_RATE = 44_100
    }
}