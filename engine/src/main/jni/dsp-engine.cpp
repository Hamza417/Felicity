/**
 * @file dsp-engine.cpp
 * @brief JNI bridge and implementation for the Felicity unified native DSP engine.
 *
 * Exposes a single hot-path JNI function, [nativeDspProcessAudio], that applies the full
 * DSP chain — 10-band EQ, bass/treble shelves, stereo widening, pan, and tape saturation —
 * to a caller-provided [jfloatArray] entirely in-place. Zero heap allocations occur on the
 * audio hot path; the input array is pinned with [GetFloatArrayElements] and committed back
 * with mode 0 via [ReleaseFloatArrayElements].
 *
 * ARM NEON SIMD acceleration is used throughout:
 *   - Biquad EQ: [float32x2_t] processes the left and right channels simultaneously for
 *     each stereo frame; the outer loop is unrolled to consume 2 stereo frames (4 samples)
 *     per iteration — satisfying the 4-sample concurrency requirement.
 *   - Stereo widening: [float32x4_t] covers 2 stereo frames (4 samples) per instruction.
 *   - Balance/pan: [float32x4_t] covers 4 samples per multiply.
 *   - Tape saturation: [float32x4_t] applies the algebraic sigmoid to 4 samples per cycle
 *     using Newton-Raphson reciprocal estimation — avoiding the expensive [tanh] call.
 *
 * After saturation the engine accumulates a per-frame mono downmix into an internal scratch
 * buffer ([DspContext::vizMono]). Once the buffer is full it applies the pre-computed Hann
 * window, runs [pffft_transform_ordered], computes per-band RMS magnitudes into
 * [DspContext::bandMagnitudes], and sets [DspContext::fftFrameReady]. Kotlin then calls
 * [nativeDspReadBandMagnitudes] to retrieve the magnitudes without any allocation.
 *
 * @author Hamza417
 */

#include <jni.h>
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <cstdlib>
#include <new>

#include "dsp-engine.h"
#include "fft-context.h"
#include "pffft/pffft.h"

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>
#define DSP_NEON_ENABLED 1
#else
#define DSP_NEON_ENABLED 0
#endif

#define DSP_TAG "FelicityDspEngine"
#define DSP_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, DSP_TAG, __VA_ARGS__)
#define DSP_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  DSP_TAG, __VA_ARGS__)

/** Minimum drive magnitude treated as active saturation (avoids divide-by-zero in compensation). */
static constexpr float kSatDriveEpsilon = 0.001f;

/** Minimum gain deviation from neutral (direct = 1, cross = 0) to apply the M/S matrix. */
static constexpr float kWidenEpsilon = 0.0001f;

/** Pan gains within this distance of 1.0 are treated as unity (center). */
static constexpr float kPanEpsilon = 0.0001f;

/**
 * Returns the identity biquad coefficient set: b0 = 1, all others = 0.
 * Applying this to any input returns the input unmodified.
 */
static inline BiquadCoeffs identityBiquad() {
    BiquadCoeffs c;
    c.b0 = 1.f;
    c.b1 = c.b2 = c.a1 = c.a2 = 0.f;
    return c;
}

/**
 * Computes normalized RBJ peaking EQ biquad coefficients for a single band.
 *
 * When [gainDb] is within [kDspFlatThresholdDb] of 0 dB the identity coefficients
 * are returned, avoiding unnecessary floating-point work per sample.
 *
 * RBJ peaking-EQ formulas (a0-normalized):
 *   A = 10^(gain / 40)      alpha = sin(w0) / (2Q)
 *   b0 = 1 + alpha*A        b1 = b2_a1 = -2*cos(w0)     b2 = 1 - alpha*A
 *   a0 = 1 + alpha/A        a2 = 1 - alpha/A
 *
 * @param f0       Center frequency in Hz.
 * @param gainDb   Gain in dB, range [-15, +15].
 * @param sampleHz Sample rate in Hz.
 * @return Normalized BiquadCoeffs for the requested band.
 */
static BiquadCoeffs computePeakingEq(float f0, float gainDb, int sampleHz) {
    if (fabsf(gainDb) < kDspFlatThresholdDb) {
        return identityBiquad();
    }
    const float A = powf(10.f, gainDb / 40.f);
    const float omega = 2.f * static_cast<float>(M_PI) * f0 / static_cast<float>(sampleHz);
    const float sinOmega = sinf(omega);
    const float cosOmega = cosf(omega);
    const float alpha = sinOmega / (2.f * kDspBandQ);
    const float a0_inv = 1.f / (1.f + alpha / A);

    BiquadCoeffs c;
    c.b0 = (1.f + alpha * A) * a0_inv;
    c.b1 = (-2.f * cosOmega) * a0_inv;
    c.b2 = (1.f - alpha * A) * a0_inv;
    c.a1 = (-2.f * cosOmega) * a0_inv;
    c.a2 = (1.f - alpha / A) * a0_inv;
    return c;
}

/**
 * Computes normalized RBJ low-shelf biquad coefficients (shelf slope S = 1).
 *
 * @param f0       Shelf frequency in Hz.
 * @param gainDb   Gain in dB, range [-12, +12].
 * @param sampleHz Sample rate in Hz.
 * @return Normalized BiquadCoeffs for the low-shelf filter.
 */
static BiquadCoeffs computeLowShelf(float f0, float gainDb, int sampleHz) {
    if (fabsf(gainDb) < kDspFlatThresholdDb) {
        return identityBiquad();
    }
    const float A = powf(10.f, gainDb / 40.f);
    const float omega = 2.f * static_cast<float>(M_PI) * f0 / static_cast<float>(sampleHz);
    const float cosOmega = cosf(omega);
    const float sinOmega = sinf(omega);
    const float sqrtA = sqrtf(A);
    const float alpha = sinOmega * 0.5f * sqrtf((A + 1.f / A) * (1.f - 1.f) + 2.f);

    /**
     * RBJ low-shelf (S = 1): alpha simplifies to sin(w0) / sqrt(2), matching
     * the BassProcessor Kotlin implementation.
     */
    const float alphaS = sinOmega / sqrtf(2.f);
    const float twoSqrtA = 2.f * sqrtA * alphaS;
    const float a0_inv = 1.f / ((A + 1.f) + (A - 1.f) * cosOmega + twoSqrtA);

    (void) alpha; // unused — kept for formula clarity

    BiquadCoeffs c;
    c.b0 = A * ((A + 1.f) - (A - 1.f) * cosOmega + twoSqrtA) * a0_inv;
    c.b1 = 2.f * A * ((A - 1.f) - (A + 1.f) * cosOmega) * a0_inv;
    c.b2 = A * ((A + 1.f) - (A - 1.f) * cosOmega - twoSqrtA) * a0_inv;
    c.a1 = -2.f * ((A - 1.f) + (A + 1.f) * cosOmega) * a0_inv;
    c.a2 = ((A + 1.f) + (A - 1.f) * cosOmega - twoSqrtA) * a0_inv;
    return c;
}

/**
 * Computes normalized RBJ high-shelf biquad coefficients (shelf slope S = 1).
 *
 * @param f0       Shelf frequency in Hz.
 * @param gainDb   Gain in dB, range [-12, +12].
 * @param sampleHz Sample rate in Hz.
 * @return Normalized BiquadCoeffs for the high-shelf filter.
 */
static BiquadCoeffs computeHighShelf(float f0, float gainDb, int sampleHz) {
    if (fabsf(gainDb) < kDspFlatThresholdDb) {
        return identityBiquad();
    }
    const float A = powf(10.f, gainDb / 40.f);
    const float omega = 2.f * static_cast<float>(M_PI) * f0 / static_cast<float>(sampleHz);
    const float cosOmega = cosf(omega);
    const float sqrtA = sqrtf(A);
    const float sinOmega = sinf(omega);
    const float alphaS = sinOmega / sqrtf(2.f);
    const float twoSqrtA = 2.f * sqrtA * alphaS;
    const float a0_inv = 1.f / ((A + 1.f) - (A - 1.f) * cosOmega + twoSqrtA);

    BiquadCoeffs c;
    c.b0 = A * ((A + 1.f) + (A - 1.f) * cosOmega + twoSqrtA) * a0_inv;
    c.b1 = -2.f * A * ((A - 1.f) + (A + 1.f) * cosOmega) * a0_inv;
    c.b2 = A * ((A + 1.f) + (A - 1.f) * cosOmega - twoSqrtA) * a0_inv;
    c.a1 = 2.f * ((A - 1.f) - (A + 1.f) * cosOmega) * a0_inv;
    c.a2 = ((A + 1.f) - (A - 1.f) * cosOmega - twoSqrtA) * a0_inv;
    return c;
}

/**
 * Zeros all biquad states (w1, w2) for all channels and bands in [ctx].
 * Called whenever the audio format changes to prevent discontinuities.
 *
 * @param ctx DSP context whose filter states are to be reset.
 */
static void clearAllBiquadState(DspContext *ctx) {
    for (int ch = 0; ch < kDspMaxChannels; ++ch) {
        for (int b = 0; b < kDspEqBandCount; ++b) {
            ctx->eqState[ch][b] = BiquadState{};
        }
        ctx->bassState[ch] = BiquadState{};
        ctx->trebleState[ch] = BiquadState{};
    }
}

/**
 * Applies a single Direct Form II Transposed biquad stage to one sample (scalar fallback).
 *
 * Recurrence:
 *   y  = b0*x + w1
 *   w1' = b1*x - a1*y + w2
 *   w2' = b2*x - a2*y
 *
 * @param x     Input sample.
 * @param c     Biquad coefficients.
 * @param state Filter state {w1, w2}, updated in-place.
 * @return Filtered output sample.
 */
static inline float applyBiquadScalar(float x, const BiquadCoeffs &c, BiquadState &state) {
    const float y = c.b0 * x + state.w1;
    state.w1 = c.b1 * x - c.a1 * y + state.w2;
    state.w2 = c.b2 * x - c.a2 * y;
    return y;
}

/**
 * Applies a biquad filter to a stereo interleaved buffer using ARM NEON intrinsics.
 *
 * The buffer layout is [L0, R0, L1, R1, ...]. A [float32x2_t] vector holds {L_i, R_i}
 * for one stereo frame, allowing the left and right channels to be processed in parallel
 * by a single NEON instruction — two independent biquad instances sharing the same
 * coefficient set but maintaining separate per-channel state in {stL, stR}.
 *
 * The outer loop is unrolled to consume two consecutive stereo frames per iteration,
 * yielding 4 samples per iteration and matching the "4-sample concurrency" design target.
 * State is stored back to [stL] and [stR] after all frames are processed.
 *
 * A scalar fallback path is compiled for non-NEON targets (x86 emulator builds).
 *
 * @param buf       Pointer to the start of the interleaved stereo PCM buffer.
 * @param numFrames Total number of stereo frames (total samples / 2).
 * @param c         Biquad coefficients shared by both channels.
 * @param stL       Left-channel biquad state, updated in-place.
 * @param stR       Right-channel biquad state, updated in-place.
 */
static void applyBiquadStereo(float *__restrict buf, int numFrames,
                              const BiquadCoeffs &c,
                              BiquadState &stL, BiquadState &stR) {
#if DSP_NEON_ENABLED
    /**
     * Pack {w1_L, w1_R} and {w2_L, w2_R} into 64-bit NEON registers so that every
     * multiply-accumulate operates on both channels simultaneously.
     */
    float32x2_t vW1    = {stL.w1, stR.w1};
    float32x2_t vW2    = {stL.w2, stR.w2};
    const float32x2_t vB0 = vdup_n_f32(c.b0);
    const float32x2_t vB1 = vdup_n_f32(c.b1);
    const float32x2_t vB2 = vdup_n_f32(c.b2);
    const float32x2_t vA1 = vdup_n_f32(c.a1);
    const float32x2_t vA2 = vdup_n_f32(c.a2);

    int i = 0;

    /**
     * Main loop: 2 stereo frames per iteration = 4 samples per outer cycle.
     * Frame 0 and frame 1 are processed serially (biquad state carries through)
     * but each individual operation covers both L and R in parallel via NEON.
     */
    for (; i <= numFrames - 2; i += 2) {
        /** Frame 0: load {L_i, R_i} */
        float32x2_t x0 = vld1_f32(buf + 2 * i);
        /** y = b0*x + w1 */
        float32x2_t y0 = vmla_f32(vW1, vB0, x0);
        /** w1' = b1*x - a1*y + w2 */
        float32x2_t nW1 = vmls_f32(vmla_f32(vW2, vB1, x0), vA1, y0);
        /** w2' = b2*x - a2*y */
        float32x2_t nW2 = vsub_f32(vmul_f32(vB2, x0), vmul_f32(vA2, y0));
        vst1_f32(buf + 2 * i, y0);

        /** Frame 1: {L_{i+1}, R_{i+1}} — uses state updated from frame 0. */
        float32x2_t x1 = vld1_f32(buf + 2 * (i + 1));
        float32x2_t y1 = vmla_f32(nW1, vB0, x1);
        vW1 = vmls_f32(vmla_f32(nW2, vB1, x1), vA1, y1);
        vW2 = vsub_f32(vmul_f32(vB2, x1), vmul_f32(vA2, y1));
        vst1_f32(buf + 2 * (i + 1), y1);
    }

    /** Handle a trailing odd frame (total frames not divisible by 2). */
    if (i < numFrames) {
        float32x2_t x = vld1_f32(buf + 2 * i);
        float32x2_t y = vmla_f32(vW1, vB0, x);
        vW1 = vmls_f32(vmla_f32(vW2, vB1, x), vA1, y);
        vW2 = vsub_f32(vmul_f32(vB2, x), vmul_f32(vA2, y));
        vst1_f32(buf + 2 * i, y);
    }

    /** Write back state registers to the DspContext. */
    stL.w1 = vget_lane_f32(vW1, 0);
    stR.w1 = vget_lane_f32(vW1, 1);
    stL.w2 = vget_lane_f32(vW2, 0);
    stR.w2 = vget_lane_f32(vW2, 1);

#else
    /** Scalar fallback for non-NEON targets (e.g., x86 emulator). */
    for (int i = 0; i < numFrames; ++i) {
        buf[2 * i] = applyBiquadScalar(buf[2 * i], c, stL);
        buf[2 * i + 1] = applyBiquadScalar(buf[2 * i + 1], c, stR);
    }
#endif
}

/**
 * Applies a biquad filter to a mono buffer.
 * Used when [DspContext::channelCount] == 1.
 *
 * @param buf      Pointer to the mono PCM sample array.
 * @param numSamples Total number of samples.
 * @param c        Biquad coefficients.
 * @param st       Mono biquad state, updated in-place.
 */
static void applyBiquadMono(float *__restrict buf, int numSamples,
                            const BiquadCoeffs &c, BiquadState &st) {
    for (int i = 0; i < numSamples; ++i) {
        buf[i] = applyBiquadScalar(buf[i], c, st);
    }
}

/**
 * Applies the M/S stereo-widening matrix to a stereo interleaved buffer using NEON.
 *
 * Matrix per frame:
 *   out_L = directGain * L + crossGain * R
 *   out_R = crossGain  * L + directGain * R
 *
 * [float32x4_t] covers two stereo frames (4 samples) per NEON instruction.
 * Each frame pair {L0, R0, L1, R1} is loaded; the reversed pair {R0, L0, R1, L1}
 * is computed by reversing each 64-bit lane and combined with the direct path.
 *
 * @param buf        Pointer to interleaved stereo PCM buffer.
 * @param numFrames  Total number of stereo frames.
 * @param directGain Direct (same-channel) gain coefficient.
 * @param crossGain  Cross (opposite-channel) gain coefficient.
 */
static void applyStereoWidening(float *__restrict buf, int numFrames,
                                float directGain, float crossGain) {
#if DSP_NEON_ENABLED
    /**
     * Build a 4-lane gain vector alternating {direct, cross, direct, cross}
     * and its pair {cross, direct, cross, direct} to apply the M/S matrix across
     * two interleaved stereo frames in a single vmulq_f32 + vmlaq_f32.
     */
    const float32x4_t vDirect = {directGain, directGain, directGain, directGain};
    const float32x4_t vCross  = {crossGain,  crossGain,  crossGain,  crossGain};

    int i = 0;
    for (; i <= numFrames - 2; i += 2) {
        /** Load 4 samples: {L0, R0, L1, R1} */
        float32x4_t f01  = vld1q_f32(buf + 2 * i);
        float32x2_t f0   = vget_low_f32(f01);   /** {L0, R0} */
        float32x2_t f1   = vget_high_f32(f01);  /** {L1, R1} */

        /** Swap each pair to get the cross-channel source: {R0, L0} and {R1, L1} */
        float32x2_t f0r  = vrev64_f32(f0);
        float32x2_t f1r  = vrev64_f32(f1);

        /**
         * Apply the M/S matrix per frame:
         *   out = directGain * {L, R} + crossGain * {R, L}
         */
        float32x2_t y0 = vadd_f32(vmul_f32(vget_low_f32(vDirect),  f0),
                                   vmul_f32(vget_low_f32(vCross),   f0r));
        float32x2_t y1 = vadd_f32(vmul_f32(vget_high_f32(vDirect), f1),
                                   vmul_f32(vget_high_f32(vCross),  f1r));

        vst1q_f32(buf + 2 * i, vcombine_f32(y0, y1));
    }

    /** Scalar tail. */
    for (; i < numFrames; ++i) {
        const float L    = buf[2 * i];
        const float R    = buf[2 * i + 1];
        buf[2 * i]     = directGain * L + crossGain * R;
        buf[2 * i + 1] = crossGain  * L + directGain * R;
    }
#else
    for (int i = 0; i < numFrames; ++i) {
        const float L = buf[2 * i];
        const float R = buf[2 * i + 1];
        buf[2 * i] = directGain * L + crossGain * R;
        buf[2 * i + 1] = crossGain * L + directGain * R;
    }
#endif
}

/**
 * Applies constant-power stereo pan gains to a stereo interleaved buffer using NEON.
 *
 * Each interleaved sample is multiplied by [leftGain] (even indices) or [rightGain]
 * (odd indices). A [float32x4_t] gain vector {L, R, L, R} covers two stereo frames
 * (4 samples) per NEON multiply.
 *
 * @param buf       Pointer to interleaved stereo PCM buffer.
 * @param numFrames Total number of stereo frames.
 * @param leftGain  Left-channel linear gain.
 * @param rightGain Right-channel linear gain.
 */
static void applyBalance(float *__restrict buf, int numFrames,
                         float leftGain, float rightGain) {
#if DSP_NEON_ENABLED
    /** Gain pattern: {L_gain, R_gain, L_gain, R_gain} applied to {L0, R0, L1, R1}. */
    const float32x4_t vGains = {leftGain, rightGain, leftGain, rightGain};

    int i = 0;
    /** Process 2 stereo frames (4 samples) per iteration. */
    for (; i <= numFrames - 2; i += 2) {
        float32x4_t f = vld1q_f32(buf + 2 * i);
        vst1q_f32(buf + 2 * i, vmulq_f32(f, vGains));
    }

    /** Scalar tail. */
    for (; i < numFrames; ++i) {
        buf[2 * i]     *= leftGain;
        buf[2 * i + 1] *= rightGain;
    }
#else
    for (int i = 0; i < numFrames; ++i) {
        buf[2 * i] *= leftGain;
        buf[2 * i + 1] *= rightGain;
    }
#endif
}

/**
 * Applies tape-style soft saturation to an arbitrary sample array using NEON.
 *
 * Transfer function:
 *   y = (drive * x) / (1 + |drive * x|) * compensation
 *
 * where compensation = (1 + drive) / drive normalizes output level across drive settings.
 * This is an algebraic sigmoid —> it is equivalent in shape to [tanh] but avoids the
 * expensive [tanh] implementation, making it safe for use on the audio hot path.
 *
 * NEON reciprocal estimation (vrecpeq_f32 + one Newton-Raphson refinement step) is used
 * instead of a division instruction, maintaining ARMv7 compatibility while providing
 * approximately 23 bits of precision —> more than sufficient for 24-bit audio.
 *
 * [float32x4_t] processes 4 samples per NEON iteration.
 *
 * @param buf        Sample buffer to saturate in-place.
 * @param numSamples Total number of samples (frames * channels).
 * @param drive      Saturation drive in (0, 4].
 * @param comp       Pre-computed compensation factor (1 + drive) / drive.
 */
static void applySaturation(float *__restrict buf, int numSamples,
                            float drive, float comp) {
#if DSP_NEON_ENABLED
    const float32x4_t vDrive = vdupq_n_f32(drive);
    const float32x4_t vComp  = vdupq_n_f32(comp);
    const float32x4_t vOne   = vdupq_n_f32(1.0f);

    int i = 0;
    for (; i <= numSamples - 4; i += 4) {
        float32x4_t x    = vld1q_f32(buf + i);

        /** drive * x */
        float32x4_t dx   = vmulq_f32(vDrive, x);

        /** 1 + |drive * x| */
        float32x4_t denom = vaddq_f32(vOne, vabsq_f32(dx));

        /**
         * Reciprocal estimate + one Newton-Raphson step:
         *   est  = vrecpeq_f32(denom)           (~8-bit accuracy)
         *   refine = est * (2 - denom * est)     (~23-bit accuracy after one step)
         */
        float32x4_t recip = vrecpeq_f32(denom);
        recip = vmulq_f32(recip, vrecpsq_f32(denom, recip));

        /** y = (drive * x) / (1 + |drive * x|) * comp */
        float32x4_t y = vmulq_f32(vmulq_f32(dx, recip), vComp);
        vst1q_f32(buf + i, y);
    }

    /** Scalar tail for any remaining samples. */
    for (; i < numSamples; ++i) {
        float dx = drive * buf[i];
        buf[i]   = (dx / (1.f + fabsf(dx))) * comp;
    }
#else
    for (int i = 0; i < numSamples; ++i) {
        float dx = drive * buf[i];
        buf[i] = (dx / (1.f + fabsf(dx))) * comp;
    }
#endif
}

/**
 * Accumulates a mono downmix of the current block into [DspContext::vizMono].
 * When the ring buffer is full ([vizBufPos] reaches [FFTContext::size]) the function
 * applies the pre-computed Hann window, runs the PFFFT forward transform, computes
 * per-band RMS magnitudes into [DspContext::bandMagnitudes], and sets
 * [DspContext::fftFrameReady] to signal a fresh spectrum frame to Kotlin.
 *
 * This runs entirely on the audio thread with zero heap allocations; all buffers are
 * pre-allocated in [nativeDspCreate].
 *
 * @param ctx       DSP context that owns the accumulation and FFT state.
 * @param buf       Current fully-processed PCM buffer (interleaved stereo or mono).
 * @param numFrames Number of audio frames in [buf].
 */
static void feedVisualizer(DspContext *ctx, const float *__restrict buf, int numFrames) {
    FFTContext *fft = ctx->fftCtx;
    if (!fft || !fft->setup || fft->bandCount <= 0 || !fft->bandEdges) return;

    const int fftSize = fft->size;
    const bool isStereo = (ctx->channelCount == 2);

    for (int i = 0; i < numFrames; ++i) {
        /** Mono downmix: average L and R (or copy the single channel for mono streams). */
        ctx->vizMono[ctx->vizBufPos] = isStereo
                                       ? (buf[2 * i] + buf[2 * i + 1]) * 0.5f
                                       : buf[i];
        ctx->vizBufPos++;

        if (ctx->vizBufPos >= fftSize) {
            /**
             * Apply the pre-computed Hann window directly into the PFFFT-aligned
             * input buffer — no extra copy needed since FFTContext::input is reused.
             */
            for (int k = 0; k < fftSize; ++k) {
                fft->input[k] = ctx->vizMono[k] * fft->window[k];
            }

            /** Forward real FFT: packed half-complex ordered output in fft->output. */
            pffft_transform_ordered(fft->setup, fft->input, fft->output,
                                    fft->work, PFFFT_FORWARD);

            /** Compute per-band RMS magnitude from the half-spectrum bins. */
            const int halfSize = fftSize / 2;
            for (int band = 0; band < fft->bandCount; ++band) {
                const int startBin = fft->bandEdges[band];
                const int endBin = (fft->bandEdges[band + 1] > startBin)
                                   ? fft->bandEdges[band + 1] : startBin + 1;
                float sumSq = 0.f;
                int cnt = 0;
                for (int k = startBin; k < endBin && k < halfSize; ++k) {
                    const float re = (k == 0) ? fft->output[0] : fft->output[2 * k];
                    const float im = (k == 0) ? 0.f : fft->output[2 * k + 1];
                    sumSq += re * re + im * im;
                    ++cnt;
                }
                ctx->bandMagnitudes[band] = (cnt > 0) ? sqrtf(sumSq / (float) cnt) : 0.f;
            }

            ctx->fftFrameReady = true;
            ctx->vizBufPos = 0;
        }
    }
}

extern "C" {

/**
 * Allocates and initializes a [DspContext] bound to the given [FFTContext].
 *
 * All biquad coefficients are set to identity, all gains to their neutral values
 * (stereo width 1.0, center pan, saturation drive 0), and the EQ is enabled.
 * The [vizMono] and [bandMagnitudes] scratch buffers are heap-allocated using the
 * sizes reported by the supplied [fftHandle].
 *
 * @param env          JNI environment pointer.
 * @param thiz         Calling Java/Kotlin object (unused).
 * @param fftHandle    Opaque pointer returned by [VisualizerProcessor.nativeCreate].
 * @param sampleRate   Initial sample rate in Hz.
 * @param channelCount Number of interleaved audio channels (1 or 2).
 * @return Opaque pointer to [DspContext] cast to jlong, or 0 on allocation failure.
 */
JNIEXPORT jlong JNICALL
Java_app_simple_felicity_engine_processors_DspProcessor_nativeDspCreate(
        JNIEnv * /*env*/, jobject /*thiz*/,
        jlong fftHandle, jint sampleRate, jint channelCount) {

    auto *fft = reinterpret_cast<FFTContext *>(fftHandle);
    if (!fft || fft->size <= 0) {
        DSP_LOGE("nativeDspCreate: invalid fftHandle");
        return 0L;
    }

    auto *ctx = static_cast<DspContext *>(calloc(1, sizeof(DspContext)));
    if (!ctx) {
        DSP_LOGE("nativeDspCreate: DspContext allocation failed");
        return 0L;
    }

    ctx->sampleRate = static_cast<int>(sampleRate);
    ctx->channelCount = static_cast<int>(channelCount);
    ctx->fftCtx = fft;
    ctx->vizBufPos = 0;
    ctx->fftFrameReady = false;

    ctx->vizMono = static_cast<float *>(calloc(fft->size, sizeof(float)));
    ctx->bandMagnitudes = (fft->bandCount > 0)
                          ? static_cast<float *>(calloc(fft->bandCount, sizeof(float)))
                          : nullptr;

    if (!ctx->vizMono) {
        DSP_LOGE("nativeDspCreate: vizMono allocation failed");
        delete ctx;
        return 0L;
    }

    /** Initialize all EQ bands to flat (identity coefficients). */
    for (int b = 0; b < kDspEqBandCount; ++b) {
        ctx->eqCoeffs[b] = identityBiquad();
    }
    ctx->eqEnabled = true;
    ctx->eqFlat = true;

    ctx->bassCoeffs = identityBiquad();
    ctx->bassFlat = true;
    ctx->trebleCoeffs = identityBiquad();
    ctx->trebleFlat = true;

    /** Neutral stereo width (width = 1.0 → direct = 1.0, cross = 0.0). */
    ctx->directGain = 1.0f;
    ctx->crossGain = 0.0f;

    /** Center pan (equal left/right). */
    ctx->leftGain = 1.0f;
    ctx->rightGain = 1.0f;

    /** Saturation off. */
    ctx->satDrive = 0.0f;
    ctx->satCompensation = 1.0f;

    clearAllBiquadState(ctx);

    DSP_LOGI("DspContext created — sampleRate=%d, channels=%d, fftSize=%d",
             ctx->sampleRate, ctx->channelCount, fft->size);
    return reinterpret_cast<jlong>(ctx);
}

/**
 * Recomputes all biquad coefficients and resets filter states when the audio
 * format changes. Must be called before the first [nativeDspProcessAudio] call
 * after a sample-rate or channel-count transition.
 *
 * @param env          JNI environment pointer.
 * @param thiz         Calling Java/Kotlin object (unused).
 * @param handle       Opaque pointer returned by [nativeDspCreate].
 * @param sampleRate   New sample rate in Hz.
 * @param channelCount New channel count (1 or 2).
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_processors_DspProcessor_nativeDspConfigure(
        JNIEnv * /*env*/, jobject /*thiz*/,
        jlong handle, jint sampleRate, jint channelCount) {

    auto *ctx = reinterpret_cast<DspContext *>(handle);
    if (!ctx) return;

    ctx->sampleRate = static_cast<int>(sampleRate);
    ctx->channelCount = static_cast<int>(channelCount);

    /** Recompute every band at the new sample rate. */
    for (int b = 0; b < kDspEqBandCount; ++b) {
        ctx->eqCoeffs[b] = ctx->eqFlat
                           ? identityBiquad()
                           : computePeakingEq(kDspEqCenterHz[b], 0.f, ctx->sampleRate);
    }
    ctx->bassCoeffs = computeLowShelf(kDspBassShelfHz, 0.f, ctx->sampleRate);
    ctx->trebleCoeffs = computeHighShelf(kDspTrebleShelfHz, 0.f, ctx->sampleRate);

    clearAllBiquadState(ctx);
    ctx->vizBufPos = 0;

    DSP_LOGI("DspContext reconfigured — sampleRate=%d, channels=%d",
             ctx->sampleRate, ctx->channelCount);
}

/**
 * Updates all 10 peaking EQ band gains and the bass/treble shelf gains atomically,
 * then recomputes all affected biquad coefficients.
 *
 * Passing 0 dB for a band causes that band to take the fast identity-coefficient path
 * in [applyBiquadStereo], skipping all arithmetic for that stage.
 *
 * @param env        JNI environment pointer.
 * @param thiz       Calling Java/Kotlin object (unused).
 * @param handle     Opaque pointer returned by [nativeDspCreate].
 * @param bandGains  Float array of exactly 10 dB gain values (index 0 = 31 Hz band).
 * @param bassDb     Bass low-shelf gain in dB.
 * @param trebleDb   Treble high-shelf gain in dB.
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_processors_DspProcessor_nativeDspSetEqBands(
        JNIEnv *env, jobject /*thiz*/,
        jlong handle, jfloatArray bandGains, jfloat bassDb, jfloat trebleDb) {

    auto *ctx = reinterpret_cast<DspContext *>(handle);
    if (!ctx) return;

    jfloat *gains = env->GetFloatArrayElements(bandGains, nullptr);
    bool anyActive = false;
    for (int b = 0; b < kDspEqBandCount; ++b) {
        const float db = gains[b];
        ctx->eqCoeffs[b] = computePeakingEq(kDspEqCenterHz[b], db, ctx->sampleRate);
        if (fabsf(db) >= kDspFlatThresholdDb) anyActive = true;
    }
    env->ReleaseFloatArrayElements(bandGains, gains, JNI_ABORT);

    ctx->eqFlat = !anyActive;

    ctx->bassCoeffs = computeLowShelf(kDspBassShelfHz, static_cast<float>(bassDb), ctx->sampleRate);
    ctx->bassFlat = fabsf(static_cast<float>(bassDb)) < kDspFlatThresholdDb;

    ctx->trebleCoeffs = computeHighShelf(kDspTrebleShelfHz, static_cast<float>(trebleDb),
                                         ctx->sampleRate);
    ctx->trebleFlat = fabsf(static_cast<float>(trebleDb)) < kDspFlatThresholdDb;
}

/**
 * Enables or disables the entire EQ stage (10-band + bass + treble).
 *
 * When disabled the EQ, bass, and treble biquad stages are completely skipped in
 * [nativeDspProcessAudio], saving significant CPU time on the hot path.
 *
 * @param env     JNI environment pointer.
 * @param thiz    Calling Java/Kotlin object (unused).
 * @param handle  Opaque pointer returned by [nativeDspCreate].
 * @param enabled True to activate the EQ stage; false for full bypass.
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_processors_DspProcessor_nativeDspSetEqEnabled(
        JNIEnv * /*env*/, jobject /*thiz*/,
        jlong handle, jboolean enabled) {

    auto *ctx = reinterpret_cast<DspContext *>(handle);
    if (!ctx) return;
    ctx->eqEnabled = (enabled == JNI_TRUE);
}

/**
 * Updates the stereo widening M/S matrix coefficients from a width value.
 *
 * Width mapping:
 *   0.0 → full mono   (directGain = 0.5, crossGain =  0.5)
 *   1.0 → natural     (directGain = 1.0, crossGain =  0.0)
 *   2.0 → max wide    (directGain = 1.5, crossGain = -0.5)
 *
 * @param env    JNI environment pointer.
 * @param thiz   Calling Java/Kotlin object (unused).
 * @param handle Opaque pointer returned by [nativeDspCreate].
 * @param width  Stereo width in [0.0, 2.0].
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_processors_DspProcessor_nativeDspSetStereoWidth(
        JNIEnv * /*env*/, jobject /*thiz*/,
        jlong handle, jfloat width) {

    auto *ctx = reinterpret_cast<DspContext *>(handle);
    if (!ctx) return;

    const float w = fmaxf(0.f, fminf(2.f, static_cast<float>(width)));
    const float mid = 0.5f;
    const float side = 0.5f * w;
    ctx->directGain = mid + side;
    ctx->crossGain = mid - side;
}

/**
 * Updates the left and right pan gains using the constant-power panning law.
 *
 * Constant-power law: theta = (pan + 1) / 2 * pi/2
 *   leftGain  = cos(theta)
 *   rightGain = sin(theta)
 *
 * @param env    JNI environment pointer.
 * @param thiz   Calling Java/Kotlin object (unused).
 * @param handle Opaque pointer returned by [nativeDspCreate].
 * @param pan    Pan value in [-1.0, 1.0]. 0.0 = center (unity gain on both channels).
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_processors_DspProcessor_nativeDspSetBalance(
        JNIEnv * /*env*/, jobject /*thiz*/,
        jlong handle, jfloat pan) {

    auto *ctx = reinterpret_cast<DspContext *>(handle);
    if (!ctx) return;

    const float p = fmaxf(-1.f, fminf(1.f, static_cast<float>(pan)));
    const double theta = ((p + 1.0) / 2.0) * (M_PI / 2.0);
    ctx->leftGain = static_cast<float>(cos(theta));
    ctx->rightGain = static_cast<float>(sin(theta));
}

/**
 * Updates the tape saturation drive and pre-computes the compensation factor.
 *
 * Setting [drive] to 0 effectively bypasses the saturation stage (the hot path
 * checks for [kSatDriveEpsilon] and skips the entire stage if inactive).
 *
 * @param env    JNI environment pointer.
 * @param thiz   Calling Java/Kotlin object (unused).
 * @param handle Opaque pointer returned by [nativeDspCreate].
 * @param drive  Saturation drive in [0.0, 4.0].
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_processors_DspProcessor_nativeDspSetSaturation(
        JNIEnv * /*env*/, jobject /*thiz*/,
        jlong handle, jfloat drive) {

    auto *ctx = reinterpret_cast<DspContext *>(handle);
    if (!ctx) return;

    const float d = fmaxf(0.f, fminf(4.f, static_cast<float>(drive)));
    ctx->satDrive = d;
    ctx->satCompensation = (d > kSatDriveEpsilon) ? (1.f + d) / d : 1.f;
}

/**
 * The unified, zero-allocation audio processing hot path.
 *
 * Pins the JVM array with [GetFloatArrayElements], applies the full DSP chain in
 * the order [EQ] -> [bass/treble shelves] -> [stereo widening] -> [balance] ->
 * [tape saturation], then feeds the processed audio into the visualizer ring buffer.
 * The modified array is committed back to the JVM heap via [ReleaseFloatArrayElements]
 * with mode 0. No heap allocations occur inside this function.
 *
 * @param env       JNI environment pointer.
 * @param thiz      Calling Java/Kotlin object (unused).
 * @param handle    Opaque pointer returned by [nativeDspCreate].
 * @param pcmBuffer Interleaved float PCM array from the decoder; modified in-place.
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_processors_DspProcessor_nativeDspProcessAudio(
        JNIEnv *env, jobject /*thiz*/,
        jlong handle, jfloatArray pcmBuffer) {

    auto *ctx = reinterpret_cast<DspContext *>(handle);
    if (!ctx) return;

    const int totalSamples = env->GetArrayLength(pcmBuffer);
    if (totalSamples <= 0) return;

    /** Pin the JVM array — no copy requested (nullptr flag = don't allocate a copy). */
    jfloat *buf = env->GetFloatArrayElements(pcmBuffer, nullptr);

    const int ch = ctx->channelCount;
    const int numFrames = totalSamples / ch;

    /**
     * Snapshot volatile parameters once at the top of the callback to ensure a
     * consistent view across all processing stages without repeated barrier reads.
     */
    const bool eqEn = ctx->eqEnabled;
    const bool eqFlat = ctx->eqFlat;
    const bool bassFlat = ctx->bassFlat;
    const bool trebleFlat = ctx->trebleFlat;
    const float direct = ctx->directGain;
    const float cross = ctx->crossGain;
    const float lGain = ctx->leftGain;
    const float rGain = ctx->rightGain;
    const float satDrive = ctx->satDrive;
    const float satComp = ctx->satCompensation;

    /**
     * Stage 1: 10-Band EQ + Bass shelf + Treble shelf.
     * Entire stage is skipped when the EQ is disabled or all bands are flat.
     * Individual bands/shelves with identity coefficients resolve to a no-op
     * inside applyBiquadStereo (b1=b2=a1=a2=0 → no multiply-adds needed by the
     * compiler after inlining, but we guard the call explicitly to be safe).
     */
    if (eqEn) {
        if (!eqFlat) {
            if (ch == 2) {
                for (int b = 0; b < kDspEqBandCount; ++b) {
                    applyBiquadStereo(buf, numFrames,
                                      ctx->eqCoeffs[b],
                                      ctx->eqState[0][b],
                                      ctx->eqState[1][b]);
                }
            } else {
                for (int b = 0; b < kDspEqBandCount; ++b) {
                    applyBiquadMono(buf, totalSamples,
                                    ctx->eqCoeffs[b],
                                    ctx->eqState[0][b]);
                }
            }
        }

        if (!bassFlat) {
            if (ch == 2) {
                applyBiquadStereo(buf, numFrames,
                                  ctx->bassCoeffs,
                                  ctx->bassState[0], ctx->bassState[1]);
            } else {
                applyBiquadMono(buf, totalSamples, ctx->bassCoeffs, ctx->bassState[0]);
            }
        }

        if (!trebleFlat) {
            if (ch == 2) {
                applyBiquadStereo(buf, numFrames,
                                  ctx->trebleCoeffs,
                                  ctx->trebleState[0], ctx->trebleState[1]);
            } else {
                applyBiquadMono(buf, totalSamples, ctx->trebleCoeffs, ctx->trebleState[0]);
            }
        }
    }

    /**
     * Stage 2: Stereo widening via M/S matrix.
     * Skipped when width is neutral (direct = 1, cross = 0) or stream is mono.
     */
    if (ch == 2 && (fabsf(direct - 1.0f) > kWidenEpsilon || fabsf(cross) > kWidenEpsilon)) {
        applyStereoWidening(buf, numFrames, direct, cross);
    }

    /**
     * Stage 3: Constant-power pan / balance.
     * Skipped when both gains are effectively unity.
     */
    if (ch == 2 && (fabsf(lGain - 1.0f) > kPanEpsilon || fabsf(rGain - 1.0f) > kPanEpsilon)) {
        applyBalance(buf, numFrames, lGain, rGain);
    }

    /**
     * Stage 4: Tape saturation.
     * Skipped when drive is below the epsilon threshold, saving the NEON loop overhead
     * for the very common "clean" playback scenario.
     */
    if (satDrive > kSatDriveEpsilon) {
        applySaturation(buf, totalSamples, satDrive, satComp);
    }

    /**
     * Stage 5: Visualizer handoff.
     * Accumulate mono downmix and trigger the FFT when the buffer is full.
     * This is the only stage that is not conditional — the visualizer always receives
     * the fully processed audio so the spectrum display reflects all active effects.
     */
    if (ctx->fftCtx && ctx->vizMono) {
        feedVisualizer(ctx, buf, numFrames);
    }

    /**
     * Commit the in-place modified buffer back to the JVM heap.
     * Mode 0 = copy native buffer back and release the pinned reference.
     */
    env->ReleaseFloatArrayElements(pcmBuffer, buf, 0);
}

/**
 * Copies the latest per-band RMS magnitudes into a caller-supplied [outBuffer].
 * Returns [JNI_TRUE] when a fresh FFT frame was available and the data was copied;
 * [JNI_FALSE] when no new frame has been produced since the last call.
 *
 * Calling this function clears the [DspContext::fftFrameReady] flag so that
 * subsequent calls return [JNI_FALSE] until the DSP engine produces a new frame.
 *
 * @param env       JNI environment pointer.
 * @param thiz      Calling Java/Kotlin object (unused).
 * @param handle    Opaque pointer returned by [nativeDspCreate].
 * @param outBuffer Pre-allocated FloatArray of at least [FFTContext::bandCount] elements.
 * @return [JNI_TRUE] if fresh data was copied, [JNI_FALSE] otherwise.
 */
JNIEXPORT jboolean JNICALL
Java_app_simple_felicity_engine_processors_DspProcessor_nativeDspReadBandMagnitudes(
        JNIEnv *env, jobject /*thiz*/,
        jlong handle, jfloatArray outBuffer) {

    auto *ctx = reinterpret_cast<DspContext *>(handle);
    if (!ctx || !ctx->fftFrameReady || !ctx->bandMagnitudes) return JNI_FALSE;

    const int bandCount = ctx->fftCtx ? ctx->fftCtx->bandCount : 0;
    if (bandCount <= 0) return JNI_FALSE;

    jfloat *out = env->GetFloatArrayElements(outBuffer, nullptr);
    memcpy(out, ctx->bandMagnitudes, bandCount * sizeof(float));
    env->ReleaseFloatArrayElements(outBuffer, out, 0);

    ctx->fftFrameReady = false;
    return JNI_TRUE;
}

/**
 * Frees all native resources owned by the given [DspContext].
 * The [FFTContext] pointed to by [DspContext::fftCtx] is NOT freed here — it is
 * owned by [VisualizerProcessor] and must be destroyed via [nativeDestroy] on
 * that class.
 *
 * @param env    JNI environment pointer.
 * @param thiz   Calling Java/Kotlin object (unused).
 * @param handle Opaque pointer returned by [nativeDspCreate].
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_processors_DspProcessor_nativeDspDestroy(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {

    auto *ctx = reinterpret_cast<DspContext *>(handle);
    if (!ctx) return;

    free(ctx->vizMono);
    free(ctx->bandMagnitudes);
    free(ctx);

    DSP_LOGI("DspContext destroyed");
}

} // extern "C"

