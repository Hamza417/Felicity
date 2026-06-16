/**
 * @file oboe-player.h
 * @brief Oboe output stream context for the Felicity DSP engine.
 *
 * Defines [OboeContext], the aggregate state for one Oboe output stream.
 *
 * Oboe is Google's C++ audio library that sits transparently on top of both
 * AAudio (API 26+) and OpenSL ES (older devices). At runtime Oboe picks the
 * best available backend automatically, so callers do not need to branch on
 * Android version at all.
 *
 * Two important correctness properties guaranteed by [oboe-player.cpp]:
 *
 * 1. **Format fallback safety.** [oboe::AudioFormat::Float] is only a hint; the
 *    HAL or Oboe's internal resampler may negotiate [oboe::AudioFormat::I16].
 *    After opening the stream the implementation reads the actual format via
 *    [stream->getFormat()] and stores it in [actualFormat]. The write path in
 *    [nativeOboeWrite] inspects this field and performs a NEON-accelerated
 *    float→int16 conversion when necessary, so callers always hand over
 *    [FloatArray] data regardless of what the HAL accepted.
 *
 * 2. **Bluetooth buffer-starvation prevention.** When [safeBufferMode] is true
 *    (set by the Kotlin caller when a Bluetooth output device is detected) the
 *    stream is opened with [oboe::PerformanceMode::None] and a larger buffer
 *    to accommodate the Bluetooth stack's latency.
 *
 * @author Hamza417
 */

#pragma once

#include "oboe/Oboe.h"
#include <atomic>
#include <cstdint>
#include <memory>
#include <string>

/**
 * Aggregate state for a single Oboe output stream.
 *
 * One [OboeContext] is heap-allocated per [nativeOboeCreate] call and freed by
 * [nativeOboeDestroy]. The opaque pointer is handed to Kotlin as a jlong handle.
 *
 * @author Hamza417
 */
struct OboeContext {
    /** The underlying Oboe audio output stream. Null when not open. */
    std::shared_ptr<oboe::AudioStream> stream;

    /** Sample rate in Hz the stream was opened with. */
    int32_t sampleRate;

    /** Number of interleaved audio channels (1 = mono, 2 = stereo). */
    int32_t channelCount;

    /**
     * The PCM format Oboe / the HAL actually negotiated after opening the stream.
     * May differ from [oboe::AudioFormat::Float] when the HAL or driver layer
     * rejects float and the builder falls back to [oboe::AudioFormat::I16].
     * [nativeOboeWrite] consults this field to decide whether conversion is needed.
     */
    oboe::AudioFormat actualFormat;

    /**
     * Which audio API Oboe chose at runtime (AAudio or OpenSL ES).
     * Stored as a string so it can be returned cheaply to Kotlin for display.
     */
    std::string actualApiName;

    /**
     * When true the stream was opened with [oboe::PerformanceMode::None] and a
     * larger buffer to accommodate Bluetooth audio latency. When false the stream
     * uses [oboe::PerformanceMode::LowLatency] for the direct-to-HAL fast path.
     */
    bool safeBufferMode;

    /**
     * Heap scratch buffer used by [nativeOboeWrite] for float→int16 conversion
     * when [actualFormat] is [oboe::AudioFormat::I16]. Null when no conversion is
     * needed or before the first write.
     */
    int16_t *conversionBuffer;

    /** Current capacity of [conversionBuffer] in samples. */
    int32_t conversionBufferCapacity;

    /** True after the stream is successfully started; cleared by [nativeOboeStop]. */
    std::atomic<bool> running;

    /**
     * Most recently measured output latency in milliseconds.
     * Updated by [nativeOboeGetLatencyMs]; -1 when unavailable.
     */
    int32_t latencyMs;
};

