/**
 * @file aaudio-player.cpp
 * @brief AAudio output stream implementation for the Felicity DSP engine.
 *
 *
 * JNI surface:
 *   [nativeAaudioCreate]          — open stream; accepts [useSafeBuffers].
 *   [nativeAaudioStart]           — request start.
 *   [nativeAaudioWrite]           — write float PCM; converts to int16 if needed.
 *   [nativeAaudioGetLatencyMs]    — timestamp-based latency estimate.
 *   [nativeAaudioGetActualFormat] — returns the HAL-negotiated format constant.
 *   [nativeAaudioStop]            — request stop without closing.
 *   [nativeAaudioDestroy]         — stop, close, free.
 *
 * @author Hamza417
 */

#include <jni.h>
#include <android/log.h>
#include <aaudio/AAudio.h>
#include <cstdlib>
#include <cstring>

#include "aaudio-player.h"

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>
#define AAUDIO_NEON_ENABLED 1
#else
#define AAUDIO_NEON_ENABLED 0
#endif

#define AAUDIO_TAG  "FelicityAaudio"
#define AAUDIO_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, AAUDIO_TAG, __VA_ARGS__)
#define AAUDIO_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  AAUDIO_TAG, __VA_ARGS__)
#define AAUDIO_LOGW(...) __android_log_print(ANDROID_LOG_WARN,  AAUDIO_TAG, __VA_ARGS__)


/**
 * Converts [numSamples] float32 samples from [src] to signed 16-bit integers in [dst].
 * Uses NEON to process 4 samples per instruction on ARM; scalar fallback otherwise.
 * Values are scaled by 32767 and clamped to [-32768, 32767].
 *
 * @param src        Input float32 PCM samples in approximately [-1.0, 1.0].
 * @param dst        Output int16 buffer of at least [numSamples] elements.
 * @param numSamples Total number of samples to convert.
 */
static void convertFloatToInt16(const float *__restrict src,
                                int16_t *__restrict dst,
                                int32_t numSamples) {
#if AAUDIO_NEON_ENABLED
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
    /** Scalar tail for any remaining samples. */
    for (; i < numSamples; ++i) {
        float s = src[i] * 32767.0f;
        dst[i]  = static_cast<int16_t>(
                s < -32768.0f ? -32768 : (s > 32767.0f ? 32767 : static_cast<int16_t>(s)));
    }
#else
    for (int32_t i = 0; i < numSamples; ++i) {
        float s = src[i] * 32767.0f;
        dst[i] = static_cast<int16_t>(
                s < -32768.0f ? -32768 : (s > 32767.0f ? 32767 : static_cast<int16_t>(s)));
    }
#endif
}

extern "C" {

/**
 * Opens an AAudio output stream and allocates an [AaudioContext].
 *
 * When [useSafeBuffers] is false (normal / wired output):
 *   - [AAUDIO_PERFORMANCE_MODE_LOW_LATENCY] with [AAUDIO_SHARING_MODE_EXCLUSIVE]
 *     (falls back to SHARED on failure), buffer = 2× burst size.
 *
 * When [useSafeBuffers] is true (Bluetooth output detected by caller):
 *   - [AAUDIO_PERFORMANCE_MODE_NONE] with [AAUDIO_SHARING_MODE_SHARED],
 *     buffer = 8× burst size to prevent BT stack starvation.
 *
 * In both modes, the HAL-negotiated format is read after opening and stored in
 * [AaudioContext::actualFormat]. If it differs from [AAUDIO_FORMAT_PCM_FLOAT], all
 * subsequent [nativeAaudioWrite] calls will perform float→int16 conversion.
 *
 * @param env          JNI environment pointer.
 * @param thiz         Calling object (unused).
 * @param sampleRate   Target sample rate in Hz.
 * @param channelCount Number of interleaved output channels (1 or 2).
 * @param useSafeBuffers When [JNI_TRUE], use Bluetooth-safe performance mode and buffer sizing.
 * @return Opaque pointer to [AaudioContext] cast to jlong, or 0 on failure.
 */
JNIEXPORT jlong JNICALL
Java_app_simple_felicity_engine_processors_AaudioOutputProcessor_nativeAaudioCreate(
        JNIEnv * /*env*/, jobject /*thiz*/,
        jint sampleRate, jint channelCount, jboolean useSafeBuffers) {

    auto *ctx = static_cast<AaudioContext *>(calloc(1, sizeof(AaudioContext)));
    if (!ctx) {
        AAUDIO_LOGE("nativeAaudioCreate: context allocation failed");
        return 0L;
    }

    ctx->sampleRate = static_cast<int32_t>(sampleRate);
    ctx->channelCount = static_cast<int32_t>(channelCount);
    ctx->safeBufferMode = (useSafeBuffers == JNI_TRUE);
    ctx->running.store(false);
    ctx->latencyMs = -1;
    ctx->stream = nullptr;
    ctx->conversionBuffer = nullptr;
    ctx->conversionBufferCapacity = 0;
    ctx->actualFormat = AAUDIO_FORMAT_PCM_FLOAT; // overwritten after open

    AAudioStreamBuilder *builder = nullptr;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK) {
        AAUDIO_LOGE("nativeAaudioCreate: AAudio_createStreamBuilder failed (%d)", result);
        free(ctx);
        return 0L;
    }

    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setSampleRate(builder, static_cast<int32_t>(sampleRate));
    AAudioStreamBuilder_setChannelCount(builder, static_cast<int32_t>(channelCount));
    AAudioStreamBuilder_setUsage(builder, AAUDIO_USAGE_MEDIA);
    AAudioStreamBuilder_setContentType(builder, AAUDIO_CONTENT_TYPE_MUSIC);
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);

    if (ctx->safeBufferMode) {
        /**
         * Bluetooth path: use NONE performance mode with shared access.
         * Exclusive mode is almost never granted for Bluetooth sinks; requesting it
         * wastes an open attempt and may cause a longer timeout before fallback.
         */
        AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_NONE);
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
        result = AAudioStreamBuilder_openStream(builder, &ctx->stream);
    } else {
        /** Fast path: try exclusive first, fall back to shared. */
        AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
        result = AAudioStreamBuilder_openStream(builder, &ctx->stream);
        if (result != AAUDIO_OK) {
            AAUDIO_LOGW("nativeAaudioCreate: exclusive open failed (%d), retrying shared", result);
            AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
            result = AAudioStreamBuilder_openStream(builder, &ctx->stream);
        }
    }

    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK || !ctx->stream) {
        AAUDIO_LOGE("nativeAaudioCreate: stream open failed (%d)", result);
        free(ctx);
        return 0L;
    }

    /**
     * Bug 1 fix: read the format the HAL actually gave us.
     * The builder's setFormat() is only a preference — the HAL may return
     * AAUDIO_FORMAT_PCM_I16. If that happens, nativeAaudioWrite will convert.
     */
    ctx->actualFormat = AAudioStream_getFormat(ctx->stream);
    if (ctx->actualFormat != AAUDIO_FORMAT_PCM_FLOAT) {
        AAUDIO_LOGW("nativeAaudioCreate: requested FLOAT but HAL negotiated format=%d "
                    "— nativeAaudioWrite will convert float→int16 on every call",
                    static_cast<int>(ctx->actualFormat));
    } else {
        AAUDIO_LOGI("nativeAaudioCreate: HAL confirmed AAUDIO_FORMAT_PCM_FLOAT");
    }

/**
     * Bug 2 fix: Time-based buffer sizing to prevent Hi-Res gaping.
     * We need to guarantee enough milliseconds of headroom to survive Java thread
     * jitter/GC pauses, regardless of how fast the sample rate consumes frames.
     */
    const int32_t burstFrames = AAudioStream_getFramesPerBurst(ctx->stream);
    const int32_t actualSampleRate = AAudioStream_getSampleRate(ctx->stream);

    // Check if the OS actually granted the hi-res sample rate
    if (actualSampleRate != ctx->sampleRate) {
        AAUDIO_LOGW("Requested %d Hz, but HAL forced %d Hz. Timings may shift.",
                    ctx->sampleRate, actualSampleRate);
    }

    // Aim for ~40ms of buffer headroom for wired, and ~80ms for Bluetooth
    const int32_t targetHeadroomMs = ctx->safeBufferMode ? 80 : 40;

    // Calculate how many frames we need to hit that time target
    int32_t targetFrames = (actualSampleRate * targetHeadroomMs) / 1000;

    // AAudio performs best when the buffer is an exact multiple of the burst size
    int32_t calculatedMultiplier = (targetFrames / burstFrames) + 1;

    // Safety clamp (don't go below 2 bursts, don't exceed max capacity)
    if (calculatedMultiplier < 2) calculatedMultiplier = 2;
    int32_t finalFrames = burstFrames * calculatedMultiplier;

    int32_t maxCapacity = AAudioStream_getBufferCapacityInFrames(ctx->stream);
    if (finalFrames > maxCapacity) finalFrames = maxCapacity;

    AAudioStream_setBufferSizeInFrames(ctx->stream, finalFrames);

    const aaudio_sharing_mode_t actualSharing = AAudioStream_getSharingMode(ctx->stream);
    AAUDIO_LOGI("AaudioContext created — sampleRate=%d, channels=%d, "
                "format=%d (float=%d), sharing=%s, safeMode=%d, "
                "burstFrames=%d, bufferFrames=%d (~%d ms)",
                ctx->sampleRate, ctx->channelCount,
                static_cast<int>(ctx->actualFormat),
                static_cast<int>(AAUDIO_FORMAT_PCM_FLOAT),
                (actualSharing == AAUDIO_SHARING_MODE_EXCLUSIVE) ? "EXCLUSIVE" : "SHARED",
                ctx->safeBufferMode ? 1 : 0,
                burstFrames, finalFrames,
                (ctx->sampleRate > 0) ? (finalFrames * 1000 / ctx->sampleRate) : -1);

    return reinterpret_cast<jlong>(ctx);
}

/**
 * Requests the AAudio stream to start.
 *
 * @param env    JNI environment pointer.
 * @param thiz   Calling object (unused).
 * @param handle Opaque pointer from [nativeAaudioCreate].
 * @return [JNI_TRUE] when started successfully; [JNI_FALSE] otherwise.
 */
JNIEXPORT jboolean JNICALL
Java_app_simple_felicity_engine_processors_AaudioOutputProcessor_nativeAaudioStart(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {

    auto *ctx = reinterpret_cast<AaudioContext *>(handle);
    if (!ctx || !ctx->stream) return JNI_FALSE;

    const aaudio_result_t result = AAudioStream_requestStart(ctx->stream);
    if (result != AAUDIO_OK) {
        AAUDIO_LOGE("nativeAaudioStart: requestStart failed (%d)", result);
        return JNI_FALSE;
    }

    ctx->running.store(true);
    AAUDIO_LOGI("AAudio stream started (safeBufferMode=%d)", ctx->safeBufferMode ? 1 : 0);
    return JNI_TRUE;
}

/**
 * Writes interleaved float32 PCM frames to the AAudio stream.
 *
 * **Format dispatch (Bug 1 fix):** if [AaudioContext::actualFormat] is
 * [AAUDIO_FORMAT_PCM_I16], the float input is converted to int16 via
 * [convertFloatToInt16] before the [AAudioStream_write] call. The conversion
 * scratch buffer ([AaudioContext::conversionBuffer]) is grown via [realloc]
 * only when the incoming block size exceeds the previous maximum; in steady
 * state no allocation occurs.
 *
 * @param env       JNI environment pointer.
 * @param thiz      Calling object (unused).
 * @param handle    Opaque pointer from [nativeAaudioCreate].
 * @param pcmBuffer Interleaved float PCM; length = numFrames × channelCount.
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_processors_AaudioOutputProcessor_nativeAaudioWrite(
        JNIEnv *env, jobject /*thiz*/,
        jlong handle, jfloatArray pcmBuffer) {

    auto *ctx = reinterpret_cast<AaudioContext *>(handle);
    if (!ctx || !ctx->stream || !ctx->running.load()) return;

    const int totalSamples = env->GetArrayLength(pcmBuffer);
    if (totalSamples <= 0) return;

    const int32_t numFrames = totalSamples / ctx->channelCount;
    if (numFrames <= 0) return;

    jfloat *buf = env->GetFloatArrayElements(pcmBuffer, nullptr);
    static constexpr int64_t kTimeoutNanos = 100 * 1000000LL;

    if (ctx->actualFormat == AAUDIO_FORMAT_PCM_FLOAT) {
        /** Fast path: HAL accepted float — write directly with no conversion. */
        const aaudio_result_t written =
                AAudioStream_write(ctx->stream, buf, numFrames, kTimeoutNanos);
        if (written < 0) {
            AAUDIO_LOGE("nativeAaudioWrite: float write error (%d)", written);
        }
    } else {
        /**
         * Conversion path: HAL gave us AAUDIO_FORMAT_PCM_I16.
         * Grow the scratch buffer only when the block size exceeds its current
         * capacity (steady-state: zero allocation).
         */
        if (totalSamples > ctx->conversionBufferCapacity) {
            auto *newBuf = static_cast<int16_t *>(
                    realloc(ctx->conversionBuffer, totalSamples * sizeof(int16_t)));
            if (!newBuf) {
                AAUDIO_LOGE("nativeAaudioWrite: conversion buffer realloc failed "
                            "(%d samples)", totalSamples);
                env->ReleaseFloatArrayElements(pcmBuffer, buf, JNI_ABORT);
                return;
            }
            ctx->conversionBuffer = newBuf;
            ctx->conversionBufferCapacity = totalSamples;
        }

        convertFloatToInt16(buf, ctx->conversionBuffer, totalSamples);

        const aaudio_result_t written =
                AAudioStream_write(ctx->stream, ctx->conversionBuffer, numFrames, kTimeoutNanos);
        if (written < 0) {
            AAUDIO_LOGE("nativeAaudioWrite: int16 write error (%d)", written);
        }
    }

    env->ReleaseFloatArrayElements(pcmBuffer, buf, JNI_ABORT);
}

/**
 * Returns the HAL-negotiated PCM format constant for this stream.
 *
 * Callers can compare the returned value against [AAUDIO_FORMAT_PCM_FLOAT] (2)
 * and [AAUDIO_FORMAT_PCM_I16] (1) to log or report the actual output format.
 *
 * @param env    JNI environment pointer.
 * @param thiz   Calling object (unused).
 * @param handle Opaque pointer from [nativeAaudioCreate].
 * @return The [aaudio_format_t] constant, cast to jint; -1 if the handle is invalid.
 */
JNIEXPORT jint JNICALL
Java_app_simple_felicity_engine_processors_AaudioOutputProcessor_nativeAaudioGetActualFormat(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {

    auto *ctx = reinterpret_cast<AaudioContext *>(handle);
    if (!ctx || !ctx->stream) return -1;
    return static_cast<jint>(ctx->actualFormat);
}

/**
 * Returns the estimated output latency in milliseconds.
 *
 * @param env    JNI environment pointer.
 * @param thiz   Calling object (unused).
 * @param handle Opaque pointer from [nativeAaudioCreate].
 * @return Estimated latency in ms, or -1 if unavailable.
 */
JNIEXPORT jint JNICALL
Java_app_simple_felicity_engine_processors_AaudioOutputProcessor_nativeAaudioGetLatencyMs(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {

    auto *ctx = reinterpret_cast<AaudioContext *>(handle);
    if (!ctx || !ctx->stream || !ctx->running.load()) return -1;

    const int64_t framesWritten = AAudioStream_getFramesWritten(ctx->stream);
    int64_t presentedFrames = 0;
    int64_t presentationTimeNanos = 0;

    const aaudio_result_t result = AAudioStream_getTimestamp(
            ctx->stream, CLOCK_MONOTONIC, &presentedFrames, &presentationTimeNanos);

    if (result != AAUDIO_OK || framesWritten <= 0) {
        const int32_t bufFrames = AAudioStream_getBufferSizeInFrames(ctx->stream);
        ctx->latencyMs = (ctx->sampleRate > 0 && bufFrames > 0)
                         ? static_cast<int32_t>(bufFrames * 1000LL / ctx->sampleRate)
                         : -1;
        return ctx->latencyMs;
    }

    const int64_t framesInFlight = framesWritten - presentedFrames;
    ctx->latencyMs = (ctx->sampleRate > 0 && framesInFlight > 0)
                     ? static_cast<int32_t>(framesInFlight * 1000LL / ctx->sampleRate)
                     : 0;
    return ctx->latencyMs;
}

/**
 * Gracefully stops the stream without closing it.
 *
 * @param env    JNI environment pointer.
 * @param thiz   Calling object (unused).
 * @param handle Opaque pointer from [nativeAaudioCreate].
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_processors_AaudioOutputProcessor_nativeAaudioStop(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {

    auto *ctx = reinterpret_cast<AaudioContext *>(handle);
    if (!ctx || !ctx->stream) return;

    ctx->running.store(false);
    const aaudio_result_t result = AAudioStream_requestStop(ctx->stream);
    if (result != AAUDIO_OK) {
        AAUDIO_LOGW("nativeAaudioStop: requestStop returned (%d)", result);
    }
    AAUDIO_LOGI("AAudio stream stopped");
}

/**
 * Stops, closes, and frees all resources owned by the [AaudioContext], including
 * the float→int16 [AaudioContext::conversionBuffer].
 *
 * @param env    JNI environment pointer.
 * @param thiz   Calling object (unused).
 * @param handle Opaque pointer from [nativeAaudioCreate].
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_processors_AaudioOutputProcessor_nativeAaudioDestroy(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {

    auto *ctx = reinterpret_cast<AaudioContext *>(handle);
    if (!ctx) return;

    if (ctx->stream) {
        ctx->running.store(false);
        AAudioStream_requestStop(ctx->stream);
        AAudioStream_close(ctx->stream);
        ctx->stream = nullptr;
    }

    free(ctx->conversionBuffer);
    ctx->conversionBuffer = nullptr;
    free(ctx);
    AAUDIO_LOGI("AaudioContext destroyed");
}

} // extern "C"

