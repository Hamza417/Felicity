package app.simple.felicity.engine.processors

import android.util.Log

/**
 * JNI handle for a native AAudio output stream.
 *
 * This class is **not** an [androidx.media3.common.audio.AudioProcessor]. It is a thin
 * Kotlin wrapper around the native [AaudioContext] defined in [aaudio-player.cpp] and
 * is intended to be owned and driven exclusively by [AaudioAudioSink].
 *
 * Two correctness properties are baked in at the native level:
 *
 * - **Format fallback safety**: if the HAL rejects [AAUDIO_FORMAT_PCM_FLOAT] and opens
 *   an [AAUDIO_FORMAT_PCM_I16] stream instead, [nativeAaudioWrite] detects this via
 *   [AAudioStream_getFormat] and performs NEON-accelerated float→int16 conversion on
 *   every [write] call. Callers always supply a [FloatArray]; the conversion is
 *   invisible here. Use [getActualFormat] to log or display what the HAL gave.
 *
 * - **Bluetooth buffer safety**: when [useSafeBuffers] is `true` the stream is opened
 *   with [AAUDIO_PERFORMANCE_MODE_NONE] + [AAUDIO_SHARING_MODE_SHARED] and its buffer
 *   is enlarged to 8× the burst count, preventing the A2DP / BLE stack from starving.
 *
 * Lifecycle:
 * ```
 * AaudioOutputProcessor(sampleRate, channelCount, useSafeBuffers)
 *     .start()
 *     .write(floatPcm)   // audio thread
 *     .stop()
 *     .release()
 * ```
 *
 * @param sampleRate    Target sample rate in Hz.
 * @param channelCount  Number of interleaved output channels (1 = mono, 2 = stereo).
 * @param useSafeBuffers Pass `true` when a Bluetooth output device is active to enable
 *                       [AAUDIO_PERFORMANCE_MODE_NONE] and the 8× burst buffer.
 *
 * @author Hamza417
 */
class AaudioOutputProcessor(
        sampleRate: Int,
        channelCount: Int,
        useSafeBuffers: Boolean = false
) {

    private var nativeHandle: Long = 0L

    init {
        nativeHandle = nativeAaudioCreate(sampleRate, channelCount, useSafeBuffers)
        if (nativeHandle == 0L) {
            Log.e(TAG, "nativeAaudioCreate returned 0 — check logcat for native errors")
        }
    }

    /** True when the native stream was opened successfully and is ready to use. */
    val isReady: Boolean
        get() = nativeHandle != 0L

    /**
     * Starts the stream (transitions to STARTED state).
     *
     * @return True on success.
     */
    fun start(): Boolean {
        if (nativeHandle == 0L) return false
        return nativeAaudioStart(nativeHandle)
    }

    /**
     * Writes interleaved float32 PCM to the stream.
     * The native layer converts to int16 if the HAL negotiated [AAUDIO_FORMAT_PCM_I16].
     *
     * @param pcmBuffer Interleaved float PCM; length = frameCount × channelCount.
     */
    fun write(pcmBuffer: FloatArray) {
        if (nativeHandle == 0L) return
        nativeAaudioWrite(nativeHandle, pcmBuffer)
    }

    /**
     * Returns the estimated output latency in milliseconds, or -1 if unavailable.
     */
    fun getLatencyMs(): Int {
        if (nativeHandle == 0L) return -1
        return nativeAaudioGetLatencyMs(nativeHandle)
    }

    /**
     * Returns the PCM format the HAL actually negotiated after the stream was opened.
     *
     * The returned integer maps to the AAudio format constants:
     *   - `1` = [AAUDIO_FORMAT_PCM_I16] — HAL rejected float; conversion is active.
     *   - `2` = [AAUDIO_FORMAT_PCM_FLOAT] — optimal float path; no conversion needed.
     *   - `-1` — stream not open.
     */
    fun getActualFormat(): Int {
        if (nativeHandle == 0L) return -1
        return nativeAaudioGetActualFormat(nativeHandle)
    }

    /**
     * Returns a human-readable label for [getActualFormat], suitable for logs and
     * the audio pipeline snapshot display.
     */
    fun getActualFormatName(): String = when (getActualFormat()) {
        AAUDIO_FORMAT_PCM_FLOAT -> "PCM_FLOAT (32-bit)"
        AAUDIO_FORMAT_PCM_I16   -> "PCM_I16 (16-bit, converted)"
        else                    -> "Unknown"
    }

    /**
     * Stops the stream without closing it. Safe to restart via [start].
     */
    fun stop() {
        if (nativeHandle == 0L) return
        nativeAaudioStop(nativeHandle)
    }

    /**
     * Stops, closes, and frees all native resources. Must not be used after this call.
     */
    fun release() {
        if (nativeHandle == 0L) return
        nativeAaudioDestroy(nativeHandle)
        nativeHandle = 0L
        Log.i(TAG, "AaudioOutputProcessor released")
    }

    // JNI declarations

    /**
     * Opens the AAudio stream.
     *
     * @param sampleRate    Sample rate in Hz.
     * @param channelCount  Channel count (1 or 2).
     * @param useSafeBuffers True to enable Bluetooth-safe performance mode and buffer sizing.
     * @return Opaque handle, or 0 on failure.
     */
    private external fun nativeAaudioCreate(
            sampleRate: Int,
            channelCount: Int,
            useSafeBuffers: Boolean
    ): Long

    private external fun nativeAaudioStart(handle: Long): Boolean
    private external fun nativeAaudioWrite(handle: Long, pcmBuffer: FloatArray)
    private external fun nativeAaudioGetLatencyMs(handle: Long): Int

    /**
     * Returns the [aaudio_format_t] constant the HAL actually used after opening the stream.
     *
     * @param handle Opaque handle from [nativeAaudioCreate].
     * @return Format constant ([AAUDIO_FORMAT_PCM_FLOAT] = 2, [AAUDIO_FORMAT_PCM_I16] = 1),
     *         or -1 if the stream is not open.
     */
    private external fun nativeAaudioGetActualFormat(handle: Long): Int

    private external fun nativeAaudioStop(handle: Long)
    private external fun nativeAaudioDestroy(handle: Long)

    companion object {
        private const val TAG = "AaudioOutputProcessor"

        /** Matches [AAUDIO_FORMAT_PCM_I16] in [aaudio/AAudio.h]. */
        const val AAUDIO_FORMAT_PCM_I16   = 1

        /** Matches [AAUDIO_FORMAT_PCM_FLOAT] in [aaudio/AAudio.h]. */
        const val AAUDIO_FORMAT_PCM_FLOAT = 2
    }
}
