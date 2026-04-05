/**
 * @file waveform-extractor.cpp
 * @brief JNI bridge for miniaudio-based waveform amplitude extraction.
 *
 * Decodes an audio file from start to finish in a single linear pass using
 * miniaudio's built-in decoders (dr_mp3, dr_flac, dr_wav). For each bar window
 * of [sampleRate / barsPerSecond] samples the absolute peak is tracked, yielding
 * a compact amplitude array that is then globally normalized to [0.0, 1.0].
 *
 * This approach is substantially faster than the seek-per-bar MediaCodec strategy
 * because there is no per-bar seek overhead, no codec flush, and no JVM object
 * allocation on the hot path — the entire PCM stream is consumed in one sequential
 * read loop at native speed.
 *
 * Supported formats: MP3, FLAC, WAV (via dr_mp3, dr_flac, dr_wav built-ins).
 * For unsupported formats the function returns null and the caller should fall
 * back to a ghost/placeholder waveform.
 *
 * @author Hamza417
 */

/**
 * Strip down miniaudio to the decoder-only feature set.
 * MA_NO_DEVICE_IO disables all playback and capture backends, removing
 * the dependency on AAudio, OpenSL ES, and any other platform audio API.
 * MA_NO_ENCODING and MA_NO_GENERATION exclude unused encoder and generator code.
 * MA_NO_ENGINE and MA_NO_NODE_GRAPH exclude the high-level node-graph engine.
 * MA_NO_RESOURCE_MANAGER excludes the async resource loader.
 */
#define MA_NO_DEVICE_IO
#define MA_NO_ENCODING
#define MA_NO_GENERATION
#define MA_NO_ENGINE
#define MA_NO_NODE_GRAPH
#define MA_NO_RESOURCE_MANAGER
#define MA_IMPLEMENTATION

#include "miniaudio.h"

#include <jni.h>
#include <android/log.h>
#include <cmath>
#include <cstdlib>
#include <cstring>

#define WFX_TAG  "WaveformExtractor"
#define WFX_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, WFX_TAG, __VA_ARGS__)
#define WFX_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  WFX_TAG, __VA_ARGS__)

/** Number of f32 frames decoded per iteration of the read loop. */
static constexpr ma_uint64 kReadChunkFrames = 4096;

extern "C" {

/**
 * Decodes the audio file at [jPath] to mono float32 PCM using miniaudio and
 * computes the peak absolute amplitude for every bar window of
 * [sampleRate / barsPerSecond] frames.  The resulting array is globally
 * normalized so the loudest bar equals 1.0.
 *
 * The total number of bars is derived from [durationMs] and [barsPerSecond];
 * any bars beyond the decoded frame count are filled with the last observed peak.
 *
 * @param env          JNI environment pointer.
 * @param thiz         Calling Kotlin object (unused).
 * @param jPath        Absolute path to the audio file.
 * @param durationMs   Track duration in milliseconds; used to pre-size the output.
 * @param barsPerSecond Number of amplitude bars per second (typically 1).
 * @return Normalized [FloatArray] of length (durationMs / 1000) * barsPerSecond,
 *         or null if the decoder could not open the file.
 */
JNIEXPORT jfloatArray JNICALL
Java_app_simple_felicity_engine_processors_WaveformExtractor_nativeExtractWaveform(
        JNIEnv *env, jobject /*thiz*/,
        jstring jPath, jlong durationMs, jint barsPerSecond) {

    if (barsPerSecond <= 0 || durationMs <= 0) {
        WFX_LOGE("nativeExtractWaveform: invalid args — durationMs=%lld barsPerSecond=%d",
                 (long long) durationMs, (int) barsPerSecond);
        return nullptr;
    }

    const char *path = env->GetStringUTFChars(jPath, nullptr);
    if (!path) return nullptr;

    /**
     * Request mono float32 output at the file's native sample rate (0 = keep source rate).
     * Mono downmix avoids allocating a stereo interleaved buffer and halves the data
     * we need to scan per frame.
     */
    ma_decoder_config cfg = ma_decoder_config_init(ma_format_f32, 1, 0);
    ma_decoder decoder;

    ma_result openResult = ma_decoder_init_file(path, &cfg, &decoder);
    env->ReleaseStringUTFChars(jPath, path);

    if (openResult != MA_SUCCESS) {
        WFX_LOGE("nativeExtractWaveform: ma_decoder_init_file failed — result=%d",
                 (int) openResult);
        return nullptr;
    }

    const ma_uint32 sampleRate = decoder.outputSampleRate;
    if (sampleRate == 0) {
        WFX_LOGE("nativeExtractWaveform: decoder reported sampleRate=0");
        ma_decoder_uninit(&decoder);
        return nullptr;
    }

    /** Number of bars derived from the reported duration and desired density. */
    const long long rawBars = (durationMs / 1000LL) * (long long) barsPerSecond;
    const int totalBars = (rawBars > 0) ? (int) rawBars : 1;

    /** Number of decoded mono frames that correspond to one visual bar. */
    const ma_uint64 framesPerBar = (ma_uint64) (sampleRate / (ma_uint32) barsPerSecond);

    /** Heap-allocate the output array (zero-initialized). */
    auto *amplitudes = static_cast<float *>(calloc(totalBars, sizeof(float)));
    if (!amplitudes) {
        WFX_LOGE("nativeExtractWaveform: allocation failed for %d bars", totalBars);
        ma_decoder_uninit(&decoder);
        return nullptr;
    }

    /** Stack-allocated decode buffer — avoid heap churn on the hot path. */
    float chunk[kReadChunkFrames];

    float barPeak = 0.0f;
    ma_uint64 framesInBar = 0;
    int barIndex = 0;

    while (barIndex < totalBars) {

        ma_uint64 framesRead = 0;
        ma_result readResult = ma_decoder_read_pcm_frames(
                &decoder, chunk, kReadChunkFrames, &framesRead);

        if (framesRead == 0) break;

        for (ma_uint64 i = 0; i < framesRead; ++i) {
            const float absVal = fabsf(chunk[i]);
            if (absVal > barPeak) barPeak = absVal;
            ++framesInBar;

            if (framesInBar >= framesPerBar) {
                amplitudes[barIndex] = barPeak;
                ++barIndex;
                barPeak = 0.0f;
                framesInBar = 0;
                if (barIndex >= totalBars) break;
            }
        }

        if (readResult == MA_AT_END) break;
        if (readResult != MA_SUCCESS) {
            WFX_LOGE("nativeExtractWaveform: read error at bar %d — result=%d",
                     barIndex, (int) readResult);
            break;
        }
    }

    /**
     * Fill any bars that were not reached (e.g., if the file was shorter than the
     * declared duration) with the last observed peak so the tail is not silenced.
     */
    while (barIndex < totalBars) {
        amplitudes[barIndex++] = barPeak;
    }

    ma_decoder_uninit(&decoder);

    /**
     * Global normalization pass: scale every bar so the loudest one equals 1.0.
     * Skip normalization if the track was completely silent to avoid division by zero.
     */
    float globalMax = 0.0f;
    for (int i = 0; i < totalBars; ++i) {
        if (amplitudes[i] > globalMax) globalMax = amplitudes[i];
    }
    if (globalMax > 0.0f) {
        const float invMax = 1.0f / globalMax;
        for (int i = 0; i < totalBars; ++i) {
            amplitudes[i] *= invMax;
        }
    }

    /** Wrap the native array in a Java float array and return it. */
    jfloatArray jResult = env->NewFloatArray(totalBars);
    if (jResult) {
        env->SetFloatArrayRegion(jResult, 0, totalBars, amplitudes);
    }

    free(amplitudes);

    WFX_LOGI("nativeExtractWaveform: extracted %d bars at %u Hz", totalBars, (unsigned) sampleRate);
    return jResult;
}

} // extern "C"

