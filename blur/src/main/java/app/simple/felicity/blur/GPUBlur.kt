package app.simple.felicity.blur

import android.graphics.Bitmap

/**
 * GPU-accelerated Gaussian blur backed by a native OpenGL ES 3.0 pipeline.
 *
 * Each call spins up a temporary EGL offscreen context, runs a two-pass
 * separable Gaussian shader on the GPU, reads the result back, and tears
 * the context down. This avoids any max-radius limitation — radii larger
 * than the shader kernel can handle in one pass are dealt with by
 * downscaling the image first, blurring, then upscaling the result.
 *
 * @author Hamza417
 */
object GPUBlur {

    init {
        System.loadLibrary("felicity_gpu_blur")
    }

    /**
     * Returns a new bitmap that is a Gaussian-blurred copy of [bitmap].
     *
     * If the GPU pipeline fails for any reason (missing EGL support, out of
     * GPU memory, etc.) the original bitmap is returned unchanged so the
     * caller always gets a valid result back.
     *
     * @param bitmap  Source bitmap. Must be in ARGB_8888 format.
     * @param radius  Blur radius in pixels. Values below 1 are clamped to 1.
     */
    fun blur(bitmap: Bitmap, radius: Float): Bitmap {
        return nativeBlur(bitmap, radius.coerceAtLeast(1f))
    }

    private external fun nativeBlur(bitmap: Bitmap, radius: Float): Bitmap
}

