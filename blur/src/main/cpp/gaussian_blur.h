#pragma once

#include <cstdint>

/**
 * Blurs the given raw RGBA pixel data using a two-pass separable Gaussian
 * filter that runs entirely on the GPU through an OpenGL ES 3.0 pipeline.
 *
 * Large radii are handled transparently: the image is downscaled so the
 * shader kernel fits within the hardware's efficient range, blurred, then
 * upscaled back — so there is no practical upper limit on the blur radius.
 *
 * @param pixels  Pointer to RGBA_8888 pixel data (row-major, top-to-bottom).
 * @param width   Image width in pixels.
 * @param height  Image height in pixels.
 * @param radius  Gaussian blur radius in pixels (>= 1).
 * @return        A newly allocated buffer of the same size containing the
 *                blurred RGBA_8888 pixels, or nullptr if the GPU pipeline
 *                could not be set up. The caller owns the buffer and must
 *                delete[] it.
 */
uint8_t *gaussianBlurGpu(const uint8_t *pixels, int width, int height, float radius);

