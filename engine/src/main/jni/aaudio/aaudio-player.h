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

/**
 * Aggregate state for the AAudio output stream.
 *
 * One [AaudioContext] is allocated per [nativeAaudioCreate] call and freed by
 * [nativeAaudioDestroy].
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
};
