/**
 * @file dsp-engine.h
 * @brief Public interface and aggregate state for the Felicity native DSP processing engine.
 *
 * Defines [DspContext], which owns all biquad filter state, effect parameters, and the
 * mono downmix scratch buffer that feeds the visualizer FFT.
 *
 * The full DSP chain applied by [nativeProcessAudio] is, in order:
 *   1. 10-band peaking EQ (ISO standard center frequencies, RBJ biquad)
 *   2. Bass low-shelf filter (250 Hz, S = 1)
 *   3. Treble high-shelf filter (4000 Hz, S = 1)
 *   4. Stereo widening via M/S matrix
 *   5. Constant-power pan / balance
 *   6. Tape-style soft saturation via algebraic sigmoid
 *   7. Mono downmix accumulation → visualizer FFT trigger
 *
 * @author Hamza417
 */

#pragma once

#include <cstddef>
#include "fft-context.h"

/** Number of ISO 10-band graphic equalizer bands. */
static constexpr int kDspEqBandCount = 10;

/** Maximum number of audio channels supported by the engine. */
static constexpr int kDspMaxChannels = 2;

/** Q factor (1-octave bandwidth, sqrt(2)) used for all peaking EQ bands. */
static constexpr float kDspBandQ = 1.4142135f;

/** Threshold below which a gain value is treated as 0 dB flat, skipping the biquad math. */
static constexpr float kDspFlatThresholdDb = 0.001f;

/** Shelf frequency for the bass low-shelf filter in Hz. */
static constexpr float kDspBassShelfHz = 250.0f;

/** Shelf frequency for the treble high-shelf filter in Hz. */
static constexpr float kDspTrebleShelfHz = 4000.0f;

/**
 * ISO 10-band graphic EQ center frequencies in Hz (1-octave spacing,
 * 31 Hz through 16 kHz).
 */
static constexpr float kDspEqCenterHz[kDspEqBandCount] = {
        31.f, 62.f, 125.f, 250.f, 500.f,
        1000.f, 2000.f, 4000.f, 8000.f, 16000.f
};

/**
 * Normalized second-order IIR biquad coefficients (a0 = 1).
 * Layout: b0, b1, b2, a1, a2 (Direct Form II Transposed convention).
 */
struct BiquadCoeffs {
    float b0 = 1.f; ///< Feed-forward coefficient for x[n].
    float b1 = 0.f; ///< Feed-forward coefficient for x[n-1].
    float b2 = 0.f; ///< Feed-forward coefficient for x[n-2].
    float a1 = 0.f; ///< Feedback coefficient for y[n-1] (sign-positive convention).
    float a2 = 0.f; ///< Feedback coefficient for y[n-2].
};

/**
 * Direct Form II Transposed biquad state for a single channel.
 * Holds the two delay-line values {w1, w2}.
 */
struct BiquadState {
    float w1 = 0.f; ///< First delay register.
    float w2 = 0.f; ///< Second delay register.
};

/**
 * Aggregate state for the unified native DSP processing chain.
 *
 * One [DspContext] is allocated per [nativeDspCreate] call and freed by [nativeDspDestroy].
 * All mutable effect parameters are plain floats intended to be written from the Kotlin
 * control thread via dedicated JNI setters and read from the audio thread in
 * [nativeProcessAudio]. The caller is responsible for ensuring that setter calls are
 * not concurrent with the audio callback (the Media3 / AudioTrack pipeline guarantees
 * this implicitly through its threading model).
 *
 * [fftCtx] is a non-owning pointer to the [FFTContext] that was created by
 * [VisualizerProcessor.nativeCreate]. The DSP engine accumulates a mono downmix of the
 * fully processed audio into [vizMono]; once [vizBufPos] reaches [FFTContext::size] the
 * engine applies the pre-computed Hann window, runs the PFFFT forward transform, computes
 * per-band RMS magnitudes, and stores them in [bandMagnitudes]. Kotlin can then call
 * [nativeDspReadBandMagnitudes] to atomically copy those magnitudes into its own buffer.
 *
 * @author Hamza417
 */
struct DspContext {

    /** Biquad coefficients for each of the 10 peaking EQ bands. */
    BiquadCoeffs eqCoeffs[kDspEqBandCount];

    /**
     * Per-channel per-band biquad state.
     * Indexing: eqState[channel][band] — channel 0 = left/mono, channel 1 = right.
     */
    BiquadState eqState[kDspMaxChannels][kDspEqBandCount];

    /** When false the entire EQ + bass + treble stage is skipped without touching samples. */
    bool eqEnabled;

    /** True when every EQ band gain is within +/-[kDspFlatThresholdDb] of 0 dB. */
    bool eqFlat;

    /** Biquad coefficients for the bass low-shelf filter (250 Hz). */
    BiquadCoeffs bassCoeffs;

    /** Per-channel biquad state for the bass filter. */
    BiquadState bassState[kDspMaxChannels];

    /** True when the bass gain is effectively 0 dB. */
    bool bassFlat;

    /** Biquad coefficients for the treble high-shelf filter (4000 Hz). */
    BiquadCoeffs trebleCoeffs;

    /** Per-channel biquad state for the treble filter. */
    BiquadState trebleState[kDspMaxChannels];

    /** True when the treble gain is effectively 0 dB. */
    bool trebleFlat;

    /**
     * Direct gain component of the M/S stereo-widening matrix: 0.5 + 0.5 * width.
     * Range [0.5, 1.5]; default 1.0 (neutral / passthrough).
     */
    float directGain;

    /**
     * Cross gain component of the M/S stereo-widening matrix: 0.5 - 0.5 * width.
     * Range [-0.5, 0.5]; negative values widen beyond natural stereo.
     * Default 0.0 (neutral / passthrough).
     */
    float crossGain;

    /** Left-channel linear gain from the constant-power pan law. Default 1.0 (center). */
    float leftGain;

    /** Right-channel linear gain from the constant-power pan law. Default 1.0 (center). */
    float rightGain;

    /**
     * Tape saturation drive in [0.0, 4.0].
     * 0.0 = bypass (no processing); 4.0 = heavy saturation.
     */
    float satDrive;

    /**
     * Pre-computed gain-compensation factor for saturation: (1 + drive) / drive.
     * Keeps the perceived loudness roughly constant across all drive settings.
     */
    float satCompensation;

    /**
     * Non-owning pointer to the [FFTContext] shared with [VisualizerProcessor].
     * The DSP engine writes windowed mono samples to [FFTContext::input] and
     * calls [pffft_transform_ordered] directly — no additional copies needed.
     */
    FFTContext *fftCtx;

    /**
     * Scratch buffer for the mono downmix accumulation, length = [FFTContext::size].
     * Allocated on the heap by [nativeDspCreate].
     */
    float *vizMono;

    /** Current write position in [vizMono]. Resets to 0 after each completed FFT frame. */
    int vizBufPos;

    /**
     * Heap buffer of length [FFTContext::bandCount] for per-band RMS magnitudes.
     * Written by the DSP engine after each completed FFT frame; read by Kotlin via
     * [nativeDspReadBandMagnitudes].
     */
    float *bandMagnitudes;

    /** Set to true after each completed FFT frame; cleared by [nativeDspReadBandMagnitudes]. */
    bool fftFrameReady;

    /** Sample rate of the current audio format in Hz. */
    int sampleRate;

    /** Number of interleaved audio channels (1 or 2). */
    int channelCount;
};

