/**
 * @file aaudio-player.h
 * @brief AAudio output stream context for the Felicity DSP engine.
 *
 * Defines [AaudioContext], the aggregate state for one AAudio output stream.
 *
 * Two important correctness properties guaranteed by [aaudio-player.cpp]:
 *
 * 1. **Format fallback safety.** [AAUDIO_FORMAT_PCM_FLOAT] is only a request; the HAL
 *    may honour it with [AAUDIO_FORMAT_PCM_I16] instead. After opening the stream the
 *    implementation reads the actual negotiated format via [AAudioStream_getFormat] and
 *    stores it in [actualFormat]. The write path in [nativeAaudioWrite] inspects this
 *    field and performs a NEON-accelerated float→int16 conversion when necessary, so
 *    callers always hand over [FloatArray] data regardless of what the HAL accepted.
 *
 * 2. **Bluetooth buffer-starvation prevention.** When [safeBufferMode] is true (set by
 *    the Kotlin caller when a Bluetooth output device is detected) the stream is opened
 *    with [AAUDIO_PERFORMANCE_MODE_NONE] and [AAUDIO_SHARING_MODE_SHARED] instead of
 *    [AAUDIO_PERFORMANCE_MODE_LOW_LATENCY] / [AAUDIO_SHARING_MODE_EXCLUSIVE]. After
 *    opening, the buffer is sized to 8× the burst count so the Bluetooth stack can drain
 *    it without underruns.
 *
 * @author Hamza417
 */

#pragma once

#include <aaudio/AAudio.h>
#include <atomic>
#include <cstdint>
#include <mutex>

/**
 * Aggregate state for the AAudio output stream.
 *
 * One [AaudioContext] is allocated per [nativeAaudioCreate] call and freed by
 * [nativeAaudioDestroy].
 *
 * **Disconnect recovery:** Exclusive low-latency AAudio streams are forcibly
 * disconnected by the audio HAL when a higher-priority client needs the DSP —
 * most notably an incoming/outgoing phone call. This is distinct from a normal
 * audio-focus loss (another app ducking/pausing us), which never disconnects
 * the stream. Without handling this, every later call into the dead stream
 * handle (start/pause/write) either fails silently or — on some HALs — blocks
 * forever, which is what causes the whole app to freeze until force-closed.
 * [AAudioStream_setErrorCallback] plus [streamMutex] here let the native layer
 * detect the disconnect and transparently reopen a fresh stream on a background
 * thread, with every Kotlin-facing entry point using a bounded try-lock so it
 * can never hang even if recovery itself is slow or stuck.
 *
 * @author Hamza417
 */
struct AaudioContext {
    /** The underlying AAudio output stream. Null when not open. */
    AAudioStream *stream;

    /** Sample rate in Hz the stream was opened with. */
    int32_t sampleRate;

    /** Number of interleaved audio channels (1 = mono, 2 = stereo). */
    int32_t channelCount;

    /**
     * The PCM format AAudio actually negotiated after opening the stream.
     * This may differ from [AAUDIO_FORMAT_PCM_FLOAT] when the HAL rejects float
     * and silently falls back to [AAUDIO_FORMAT_PCM_I16].
     * [nativeAaudioWrite] consults this field to decide whether conversion is needed.
     */
    aaudio_format_t actualFormat;

    /**
     * When true the stream was opened with [AAUDIO_PERFORMANCE_MODE_NONE] and a
     * larger buffer to accommodate Bluetooth audio latency. When false the stream
     * uses [AAUDIO_PERFORMANCE_MODE_LOW_LATENCY] for the direct-to-HAL fast path.
     */
    bool safeBufferMode;

    /**
     * Heap scratch buffer used by [nativeAaudioWrite] for float→int16 conversion
     * when [actualFormat] is [AAUDIO_FORMAT_PCM_I16]. Null when no conversion is
     * needed or before the first write.
     */
    int16_t *conversionBuffer;

    /** Current capacity of [conversionBuffer] in samples. */
    int32_t conversionBufferCapacity;

    /** True after [AAudioStream_requestStart] succeeds; cleared by [nativeAaudioStop]. */
    std::atomic<bool> running;

    /**
     * Most recently measured output latency in milliseconds.
     * Updated by [nativeAaudioGetLatencyMs]; -1 when unavailable.
     */
    int32_t latencyMs;

    /**
     * Guards every read/write of [stream]. All JNI entry points acquire this with a
     * short bounded timeout (see aaudio-player.cpp) instead of a plain lock, so a
     * slow or stuck recovery can never freeze the audio thread or the caller —
     * worst case a single buffer/call is dropped and retried next time.
     */
    std::timed_mutex streamMutex;

    /**
     * Set while a background reopen (triggered by [aaudioErrorCallback]) is in
     * flight, so a burst of extra error callbacks doesn't spawn duplicate
     * recovery threads racing each other.
     */
    std::atomic<bool> recovering{false};

    /**
     * Flipped to true at the very start of [nativeAaudioDestroy], before it takes
     * the lock, so any recovery thread that is mid-flight bails out early instead
     * of resurrecting a stream nobody owns anymore.
     */
    std::atomic<bool> destroyed{false};
};
