/**
 * @file oboe-player.cpp
 * @brief Oboe output stream implementation for the Felicity DSP engine.
 *
 * This file provides the full JNI layer that backs [OboeOutputProcessor.kt].
 * Every function here maps 1-to-1 to a Kotlin [external fun] declaration in
 * that class.
 *
 * Oboe is Google's open-source C++ audio library. Under the hood it routes
 * through AAudio on Android 8.0 (API 26) and higher, and automatically falls
 * back to OpenSL ES on older devices — giving the best available audio path
 * with zero version-branching in this code.
 *
 * JNI surface exported by this file:
 *   [nativeOboeCreate]        — open stream; chooses perf mode based on [useSafeBuffers].
 *   [nativeOboeStart]         — transition stream to the STARTED state.
 *   [nativeOboeWrite]         — write float PCM; converts to int16 when the HAL needs it.
 *   [nativeOboeGetLatencyMs]  — latency estimate using Oboe's calculateLatencyMillis.
 *   [nativeOboeGetApiName]    — returns "AAudio" or "OpenSL ES" to show in the UI.
 *   [nativeOboeStop]          — pause stream without closing it.
 *   [nativeOboeDestroy]       — stop, close, and free all resources.
 *
 * @author Hamza417
 */

#include <jni.h>
#include <android/log.h>
#include "oboe/Oboe.h"
#include <cstdlib>
#include <cstring>
#include <string>

#include "oboe-player.h"

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>
#define OBOE_NEON_ENABLED 1
#else
#define OBOE_NEON_ENABLED 0
#endif

#define OBOE_TAG  "FelicityOboe"
#define OBOE_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, OBOE_TAG, __VA_ARGS__)
#define OBOE_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  OBOE_TAG, __VA_ARGS__)
#define OBOE_LOGW(...) __android_log_print(ANDROID_LOG_WARN,  OBOE_TAG, __VA_ARGS__)

/**
 * Converts [numSamples] float32 samples from [src] to signed 16-bit integers in [dst].
 * Uses NEON to process 4 samples per instruction on ARM; scalar fallback otherwise.
 * Values are scaled by 32767 and clamped to [-32768, 32767].
 *
 * @param src        Input float32 PCM samples in approximately [-1.0, 1.0].
 * @param dst        Output int16 buffer of at least [numSamples] elements.
 * @param numSamples Total number of samples to convert.
 */
static void oboeConvertFloatToInt16(const float *__restrict src,
                                    int16_t *__restrict dst,
                                    int32_t numSamples) {
#if OBOE_NEON_ENABLED
    const float32x4_t vScale = vdupq_n_f32(32767.0f);
    const float32x4_t vMax   = vdupq_n_f32( 32767.0f);
    const float32x4_t vMin   = vdupq_n_f32(-32768.0f);

    int32_t i = 0;
    for (; i <= numSamples - 4; i += 4) {
        float32x4_t f   = vld1q_f32(src + i);
        float32x4_t s   = vminq_f32(vmaxq_f32(vmulq_f32(f, vScale), vMin), vMax);
        int32x4_t   i32 = vcvtq_s32_f32(s);
        int16x4_t   i16 = vmovn_s32(i32);
        vst1_s16(dst + i, i16);
    }
    /** Scalar tail for any remaining samples that didn't fill a full NEON vector. */
    for (; i < numSamples; ++i) {
        float sv = src[i] * 32767.0f;
        dst[i] = static_cast<int16_t>(
                sv < -32768.0f ? -32768 : (sv > 32767.0f ? 32767 : static_cast<int16_t>(sv)));
    }
#else
    for (int32_t i = 0; i < numSamples; ++i) {
        float sv = src[i] * 32767.0f;
        dst[i] = static_cast<int16_t>(
                sv < -32768.0f ? -32768 : (sv > 32767.0f ? 32767 : static_cast<int16_t>(sv)));
    }
#endif
}

/**
 * Returns a human-readable name for the Oboe audio API constant.
 * Used to populate [OboeContext::actualApiName] right after the stream is opened.
 */
static const char *apiName(oboe::AudioApi api) {
    switch (api) {
        case oboe::AudioApi::AAudio:
            return "AAudio";
        case oboe::AudioApi::OpenSLES:
            return "OpenSL ES";
        default:
            return "Unknown";
    }
}

extern "C" {

/**
 * Opens an Oboe output stream and allocates an [OboeContext].
 *
 * When [useSafeBuffers] is false (normal / wired output):
 *   - [oboe::PerformanceMode::LowLatency] with [oboe::SharingMode::Exclusive]
 *     (Oboe will auto-fall back to Shared on failure), buffer = ~40 ms headroom.
 *
 * When [useSafeBuffers] is true (Bluetooth output detected by caller):
 *   - [oboe::PerformanceMode::None] with [oboe::SharingMode::Shared],
 *     buffer = ~80 ms headroom to prevent the Bluetooth stack from starving.
 *
 * After the stream opens, [OboeContext::actualFormat] is read from the stream
 * so the write path can decide whether float→int16 conversion is needed.
 *
 * @param env           JNI environment pointer.
 * @param thiz          Calling object (unused).
 * @param sampleRate    Target sample rate in Hz.
 * @param channelCount  Number of interleaved output channels (1 or 2).
 * @param useSafeBuffers When [JNI_TRUE], use Bluetooth-safe performance mode and buffer sizing.
 * @return Opaque pointer to [OboeContext] cast to jlong, or 0 on failure.
 */
JNIEXPORT jlong JNICALL
Java_app_simple_felicity_engine_processors_OboeOutputProcessor_nativeOboeCreate(
        JNIEnv * /*env*/, jobject /*thiz*/,
        jint sampleRate, jint channelCount, jboolean useSafeBuffers) {

    auto *ctx = new(std::nothrow) OboeContext();
    if (!ctx) {
        OBOE_LOGE("nativeOboeCreate: OboeContext allocation failed");
        return 0L;
    }

    ctx->sampleRate = static_cast<int32_t>(sampleRate);
    ctx->channelCount = static_cast<int32_t>(channelCount);
    ctx->safeBufferMode = (useSafeBuffers == JNI_TRUE);
    ctx->actualFormat = oboe::AudioFormat::Float; // overwritten after open
    ctx->conversionBuffer = nullptr;
    ctx->conversionBufferCapacity = 0;
    ctx->latencyMs = -1;
    ctx->running.store(false);

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
            ->setUsage(oboe::Usage::Media)
            ->setContentType(oboe::ContentType::Music)
            ->setSampleRate(static_cast<int32_t>(sampleRate))
            ->setChannelCount(static_cast<int32_t>(channelCount))
            ->setFormat(oboe::AudioFormat::Float)
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium);

    if (ctx->safeBufferMode) {
        /**
         * Bluetooth path: use None performance mode with shared access.
         * Exclusive mode is almost never granted for Bluetooth sinks; requesting it
         * wastes an attempt and may cause a longer timeout before fallback.
         */
        builder.setPerformanceMode(oboe::PerformanceMode::None)
                ->setSharingMode(oboe::SharingMode::Shared);
    } else {
        /**
         * Fast path: request exclusive low-latency. Oboe will transparently fall
         * back to shared mode if the hardware rejects the exclusive request.
         */
        builder.setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setSharingMode(oboe::SharingMode::Exclusive);
    }

    oboe::Result result = builder.openStream(ctx->stream);
    if (result != oboe::Result::OK || !ctx->stream) {
        OBOE_LOGE("nativeOboeCreate: openStream failed (%s)",
                  oboe::convertToText(result));
        delete ctx;
        return 0L;
    }

    /**
     * Read back what the HAL actually negotiated — Oboe may have fallen back
     * from float to int16, or from AAudio to OpenSL ES. We record both so
     * the write path can convert if needed and the UI can show the real backend.
     */
    ctx->actualFormat = ctx->stream->getFormat();
    ctx->actualApiName = apiName(ctx->stream->getAudioApi());

    if (ctx->actualFormat != oboe::AudioFormat::Float) {
        OBOE_LOGW("nativeOboeCreate: requested Float but HAL negotiated format=%d "
                  "— nativeOboeWrite will convert float→int16 on every call",
                  static_cast<int>(ctx->actualFormat));
    } else {
        OBOE_LOGI("nativeOboeCreate: HAL confirmed AudioFormat::Float");
    }

    /**
     * Size the buffer to give us enough headroom to absorb Java thread jitter
     * and GC pauses without underruns, regardless of sample rate.
     * We aim for ~40 ms on wired output and ~80 ms on Bluetooth.
     */
    const int32_t actualSr = ctx->stream->getSampleRate();
    const int32_t burstFrames = ctx->stream->getFramesPerBurst();
    const int32_t targetMs = ctx->safeBufferMode ? 80 : 40;
    const int32_t targetFrames = (actualSr * targetMs) / 1000;
    int32_t multiplier = (burstFrames > 0) ? (targetFrames / burstFrames) + 1 : 4;
    if (multiplier < 2) multiplier = 2;
    const int32_t maxCap = ctx->stream->getBufferCapacityInFrames();
    int32_t finalFrames = burstFrames * multiplier;
    if (finalFrames > maxCap) finalFrames = maxCap;

    ctx->stream->setBufferSizeInFrames(finalFrames);

    OBOE_LOGI("OboeContext created — sampleRate=%d (actual=%d), channels=%d, "
              "format=%d, api=%s, safeMode=%d, "
              "burstFrames=%d, bufferFrames=%d (~%d ms)",
              ctx->sampleRate, actualSr, ctx->channelCount,
              static_cast<int>(ctx->actualFormat),
              ctx->actualApiName.c_str(),
              ctx->safeBufferMode ? 1 : 0,
              burstFrames, finalFrames,
              (actualSr > 0) ? (finalFrames * 1000 / actualSr) : -1);

    return reinterpret_cast<jlong>(ctx);
}

/**
 * Requests the Oboe stream to start.
 *
 * @param env    JNI environment pointer.
 * @param thiz   Calling object (unused).
 * @param handle Opaque pointer from [nativeOboeCreate].
 * @return [JNI_TRUE] when started successfully; [JNI_FALSE] otherwise.
 */
JNIEXPORT jboolean JNICALL
Java_app_simple_felicity_engine_processors_OboeOutputProcessor_nativeOboeStart(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {

    auto *ctx = reinterpret_cast<OboeContext *>(handle);
    if (!ctx || !ctx->stream) return JNI_FALSE;

    const oboe::Result result = ctx->stream->requestStart();
    if (result != oboe::Result::OK) {
        OBOE_LOGE("nativeOboeStart: requestStart failed (%s)",
                  oboe::convertToText(result));
        return JNI_FALSE;
    }

    ctx->running.store(true);
    OBOE_LOGI("Oboe stream started (safeBufferMode=%d, api=%s)",
              ctx->safeBufferMode ? 1 : 0,
              ctx->actualApiName.c_str());
    return JNI_TRUE;
}

/**
 * Writes interleaved float32 PCM frames to the Oboe stream.
 *
 * **Format dispatch:** if [OboeContext::actualFormat] is [oboe::AudioFormat::I16],
 * the float input is converted to int16 via [oboeConvertFloatToInt16] before the
 * [stream->write] call. The conversion scratch buffer is grown via [realloc] only
 * when the incoming block size exceeds the previous maximum — in steady state no
 * allocation occurs.
 *
 * @param env       JNI environment pointer.
 * @param thiz      Calling object (unused).
 * @param handle    Opaque pointer from [nativeOboeCreate].
 * @param pcmBuffer Interleaved float PCM; length = numFrames × channelCount.
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_processors_OboeOutputProcessor_nativeOboeWrite(
        JNIEnv *env, jobject /*thiz*/,
        jlong handle, jfloatArray pcmBuffer) {

    auto *ctx = reinterpret_cast<OboeContext *>(handle);
    if (!ctx || !ctx->stream || !ctx->running.load()) return;

    const int totalSamples = env->GetArrayLength(pcmBuffer);
    if (totalSamples <= 0) return;

    const int32_t numFrames = totalSamples / ctx->channelCount;
    if (numFrames <= 0) return;

    jfloat *buf = env->GetFloatArrayElements(pcmBuffer, nullptr);

    /**
     * The write timeout is generous enough to survive an occasional GC pause but
     * short enough that a stalled stream doesn't block the audio thread for long.
     */
    static constexpr int64_t kTimeoutNanos = 100 * 1000000LL; // 100 ms

    if (ctx->actualFormat == oboe::AudioFormat::Float) {
        /** Fast path: HAL accepted float — write directly with no conversion. */
        auto result = ctx->stream->write(buf, numFrames, kTimeoutNanos);
        if (!result) {
            OBOE_LOGE("nativeOboeWrite: float write error (%s)",
                      oboe::convertToText(result.error()));
        }
    } else {
        /**
         * Conversion path: HAL gave us int16.
         * Grow the scratch buffer only when the block size exceeds its current
         * capacity (steady-state: zero allocation).
         */
        if (totalSamples > ctx->conversionBufferCapacity) {
            auto *newBuf = static_cast<int16_t *>(
                    realloc(ctx->conversionBuffer,
                            static_cast<size_t>(totalSamples) * sizeof(int16_t)));
            if (!newBuf) {
                OBOE_LOGE("nativeOboeWrite: conversion buffer realloc failed (%d samples)",
                          totalSamples);
                env->ReleaseFloatArrayElements(pcmBuffer, buf, JNI_ABORT);
                return;
            }
            ctx->conversionBuffer = newBuf;
            ctx->conversionBufferCapacity = totalSamples;
        }

        oboeConvertFloatToInt16(buf, ctx->conversionBuffer, totalSamples);

        auto result = ctx->stream->write(
                ctx->conversionBuffer, numFrames, kTimeoutNanos);
        if (!result) {
            OBOE_LOGE("nativeOboeWrite: int16 write error (%s)",
                      oboe::convertToText(result.error()));
        }
    }

    env->ReleaseFloatArrayElements(pcmBuffer, buf, JNI_ABORT);
}

/**
 * Returns the estimated output latency in milliseconds using Oboe's own
 * [calculateLatencyMillis], which uses an audio timestamp under the hood.
 * Falls back to a buffer-size estimate when the timestamp is unavailable.
 *
 * @param env    JNI environment pointer.
 * @param thiz   Calling object (unused).
 * @param handle Opaque pointer from [nativeOboeCreate].
 * @return Estimated latency in ms, or -1 if unavailable.
 */
JNIEXPORT jint JNICALL
Java_app_simple_felicity_engine_processors_OboeOutputProcessor_nativeOboeGetLatencyMs(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {

    auto *ctx = reinterpret_cast<OboeContext *>(handle);
    if (!ctx || !ctx->stream || !ctx->running.load()) return -1;

    /** Oboe's helper does the AAudioStream_getTimestamp math for us. */
    auto latencyResult = ctx->stream->calculateLatencyMillis();
    if (latencyResult) {
        ctx->latencyMs = static_cast<int32_t>(latencyResult.value());
        return ctx->latencyMs;
    }

    /** Timestamp unavailable — fall back to a simple buffer-size estimate. */
    const int32_t bufFrames = ctx->stream->getBufferSizeInFrames();
    const int32_t sr = ctx->stream->getSampleRate();
    ctx->latencyMs = (sr > 0 && bufFrames > 0)
                     ? static_cast<int32_t>(bufFrames * 1000LL / sr)
                     : -1;
    return ctx->latencyMs;
}

/**
 * Returns the name of the audio API Oboe chose at runtime ("AAudio" or "OpenSL ES").
 * The returned string is valid for the lifetime of the JNI call.
 *
 * @param env    JNI environment pointer.
 * @param thiz   Calling object (unused).
 * @param handle Opaque pointer from [nativeOboeCreate].
 * @return JNI string, or "Unknown" if the handle is invalid.
 */
JNIEXPORT jstring JNICALL
Java_app_simple_felicity_engine_processors_OboeOutputProcessor_nativeOboeGetApiName(
        JNIEnv *env, jobject /*thiz*/, jlong handle) {

    auto *ctx = reinterpret_cast<OboeContext *>(handle);
    if (!ctx || !ctx->stream) {
        return env->NewStringUTF("Unknown");
    }
    return env->NewStringUTF(ctx->actualApiName.c_str());
}

/**
 * Returns the estimated playback position in microseconds using Oboe's
 * [getTimestamp] API to find the most recently presented frame and its
 * wall-clock time, then extrapolating to the current moment.
 *
 * Falls back to a simple buffer-depth estimate when the timestamp is
 * unavailable (typically during the first few frames after stream start).
 *
 * @param env         JNI environment pointer.
 * @param thiz        Calling object (unused).
 * @param handle      Opaque pointer from [nativeOboeCreate].
 * @param sourceEnded JNI_TRUE when the source has no more data to deliver.
 * @return Playback position in microseconds, or -1 if the stream is not ready.
 */
JNIEXPORT jlong JNICALL
Java_app_simple_felicity_engine_processors_OboeOutputProcessor_nativeOboeGetPlaybackPositionUs(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle, jboolean /*sourceEnded*/) {

    auto *ctx = reinterpret_cast<OboeContext *>(handle);
    if (!ctx || !ctx->stream) return -1L;

    const int64_t framesWritten = ctx->stream->getFramesWritten();
    if (framesWritten <= 0) return -1L;

    auto tsResult = ctx->stream->getTimestamp(CLOCK_MONOTONIC);
    if (!tsResult || tsResult.value().position <= 0) {
        /** Timestamp not yet available — estimate from frames written minus buffer depth. */
        const int32_t bufFrames = ctx->stream->getBufferSizeInFrames();
        const int64_t estimated = framesWritten - bufFrames;
        return (estimated > 0 && ctx->sampleRate > 0)
               ? (estimated * 1000000LL / ctx->sampleRate)
               : -1L;
    }

    const int64_t presentedFrames = tsResult.value().position;
    const int64_t presentationTimeNanos = tsResult.value().timestamp;

    /**
     * Extrapolate forward from the last known presented-frame timestamp to now,
     * so the reported position advances smoothly even between callbacks.
     */
    struct timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    const int64_t nowNanos = static_cast<int64_t>(ts.tv_sec) * 1000000000LL + ts.tv_nsec;
    const int64_t elapsedNanos = nowNanos - presentationTimeNanos;
    const int64_t extraFrames = (elapsedNanos > 0 && ctx->sampleRate > 0)
                                ? (elapsedNanos * ctx->sampleRate / 1000000000LL)
                                : 0;

    int64_t currentFrames = presentedFrames + extraFrames;
    if (currentFrames > framesWritten) currentFrames = framesWritten;
    if (currentFrames < 0) currentFrames = 0;

    return (ctx->sampleRate > 0) ? (currentFrames * 1000000LL / ctx->sampleRate) : -1L;
}

/**
 * Gracefully pauses the stream without tearing it down. Safe to restart via [nativeOboeStart].
 *
 * @param env    JNI environment pointer.
 * @param thiz   Calling object (unused).
 * @param handle Opaque pointer from [nativeOboeCreate].
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_processors_OboeOutputProcessor_nativeOboePause(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {

    auto *ctx = reinterpret_cast<OboeContext *>(handle);
    if (!ctx || !ctx->stream) return;

    ctx->running.store(false);

    // Use requestPause instead of requestStop
    const oboe::Result result = ctx->stream->requestPause();
    if (result != oboe::Result::OK) {
        OBOE_LOGW("nativeOboePause: requestPause returned (%s)",
                  oboe::convertToText(result));
    }
    OBOE_LOGI("Oboe stream paused");
}

/**
 * Gracefully stops the stream without closing it. Safe to restart via [nativeOboeStart].
 *
 * @param env    JNI environment pointer.
 * @param thiz   Calling object (unused).
 * @param handle Opaque pointer from [nativeOboeCreate].
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_processors_OboeOutputProcessor_nativeOboeStop(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {

    auto *ctx = reinterpret_cast<OboeContext *>(handle);
    if (!ctx || !ctx->stream) return;

    ctx->running.store(false);
    const oboe::Result result = ctx->stream->requestStop();
    if (result != oboe::Result::OK) {
        OBOE_LOGW("nativeOboeStop: requestStop returned (%s)",
                  oboe::convertToText(result));
    }
    OBOE_LOGI("Oboe stream stopped");
}

/**
 * Stops, closes, and frees all resources owned by the [OboeContext], including
 * the float→int16 [OboeContext::conversionBuffer].
 *
 * @param env    JNI environment pointer.
 * @param thiz   Calling object (unused).
 * @param handle Opaque pointer from [nativeOboeCreate].
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_processors_OboeOutputProcessor_nativeOboeDestroy(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {

    auto *ctx = reinterpret_cast<OboeContext *>(handle);
    if (!ctx) return;

    if (ctx->stream) {
        ctx->running.store(false);
        ctx->stream->requestStop();
        ctx->stream->close();
        ctx->stream.reset();
    }

    free(ctx->conversionBuffer);
    ctx->conversionBuffer = nullptr;
    delete ctx;
    OBOE_LOGI("OboeContext destroyed");
}

} // extern "C"

