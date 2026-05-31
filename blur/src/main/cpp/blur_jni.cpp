#include "gaussian_blur.h"

#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cstring>

#define TAG  "GPUBlur"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/**
 * JNI entry point called from GPUBlur.kt.
 *
 * Locks the source bitmap, runs the GPU blur pipeline, and returns a new
 * Bitmap containing the blurred pixels. If the pipeline fails for any reason
 * (no EGL, out of GPU memory, etc.) the original bitmap is returned unchanged
 * so the caller always gets a valid result.
 *
 * @param bitmap  The source Android Bitmap (must be ARGB_8888 / RGBA_8888).
 * @param radius  Blur radius in pixels.
 * @return        A new Bitmap with blurred pixels, or the original on failure.
 */
extern "C" JNIEXPORT jobject JNICALL
Java_app_simple_felicity_blur_GPUBlur_nativeBlur(
        JNIEnv *env,
        jobject  /* thiz */,
        jobject bitmap,
        jfloat radius) {

    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("AndroidBitmap_getInfo failed");
        return bitmap;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Unsupported bitmap format %d (only RGBA_8888 is supported)", info.format);
        return bitmap;
    }

    void *srcPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &srcPixels) < 0) {
        LOGE("AndroidBitmap_lockPixels failed");
        return bitmap;
    }

    uint8_t *blurred = gaussianBlurGpu(
            static_cast<const uint8_t *>(srcPixels),
            static_cast<int>(info.width),
            static_cast<int>(info.height),
            static_cast<float>(radius));

    AndroidBitmap_unlockPixels(env, bitmap);

    if (!blurred) {
        LOGE("GPU blur returned null — returning original bitmap");
        return bitmap;
    }

    // Create a new Bitmap to hold the blurred pixels. We call the Java-side
    // Bitmap.createBitmap(int, int, Config) factory because there is no NDK
    // API for allocating a fresh Bitmap from C++.
    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jclass configClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888Id = env->GetStaticFieldID(configClass, "ARGB_8888",
                                                "Landroid/graphics/Bitmap$Config;");
    jobject argb8888 = env->GetStaticObjectField(configClass, argb8888Id);
    jmethodID createId = env->GetStaticMethodID(bitmapClass, "createBitmap",
                                                "(IILandroid/graphics/Bitmap$Config;)"
                                                "Landroid/graphics/Bitmap;");

    jobject resultBitmap = env->CallStaticObjectMethod(
            bitmapClass, createId,
            static_cast<jint>(info.width),
            static_cast<jint>(info.height),
            argb8888);

    if (!resultBitmap) {
        LOGE("Bitmap.createBitmap returned null");
        delete[] blurred;
        return bitmap;
    }

    void *dstPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, resultBitmap, &dstPixels) < 0) {
        LOGE("AndroidBitmap_lockPixels on result bitmap failed");
        delete[] blurred;
        return bitmap;
    }

    std::memcpy(dstPixels, blurred,
                static_cast<size_t>(info.width) * info.height * 4);

    AndroidBitmap_unlockPixels(env, resultBitmap);
    delete[] blurred;

    return resultBitmap;
}

