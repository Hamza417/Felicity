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
#include <chrono>
#include <mutex>
#include <thread>

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

namespace {
/**
 * How long any JNI entry point will wait to acquire [AaudioContext::streamMutex]
 * before giving up and returning a "not ready" result. Kept short so a stuck
 * recovery thread (or any other bug) can never block the audio thread or a
 * Kotlin caller — this is the key property that prevents the whole-app freeze
 * that used to happen when a phone call disconnected the stream.
 */
    constexpr auto kLockTimeout = std::chrono::milliseconds(50);

/** Longer timeout used only by the recovery thread itself and by destroy(). */
    constexpr auto kRecoveryLockTimeout = std::chrono::milliseconds(1000);

    void aaudioErrorCallback(AAudioStream *stream, void *userData, aaudio_result_t error);
}  // namespace

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

/**
 * Builds and opens a brand new AAudio stream using the parameters already stored
 * on [ctx] (sampleRate / channelCount / safeBufferMode), registers the disconnect
 * error callback on it, and sizes its buffer the same way [nativeAaudioCreate]
 * originally did.
 *
 * Does **not** touch `ctx->stream` itself — callers (both the initial create and
 * the disconnect-recovery path) are responsible for swapping it in under
 * [AaudioContext::streamMutex]. This separation is what lets recovery build the
 * replacement stream *before* taking the lock, so the lock is only ever held for
 * the brief pointer-swap, not for the whole (possibly slow) reopen.
 *
 * @return The freshly opened stream (caller takes ownership), or nullptr on failure.
 */
static AAudioStream *openAaudioStream(AaudioContext *ctx, aaudio_format_t *outFormat) {
    AAudioStreamBuilder *builder = nullptr;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK) {
        AAUDIO_LOGE("openAaudioStream: AAudio_createStreamBuilder failed (%d)", result);
        return nullptr;
    }

    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setSampleRate(builder, ctx->sampleRate);
    AAudioStreamBuilder_setChannelCount(builder, ctx->channelCount);
    AAudioStreamBuilder_setUsage(builder, AAUDIO_USAGE_MEDIA);
    AAudioStreamBuilder_setContentType(builder, AAUDIO_CONTENT_TYPE_MUSIC);
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setErrorCallback(builder, aaudioErrorCallback, ctx);

    AAudioStream *stream = nullptr;
    if (ctx->safeBufferMode) {
        AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_NONE);
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
        result = AAudioStreamBuilder_openStream(builder, &stream);
    } else {
        AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
        result = AAudioStreamBuilder_openStream(builder, &stream);
        if (result != AAUDIO_OK) {
            AAUDIO_LOGW("openAaudioStream: exclusive open failed (%d), retrying shared", result);
            AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
            result = AAudioStreamBuilder_openStream(builder, &stream);
        }
    }

    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK || !stream) {
        AAUDIO_LOGE("openAaudioStream: stream open failed (%d)", result);
        return nullptr;
    }

    *outFormat = AAudioStream_getFormat(stream);
    if (*outFormat != AAUDIO_FORMAT_PCM_FLOAT) {
        AAUDIO_LOGW("openAaudioStream: requested FLOAT but HAL negotiated format=%d "
                    "— nativeAaudioWrite will convert float→int16 on every call",
                    static_cast<int>(*outFormat));
    }

    const int32_t burstFrames = AAudioStream_getFramesPerBurst(stream);
    const int32_t actualSampleRate = AAudioStream_getSampleRate(stream);
    const int32_t targetHeadroomMs = ctx->safeBufferMode ? 80 : 40;
    int32_t targetFrames = (actualSampleRate * targetHeadroomMs) / 1000;
    int32_t calculatedMultiplier = (burstFrames > 0) ? (targetFrames / burstFrames) + 1 : 4;
    if (calculatedMultiplier < 2) calculatedMultiplier = 2;
    int32_t finalFrames = burstFrames * calculatedMultiplier;
    const int32_t maxCapacity = AAudioStream_getBufferCapacityInFrames(stream);
    if (finalFrames > maxCapacity) finalFrames = maxCapacity;
    AAudioStream_setBufferSizeInFrames(stream, finalFrames);

    AAUDIO_LOGI("AAudio stream (re)opened — sampleRate=%d (actual=%d), channels=%d, "
                "format=%d, safeMode=%d, burstFrames=%d, bufferFrames=%d",
                ctx->sampleRate, actualSampleRate, ctx->channelCount,
                static_cast<int>(*outFormat), ctx->safeBufferMode ? 1 : 0,
                burstFrames, finalFrames);

    return stream;
}

namespace {

/**
 * Runs on a detached background thread in response to [aaudioErrorCallback]. Closes
 * the now-dead stream and opens a fresh replacement, swapping it into [ctx->stream]
 * under [AaudioContext::streamMutex] only for the brief pointer-assignment — never
 * while the (potentially slow) HAL open call itself is in flight.
 *
 * Guarded by [AaudioContext::recovering] so a burst of extra error callbacks (AAudio
 * can fire more than one in quick succession) only triggers one reopen at a time, and
 * by [AaudioContext::destroyed] so a reopen that races against [nativeAaudioDestroy]
 * backs off instead of resurrecting a stream nobody owns anymore.
 */
    void recoverAaudioStream(AaudioContext *ctx) {
        if (ctx->destroyed.load()) return;

        bool expected = false;
        if (!ctx->recovering.compare_exchange_strong(expected, true)) {
            return; // Another recovery is already in flight.
        }

        AAudioStream *oldStream = nullptr;
        {
            std::unique_lock<std::timed_mutex> lock(ctx->streamMutex, kRecoveryLockTimeout);
            if (!lock.owns_lock() || ctx->destroyed.load()) {
                AAUDIO_LOGE("recoverAaudioStream: could not safely detach old stream — aborting");
                ctx->recovering.store(false);
                return;
            }
            oldStream = ctx->stream;
            ctx->stream = nullptr;
            ctx->running.store(false);
        }

        if (oldStream) {
            // The AAudio docs are explicit that requestStop()/close() must not be called
            // from inside the error callback itself — but we are on our own dedicated
            // thread here, so this is safe.
            AAudioStream_requestStop(oldStream);
            AAudioStream_close(oldStream);
        }

        aaudio_format_t newFormat = AAUDIO_FORMAT_PCM_FLOAT;
        AAudioStream *newStream = ctx->destroyed.load() ? nullptr : openAaudioStream(ctx,
                                                                                     &newFormat);

        std::unique_lock<std::timed_mutex> lock(ctx->streamMutex, kRecoveryLockTimeout);
        if (!lock.owns_lock() || ctx->destroyed.load()) {
            if (newStream) {
                AAudioStream_requestStop(newStream);
                AAudioStream_close(newStream);
            }
            ctx->recovering.store(false);
            return;
        }

        if (newStream) {
            ctx->stream = newStream;
            ctx->actualFormat = newFormat;
            AAUDIO_LOGI("recoverAaudioStream: stream successfully reopened after disconnect");
        } else {
            AAUDIO_LOGE("recoverAaudioStream: reopen failed — route stays silent until the "
                        "next configure() call recreates it from the Kotlin side");
        }
        ctx->recovering.store(false);
    }

/**
 * Fired by AAudio on its own internal thread when the stream is disconnected —
 * most commonly because a phone call (or other higher-priority client) forced the
 * audio HAL to tear down our exclusive low-latency stream. This is what used to
 * leave the app frozen: every later call into the dead handle either failed
 * silently or blocked, and nothing ever told the Kotlin side to recreate it.
 *
 * Per AAudio's threading rules we must not call requestStop()/close() on the
 * stream from directly within this callback, so all we do here is flip the
 * running flag and hand off to [recoverAaudioStream] on a detached thread.
 */
    void aaudioErrorCallback(AAudioStream * /*stream*/, void *userData, aaudio_result_t error) {
        auto *ctx = static_cast<AaudioContext *>(userData);
        if (!ctx) return;
        AAUDIO_LOGW("aaudioErrorCallback: stream error %d — scheduling recovery", error);
        ctx->running.store(false);
        std::thread(recoverAaudioStream, ctx).detach();
    }

}  // namespace

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
 * Also registers [aaudioErrorCallback] so a later forced disconnect (e.g. a phone
 * call taking over the HAL) triggers automatic stream recovery instead of leaving
 * every subsequent call stuck against a dead handle.
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

    auto *ctx = new(std::nothrow) AaudioContext();
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
    ctx->actualFormat = AAUDIO_FORMAT_PCM_FLOAT;

    AAudioStream *stream = openAaudioStream(ctx, &ctx->actualFormat);
    if (!stream) {
        delete ctx;
        return 0L;
    }
    ctx->stream = stream;

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
    if (!ctx) return JNI_FALSE;

    std::unique_lock<std::timed_mutex> lock(ctx->streamMutex, kLockTimeout);
    if (!lock.owns_lock() || !ctx->stream) return JNI_FALSE;

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
 * **Blocking on purpose:** the write waits (up to [kTimeoutNanos]) for the
 * hardware to have room, which is what keeps playback smooth and gap-free.
 * If the stream has been disconnected (e.g. mid phone-call) and a recovery is
 * in flight, [streamMutex] simply fails to lock within [kLockTimeout] and this
 * returns 0 immediately rather than touching the dead/being-replaced handle —
 * ExoPlayer will retry the same buffer on the next cycle, and once recovery
 * finishes [AaudioOutputProcessor.isRunning] reflects the new stream so
 * [FelicityAudioSink] auto-restarts it.
 *
 * @param env       JNI environment pointer.
 * @param thiz      Calling object (unused).
 * @param handle    Opaque pointer from [nativeAaudioCreate].
 * @param pcmBuffer Interleaved float PCM; length = numFrames × channelCount.
 * @return Number of samples actually accepted by the hardware buffer (may be
 *         less than the input length, or 0 if the buffer is currently full or
 *         the stream is mid-recovery), or -1 if the stream reported an error.
 */
JNIEXPORT jint JNICALL
Java_app_simple_felicity_engine_processors_AaudioOutputProcessor_nativeAaudioWrite(
        JNIEnv *env, jobject /*thiz*/,
        jlong handle, jfloatArray pcmBuffer) {

    auto *ctx = reinterpret_cast<AaudioContext *>(handle);
    if (!ctx) return 0;

    std::unique_lock<std::timed_mutex> lock(ctx->streamMutex, kLockTimeout);
    if (!lock.owns_lock() || !ctx->stream || !ctx->running.load()) return 0;

    const int totalSamples = env->GetArrayLength(pcmBuffer);
    if (totalSamples <= 0) return 0;

    const int32_t numFrames = totalSamples / ctx->channelCount;
    if (numFrames <= 0) return 0;

    jfloat *buf = env->GetFloatArrayElements(pcmBuffer, nullptr);

    static constexpr int64_t kTimeoutNanos = 100'000'000LL; // 100 ms
    jint framesWritten = 0;

    if (ctx->actualFormat == AAUDIO_FORMAT_PCM_FLOAT) {
        /** Fast path: HAL accepted float — write directly with no conversion. */
        const aaudio_result_t written =
                AAudioStream_write(ctx->stream, buf, numFrames, kTimeoutNanos);
        if (written < 0) {
            if (written == AAUDIO_ERROR_DISCONNECTED) {
                AAUDIO_LOGW("nativeAaudioWrite: stream disconnected — recovery will handle it");
                ctx->running.store(false);
            } else {
                AAUDIO_LOGE("nativeAaudioWrite: float write error (%d)", written);
            }
            framesWritten = -1;
        } else {
            framesWritten = written;
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
                return -1;
            }
            ctx->conversionBuffer = newBuf;
            ctx->conversionBufferCapacity = totalSamples;
        }

        convertFloatToInt16(buf, ctx->conversionBuffer, totalSamples);

        const aaudio_result_t written =
                AAudioStream_write(ctx->stream, ctx->conversionBuffer, numFrames, kTimeoutNanos);
        if (written < 0) {
            if (written == AAUDIO_ERROR_DISCONNECTED) {
                AAUDIO_LOGW("nativeAaudioWrite: stream disconnected — recovery will handle it");
                ctx->running.store(false);
            } else {
                AAUDIO_LOGE("nativeAaudioWrite: int16 write error (%d)", written);
            }
            framesWritten = -1;
        } else {
            framesWritten = written;
        }
    }

    env->ReleaseFloatArrayElements(pcmBuffer, buf, JNI_ABORT);

    if (framesWritten < 0) return -1;
    return framesWritten * ctx->channelCount;
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
    if (!ctx) return -1;

    std::unique_lock<std::timed_mutex> lock(ctx->streamMutex, kLockTimeout);
    if (!lock.owns_lock() || !ctx->stream) return -1;
    return static_cast<jint>(ctx->actualFormat);
}

/**
 * Returns whether the stream is currently started and running, straight from the
 * native [AaudioContext::running] flag.
 *
 * This is the single source of truth [AaudioOutputProcessor.isRunning] delegates
 * to on the Kotlin side. Tying it to live native state (instead of a Kotlin-side
 * cached boolean) is what lets [FelicityAudioSink]'s existing
 * `if (!isPaused && !stream.isRunning) stream.start()` auto-restart check in
 * `handleBuffer` notice — on its own, with no extra plumbing — that a disconnect
 * happened and automatically resume playback once recovery has reopened the
 * stream, instead of staying silently stuck forever.
 *
 * @param env    JNI environment pointer.
 * @param thiz   Calling object (unused).
 * @param handle Opaque pointer from [nativeAaudioCreate].
 */
JNIEXPORT jboolean JNICALL
Java_app_simple_felicity_engine_processors_AaudioOutputProcessor_nativeAaudioIsRunning(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {

    auto *ctx = reinterpret_cast<AaudioContext *>(handle);
    if (!ctx) return JNI_FALSE;
    return ctx->running.load() ? JNI_TRUE : JNI_FALSE;
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
    if (!ctx) return -1;

    std::unique_lock<std::timed_mutex> lock(ctx->streamMutex, kLockTimeout);
    if (!lock.owns_lock() || !ctx->stream || !ctx->running.load()) return -1;

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
 * Returns the estimated playback position in microseconds by asking AAudio for the
 * most recently presented frame and its wall-clock timestamp, then extrapolating
 * forward to the current moment. This gives a smooth, low-jitter position that
 * ExoPlayer can use for seeking and progress display.
 *
 * Falls back to a buffer-depth estimate when the timestamp API is unavailable
 * (e.g., during the first few milliseconds after a stream is started).
 *
 * @param env         JNI environment pointer.
 * @param thiz        Calling object (unused).
 * @param handle      Opaque pointer from [nativeAaudioCreate].
 * @param sourceEnded JNI_TRUE when the source has finished delivering data.
 * @return Playback position in microseconds, or -1 if the stream is not ready.
 */
JNIEXPORT jlong JNICALL
Java_app_simple_felicity_engine_processors_AaudioOutputProcessor_nativeAaudioGetPlaybackPositionUs(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle, jboolean sourceEnded) {

    auto *ctx = reinterpret_cast<AaudioContext *>(handle);
    if (!ctx) return -1L;

    std::unique_lock<std::timed_mutex> lock(ctx->streamMutex, kLockTimeout);
    if (!lock.owns_lock() || !ctx->stream) return -1L;

    const int64_t framesWritten = AAudioStream_getFramesWritten(ctx->stream);
    if (framesWritten <= 0) return -1L;

    int64_t presentedFrames = 0;
    int64_t presentationTimeNanos = 0;
    const aaudio_result_t tsResult = AAudioStream_getTimestamp(
            ctx->stream, CLOCK_MONOTONIC, &presentedFrames, &presentationTimeNanos);

    if (tsResult != AAUDIO_OK || presentedFrames <= 0) {
        /** Timestamp not yet available — estimate from frames written minus buffer depth. */
        const int32_t bufFrames = AAudioStream_getBufferSizeInFrames(ctx->stream);
        const int64_t estimated = framesWritten - bufFrames;
        return (estimated > 0 && ctx->sampleRate > 0)
               ? (estimated * 1000000LL / ctx->sampleRate)
               : -1L;
    }

    /**
     * Interpolate from the last known presentation timestamp to "right now".
     * The HAL tells us "frame N was at the speakers at time T". We find out
     * how much time has passed since T and add those extra frames on top.
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
 * Gracefully pauses the stream without tearing it down or flushing the buffers.
 *
 * @param env    JNI environment pointer.
 * @param thiz   Calling object (unused).
 * @param handle Opaque pointer from [nativeAaudioCreate].
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_processors_AaudioOutputProcessor_nativeAaudioPause(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {

    auto *ctx = reinterpret_cast<AaudioContext *>(handle);
    if (!ctx) return;

    ctx->running.store(false);

    std::unique_lock<std::timed_mutex> lock(ctx->streamMutex, kLockTimeout);
    if (!lock.owns_lock() || !ctx->stream) return;

    // Use requestPause instead of requestStop to avoid HAL teardown latency
    const aaudio_result_t result = AAudioStream_requestPause(ctx->stream);
    if (result != AAUDIO_OK) {
        AAUDIO_LOGW("nativeAaudioPause: requestPause returned (%d)", result);
    }
    AAUDIO_LOGI("AAudio stream paused");
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
    if (!ctx) return;

    ctx->running.store(false);

    std::unique_lock<std::timed_mutex> lock(ctx->streamMutex, kLockTimeout);
    if (!lock.owns_lock() || !ctx->stream) return;

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
 * Sets [AaudioContext::destroyed] before taking the lock so any disconnect-recovery
 * thread that is mid-flight (see [recoverAaudioStream]) notices and backs off
 * instead of resurrecting or touching a stream that is being torn down.
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

    ctx->destroyed.store(true);
    ctx->running.store(false);

    AAudioStream *stream = nullptr;
    {
        std::unique_lock<std::timed_mutex> lock(ctx->streamMutex, kRecoveryLockTimeout);
        if (lock.owns_lock()) {
            stream = ctx->stream;
            ctx->stream = nullptr;
        } else {
            AAUDIO_LOGE("nativeAaudioDestroy: could not acquire stream lock in time — "
                        "freeing context without closing the stream (will be reclaimed by "
                        "the OS on process exit if this ever happens)");
        }
    }

    if (stream) {
        AAudioStream_requestStop(stream);
        AAudioStream_close(stream);
    }

    free(ctx->conversionBuffer);
    ctx->conversionBuffer = nullptr;
    delete ctx;
    AAUDIO_LOGI("AaudioContext destroyed");
}

} // extern "C"

