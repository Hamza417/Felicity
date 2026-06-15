/**
 * @file fft-context.h
 * @brief Shared definition of [FFTContext] used by both the visualizer JNI bridge
 * and the native DSP engine.
 *
 * Separating this struct into its own header allows [dsp-engine.cpp] to reference
 * the FFT context directly — writing windowed mono samples into [FFTContext::input]
 * and triggering the transform — without duplicating the struct or introducing a
 * circular include between the two translation units.
 *
 * All float buffers that PFFFT touches must remain SIMD-aligned (16-byte); they are
 * managed exclusively by [nativeCreate] / [nativeDestroy] in visualizer-fft.cpp using
 * [pffft_aligned_malloc] and [pffft_aligned_free].
 *
 * @author Hamza417
 */

#pragma once

#include "pffft/pffft.h"

/**
 * Aggregates all PFFFT state for a single real-valued FFT plan plus the
 * frequency-band mapping needed by the visualizer and the DSP engine.
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
    float *window;    ///< Pre-computed Hann window w[i] = 0.5*(1-cos(2π*i/(N-1))).
    int size;      ///< FFT size N; must be a power of two and >= 32.
    int bandCount; ///< Number of frequency bands; set by nativeSetBandEdges.
    int *bandEdges; ///< Band boundary bin indices, length = bandCount + 1; nullptr until set.
};

