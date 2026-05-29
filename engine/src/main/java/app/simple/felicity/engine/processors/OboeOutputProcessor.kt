package app.simple.felicity.engine.processors

import android.util.Log

/**
 * JNI handle for a native Oboe output stream.
 *
 * Oboe is Google's C++ audio library that sits on top of both AAudio and OpenSL ES.
 * On devices running Android 8.0 (API 26) or higher it routes through AAudio for
 * low latency; on older devices it falls back to OpenSL ES automatically, giving you
 * the best available path without any extra code on the Kotlin side.
 *
 * This class is **not** an [androidx.media3.common.audio.AudioProcessor]. It is a thin
 * Kotlin wrapper around the native OboeContext defined in oboe-player.cpp and is
 * intended to be owned and driven exclusively by [FelicityAudioSink].
 *
 * Lifecycle:
 * ```
 * OboeOutputProcessor(sampleRate, channelCount, useSafeBuffers)
 *     .start()
 *     .write(floatPcm, length)   // audio thread
 *     .stop()
 *     .release()
 * ```
 *
 * @param sampleRate     Target sample rate in Hz.
 * @param channelCount   Number of interleaved output channels (1 = mono, 2 = stereo).
 * @param useSafeBuffers Pass `true` when a Bluetooth output device is active to use
 *                       a larger buffer size that prevents the A2DP stack from starving.
 *
 * @author Hamza417
 */
class OboeOutputProcessor(
        sampleRate: Int,
        channelCount: Int,
        useSafeBuffers: Boolean = false
) {

    private var nativeHandle: Long = 0L

    init {
        nativeHandle = nativeOboeCreate(sampleRate, channelCount, useSafeBuffers)
        if (nativeHandle == 0L) {
            Log.e(TAG, "nativeOboeCreate returned 0 — check logcat for native errors")
        }
    }

    /** True when the native stream was opened successfully and is ready to accept audio. */
    val isReady: Boolean
        get() = nativeHandle != 0L

    /**
     * Starts the stream so it is ready to receive audio data.
     *
     * @return True on success, false if the native stream was never opened.
     */
    fun start(): Boolean {
        if (nativeHandle == 0L) return false
        return nativeOboeStart(nativeHandle)
    }

    /**
     * Sends the first [length] samples from [pcmBuffer] to the hardware.
     * The native layer handles any format conversion the HAL requires.
     *
     * @param pcmBuffer Interleaved float32 PCM scratch buffer.
     * @param length    Number of valid samples to write from the start of the array.
     */
    fun write(pcmBuffer: FloatArray, length: Int) {
        if (nativeHandle == 0L) return
        val buf = if (pcmBuffer.size == length) pcmBuffer else pcmBuffer.copyOf(length)
        nativeOboeWrite(nativeHandle, buf)
    }

    /**
     * Returns the estimated round-trip output latency in milliseconds,
     * or -1 if the stream is not open or the value is unavailable.
     */
    fun getLatencyMs(): Int {
        if (nativeHandle == 0L) return -1
        return nativeOboeGetLatencyMs(nativeHandle)
    }

    /**
     * Returns a human-readable label describing the audio API Oboe chose at runtime
     * (either "AAudio" or "OpenSL ES"), suitable for the pipeline snapshot display.
     */
    fun getActualApiName(): String {
        if (nativeHandle == 0L) return "Unknown"
        return nativeOboeGetApiName(nativeHandle)
    }

    /** Pauses output without closing the stream. Safe to restart via [start]. */
    fun stop() {
        if (nativeHandle == 0L) return
        nativeOboeStop(nativeHandle)
    }

    /** Stops, closes, and frees all native resources. Must not be used after this call. */
    fun release() {
        if (nativeHandle == 0L) return
        nativeOboeDestroy(nativeHandle)
        nativeHandle = 0L
        Log.i(TAG, "OboeOutputProcessor released")
    }

    private external fun nativeOboeCreate(sampleRate: Int, channelCount: Int, useSafeBuffers: Boolean): Long
    private external fun nativeOboeStart(handle: Long): Boolean
    private external fun nativeOboeWrite(handle: Long, pcmBuffer: FloatArray)
    private external fun nativeOboeGetLatencyMs(handle: Long): Int
    private external fun nativeOboeGetApiName(handle: Long): String
    private external fun nativeOboeStop(handle: Long)
    private external fun nativeOboeDestroy(handle: Long)

    companion object {
        private const val TAG = "OboeOutputProcessor"
    }
}

