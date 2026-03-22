//
// Created by Hamza on 23-03-2026.
//

/**
 * JNI bridge that exposes a PFFFT-backed real-valued FFT to [VisualizerAudioProcessor].
 *
 * Each native context allocates SIMD-aligned buffers and a pre-computed Hann window so
 * that the Kotlin side only needs to pass raw mono PCM samples. Band-edge mapping is
 * stored inside the context and updated via [nativeSetBandEdges], allowing the Kotlin
 * layer to recompute log-spaced edges whenever the sample rate changes without
 * recreating the PFFFT plan. [nativeProcessInto] writes band magnitudes directly into
 * a pre-allocated Java float array — zero allocations on the audio hot path.
 *
 * @author Hamza417
 */

#include <jni.h>
#include <android/log.h>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include "pffft/pffft.h"

#define LOG_TAG "VisualizerFFT"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

/**
 * Aggregates all PFFFT state for a single real-valued FFT plan plus the
 * frequency-band mapping needed by [nativeProcessInto].
 *
 * All float buffers that PFFFT touches must be SIMD-aligned (16-byte); they are
 * therefore allocated with [pffft_aligned_malloc] / freed with [pffft_aligned_free].
 * The Hann window and the band-edge array are plain heap allocations because PFFFT
 * never reads them directly.
 */
struct FFTContext {
    PFFFT_Setup *setup;     ///< Opaque PFFFT plan for a real transform of [size] samples.
    float *input;     ///< Aligned input buffer — receives windowed PCM before the transform.
    float *output;    ///< Aligned output buffer — receives the packed half-complex result.
    float *work;      ///< Aligned scratch buffer required by pffft_transform_ordered.
    float *window;    ///< Pre-computed Hann window w[i] = 0.5*(1−cos(2π·i/(N−1))).
    int size;      ///< FFT size N; must be a power of two and ≥ 32.
    int bandCount; ///< Number of frequency bands; set by nativeSetBandEdges.
    int *bandEdges; ///< Band boundary bin indices, length = bandCount + 1; nullptr until set.
};

extern "C" {

/**
 * Allocates a native [FFTContext] for a real FFT of [fftSize] samples and pre-computes
 * the Hann window. Band-edge data is not set here — call [nativeSetBandEdges] afterward.
 *
 * @param env     JNI environment pointer.
 * @param thiz    Calling Java/Kotlin object (unused).
 * @param fftSize FFT size — must be a power of two and at least 32.
 * @return        Opaque pointer to [FFTContext] cast to jlong, or 0 on allocation failure.
 */
JNIEXPORT jlong JNICALL
Java_app_simple_felicity_engine_processors_VisualizerAudioProcessor_nativeCreate(
        JNIEnv * /*env*/, jobject /*thiz*/, jint fftSize) {

    auto *ctx = new FFTContext();
    ctx->size = static_cast<int>(fftSize);
    ctx->bandCount = 0;
    ctx->bandEdges = nullptr;
    ctx->setup = pffft_new_setup(fftSize, PFFFT_REAL);
    ctx->input = static_cast<float *>(pffft_aligned_malloc(fftSize * sizeof(float)));
    ctx->output = static_cast<float *>(pffft_aligned_malloc(fftSize * sizeof(float)));
    ctx->work = static_cast<float *>(pffft_aligned_malloc(fftSize * sizeof(float)));
    ctx->window = new float[fftSize];

    if (!ctx->setup || !ctx->input || !ctx->output || !ctx->work || !ctx->window) {
        LOGE("PFFFT context allocation failed for size %d", fftSize);
        if (ctx->setup) pffft_destroy_setup(ctx->setup);
        if (ctx->input) pffft_aligned_free(ctx->input);
        if (ctx->output) pffft_aligned_free(ctx->output);
        if (ctx->work) pffft_aligned_free(ctx->work);
        delete[] ctx->window;
        delete ctx;
        return 0L;
    }

    const double twoPiOverNm1 = 2.0 * M_PI / (fftSize - 1);
    for (int i = 0; i < fftSize; ++i) {
        ctx->window[i] = static_cast<float>(0.5 * (1.0 - cos(twoPiOverNm1 * i)));
    }

    LOGI("PFFFT context created — size=%d", fftSize);
    return reinterpret_cast<jlong>(ctx);
}

/**
 * Stores frequency-band boundary bin indices inside the context.
 *
 * The [bandEdges] array must have [bandCount + 1] elements where each consecutive pair
 * [bandEdges[k], bandEdges[k+1]) defines the half-spectrum bin range for band k.
 * Typically computed by the Kotlin layer via logarithmic spacing and passed here once
 * per sample-rate change.
 *
 * @param env        JNI environment pointer.
 * @param thiz       Calling Java/Kotlin object (unused).
 * @param handle     Opaque pointer returned by [nativeCreate].
 * @param bandEdges  Integer array of length bandCount + 1 with inclusive bin boundaries.
 * @param bandCount  Number of frequency bands.
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_processors_VisualizerAudioProcessor_nativeSetBandEdges(
        JNIEnv *env, jobject /*thiz*/, jlong handle, jintArray bandEdges, jint bandCount) {

    auto *ctx = reinterpret_cast<FFTContext *>(handle);
    if (!ctx) return;

    delete[] ctx->bandEdges;
    ctx->bandCount = static_cast<int>(bandCount);
    ctx->bandEdges = new int[bandCount + 1];

    jint *src = env->GetIntArrayElements(bandEdges, nullptr);
    for (int i = 0; i <= bandCount; ++i) {
        ctx->bandEdges[i] = src[i];
    }
    env->ReleaseIntArrayElements(bandEdges, src, JNI_ABORT);

    LOGI("Band edges updated — bandCount=%d", bandCount);
}

/**
 * Applies the stored Hann window to [rawSamples], executes a forward real FFT via PFFFT,
 * maps the result to frequency bands, and writes the per-band magnitudes directly into
 * the pre-allocated [bandBuffer] array.
 *
 * No heap allocations occur on the hot path. [bandBuffer] is the Kotlin-managed back
 * buffer from the twin-buffer system; JNI_ABORT is used on [rawSamples] (read-only)
 * and mode 0 on [bandBuffer] to commit the computed values back to the JVM.
 *
 * When [isOptimized] is true the processor uses peak magnitude per band plus a treble
 * boost curve for a visually dynamic spectrum. When false it computes true RMS magnitude
 * per band for accuracy.
 *
 * @param env         JNI environment pointer.
 * @param thiz        Calling Java/Kotlin object (unused).
 * @param handle      Opaque pointer returned by [nativeCreate].
 * @param rawSamples  Exactly [fftSize] windowed mono PCM float samples in [-1, 1].
 * @param bandBuffer  Pre-allocated float array of length bandCount to write into.
 * @param isOptimized JNI_TRUE for visualizer-optimized peak+boost mode; JNI_FALSE for RMS.
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_processors_VisualizerAudioProcessor_nativeProcessInto(
        JNIEnv *env, jobject /*thiz*/, jlong handle,
        jfloatArray rawSamples, jfloatArray bandBuffer, jboolean isOptimized) {

    auto *ctx = reinterpret_cast<FFTContext *>(handle);
    if (!ctx || !ctx->bandEdges || ctx->bandCount <= 0) return;

    const int n = ctx->size;
    const int halfSize = n / 2;

    // Apply Hann window into the SIMD-aligned input buffer.
    // JNI_ABORT: we only read rawSamples, no write-back needed.
    jfloat *src = env->GetFloatArrayElements(rawSamples, nullptr);
    for (int i = 0; i < n; ++i) {
        ctx->input[i] = src[i] * ctx->window[i];
    }
    env->ReleaseFloatArrayElements(rawSamples, src, JNI_ABORT);

    // Forward real FFT — packed half-complex ordered output.
    pffft_transform_ordered(ctx->setup, ctx->input, ctx->output, ctx->work, PFFFT_FORWARD);

    // Write band magnitudes directly into the pre-allocated back buffer.
    // Mode 0 commits any JNI copy back to the JVM array.
    jfloat *bands = env->GetFloatArrayElements(bandBuffer, nullptr);

    for (int band = 0; band < ctx->bandCount; ++band) {
        const int startBin = ctx->bandEdges[band];
        const int endBin = (ctx->bandEdges[band + 1] > startBin)
                           ? ctx->bandEdges[band + 1] : startBin + 1;

        if (isOptimized) {
            // VISUALIZER mode: peak magnitude with treble boost for visual impact.
            float maxMag = 0.0f;
            for (int k = startBin; k < endBin && k < halfSize; ++k) {
                float re, im;
                if (k == 0) {
                    re = ctx->output[0];
                    im = 0.0f; // DC bin — purely real
                } else {
                    re = ctx->output[2 * k];
                    im = ctx->output[2 * k + 1];
                }
                const float mag = sqrtf(re * re + im * im);
                if (mag > maxMag) maxMag = mag;
            }
            const float weight = 1.0f + (float) band / (float) ctx->bandCount * 3.0f;
            bands[band] = sqrtf(maxMag * weight);
        } else {
            // SCIENTIFIC mode: true RMS magnitude per band.
            float sumSq = 0.0f;
            int count = 0;
            for (int k = startBin; k < endBin && k < halfSize; ++k) {
                float re, im;
                if (k == 0) {
                    re = ctx->output[0];
                    im = 0.0f;
                } else {
                    re = ctx->output[2 * k];
                    im = ctx->output[2 * k + 1];
                }
                sumSq += re * re + im * im;
                ++count;
            }
            bands[band] = (count > 0) ? sqrtf(sumSq / (float) count) : 0.0f;
        }
    }

    // Commit the written values back to the Java heap.
    env->ReleaseFloatArrayElements(bandBuffer, bands, 0);
}

/**
 * Applies the stored Hann window to [rawSamples], executes a forward real FFT via PFFFT,
 * and returns a float array of per-bin magnitudes for bins 0 through N/2−1.
 *
 * @param env        JNI environment pointer.
 * @param thiz       Calling Java/Kotlin object (unused).
 * @param handle     Opaque pointer returned by [nativeCreate].
 * @param rawSamples Exactly [fftSize] mono PCM float samples in the range [−1, 1].
 * @return           Float array of length N/2 — magnitude |X[k]| for k = 0 … N/2−1.
 */
JNIEXPORT jfloatArray JNICALL
Java_app_simple_felicity_engine_processors_VisualizerAudioProcessor_nativeComputeMagnitudes(
        JNIEnv *env, jobject /*thiz*/, jlong handle, jfloatArray rawSamples) {

    auto *ctx = reinterpret_cast<FFTContext *>(handle);
    if (!ctx) return nullptr;

    const int n = ctx->size;
    const int halfSize = n / 2;

    jfloat *src = env->GetFloatArrayElements(rawSamples, nullptr);
    for (int i = 0; i < n; ++i) {
        ctx->input[i] = src[i] * ctx->window[i];
    }
    env->ReleaseFloatArrayElements(rawSamples, src, JNI_ABORT);

    pffft_transform_ordered(ctx->setup, ctx->input, ctx->output, ctx->work, PFFFT_FORWARD);

    auto *mags = new float[halfSize];
    mags[0] = fabsf(ctx->output[0]);
    for (int k = 1; k < halfSize; ++k) {
        const float re = ctx->output[2 * k];
        const float im = ctx->output[2 * k + 1];
        mags[k] = sqrtf(re * re + im * im);
    }

    jfloatArray result = env->NewFloatArray(halfSize);
    if (result) {
        env->SetFloatArrayRegion(result, 0, halfSize, mags);
    }
    delete[] mags;
    return result;
}

/**
 * Frees all native resources associated with the given [FFTContext].
 *
 * @param env    JNI environment pointer.
 * @param thiz   Calling Java/Kotlin object (unused).
 * @param handle Opaque pointer returned by [nativeCreate].
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_processors_VisualizerAudioProcessor_nativeDestroy(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {

    auto *ctx = reinterpret_cast<FFTContext *>(handle);
    if (!ctx) return;

    pffft_destroy_setup(ctx->setup);
    pffft_aligned_free(ctx->input);
    pffft_aligned_free(ctx->output);
    pffft_aligned_free(ctx->work);
    delete[] ctx->window;
    delete[] ctx->bandEdges;
    delete ctx;

    LOGI("PFFFT context destroyed");
}

} // extern "C"

