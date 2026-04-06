/**
 * @file milkdrop-bridge.cpp
 * @brief JNI bridge between the Kotlin [ProjectMBridge] class and the native projectM library.
 *
 * Wraps the projectM 4.x C API so that the visualizer instance lifecycle (create, feed
 * PCM audio, render frames, destroy) can be driven entirely from Kotlin.  An opaque
 * [projectm_handle] is stored as a [jlong] on the Kotlin side; all native functions
 * accept that handle so that multiple independent renderer instances can coexist.
 *
 * The OpenGL context MUST be current on the calling thread before invoking
 * [nativeRenderFrame] or [nativeCreate].  Frame rendering targets whatever
 * framebuffer is currently bound on entry, making it compatible with both
 * [android.opengl.GLSurfaceView] and a custom EGL surface.
 *
 * @author Hamza417
 */

#include <jni.h>
#include <android/log.h>
#include <cstdint>
#include <cstring>

// Include the projectM C API.  The exact header path depends on the projectM
// version; both known layouts are tried via the include paths added in CMakeLists.txt.
#if __has_include(<projectM-4/projectM.h>)

#include <projectM-4/projectM.h>
#include <projectM-4/audio.h>
#include <projectM-4/render_opengl.h>

#elif __has_include("api/include/projectM-4/projectM.h")
#include "api/include/projectM-4/projectM.h"
#include "api/include/projectM-4/audio.h"
#include "api/include/projectM-4/render_opengl.h"
#else
// Minimal forward declarations so this translation unit still compiles
// even if the header search paths are not yet fully resolved.
typedef void* projectm_handle;
typedef enum { PROJECTM_MONO = 1, PROJECTM_STEREO = 2 } projectm_channels;
extern "C" {
projectm_handle projectm_create(void);
void            projectm_destroy(projectm_handle instance);
void            projectm_set_window_size(projectm_handle instance, size_t width, size_t height);
void            projectm_opengl_render_frame(projectm_handle instance);
void            projectm_pcm_add_float(projectm_handle instance,
                                       const float*     samples,
                                       unsigned int     count,
                                       projectm_channels channels);
}
#endif

#define BRIDGE_TAG "MilkdropBridge"
#define BRIDGE_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  BRIDGE_TAG, __VA_ARGS__)
#define BRIDGE_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, BRIDGE_TAG, __VA_ARGS__)

/**
 * Minimal blank Milkdrop preset used to suppress the projectM built-in idle preset
 * (the animated "M" logo with headphones) that is loaded automatically on
 * [projectm_create].  The preset renders a plain black frame with full decay so
 * that no residual image lingers before the app loads its own first preset.
 *
 * This string is fed to [projectm_load_preset_data] immediately after instance
 * creation with smooth_transition=false, atomically replacing the idle preset
 * before the very first [projectm_opengl_render_frame] call.
 */
static const char *const BLANK_PRESET =
        "[preset00]\n"
        "fDecay=1.000000\n"
        "fGammaAdj=1.000000\n"
        "zoom=1.000000\n"
        "warp=0.000000\n"
        "rot=0.000000\n"
        "dx=0.000000\n"
        "dy=0.000000\n";

extern "C" {

/**
 * Creates a new projectM instance and returns an opaque handle.
 *
 * The caller is responsible for making an OpenGL ES context current on this
 * thread before calling this function.  The returned handle must eventually
 * be passed to [nativeDestroy] to release native resources.
 *
 * The built-in projectM idle preset (animated "M" logo) is immediately replaced
 * with a blank preset so the first visible frame is always black rather than the
 * default splash animation.  The caller should load its own first preset shortly
 * after this call via [nativeLoadPresetData].
 *
 * @param env       JNI environment pointer.
 * @param thiz      Calling Kotlin object (unused).
 * @param width     Initial viewport width in pixels.
 * @param height    Initial viewport height in pixels.
 * @return          Opaque handle cast to [jlong], or 0 on failure.
 */
JNIEXPORT jlong JNICALL
Java_app_simple_felicity_milkdrop_bridges_ProjectMBridge_nativeCreate(
        JNIEnv * /*env*/, jobject /*thiz*/, jint width, jint height) {

    projectm_handle handle = projectm_create();
    if (!handle) {
        BRIDGE_LOGE("projectm_create() returned null");
        return 0L;
    }

    projectm_set_window_size(handle,
                             static_cast<size_t>(width),
                             static_cast<size_t>(height));

    // Immediately displace the built-in idle preset so the first rendered frame
    // is a blank black screen rather than the projectM "M" logo animation.
    // smooth_transition=false ensures the switch is instantaneous.
    projectm_load_preset_data(handle, BLANK_PRESET, false);
    BRIDGE_LOGI("Idle preset suppressed — blank preset loaded");

    BRIDGE_LOGI("projectM instance created: %p  size=%dx%d",
                handle, static_cast<int>(width), static_cast<int>(height));

    return reinterpret_cast<jlong>(handle);
}

/**
 * Notifies projectM that the surface was resized.
 *
 * @param env       JNI environment pointer.
 * @param thiz      Calling Kotlin object (unused).
 * @param nativePtr Opaque handle returned by [nativeCreate].
 * @param width     New viewport width in pixels.
 * @param height    New viewport height in pixels.
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_milkdrop_bridges_ProjectMBridge_nativeSurfaceChanged(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong nativePtr, jint width, jint height) {

    if (!nativePtr) return;
    auto *handle = reinterpret_cast<projectm_handle>(nativePtr);
    projectm_set_window_size(handle,
                             static_cast<size_t>(width),
                             static_cast<size_t>(height));
}

/**
 * Feeds PCM float samples to the projectM audio pipeline.
 *
 * This function is safe to call from any thread; projectM queues the data
 * internally.  Samples should be in the range [-1.0, 1.0].
 *
 * @param env       JNI environment pointer.
 * @param thiz      Calling Kotlin object (unused).
 * @param nativePtr Opaque handle returned by [nativeCreate].
 * @param samples   Float array of PCM samples.
 * @param count     Number of frames in [samples].
 * @param stereo    Pass JNI_TRUE for interleaved stereo, JNI_FALSE for mono.
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_milkdrop_bridges_ProjectMBridge_nativeAddPcmData(
        JNIEnv *env, jobject /*thiz*/, jlong nativePtr, jfloatArray samples,
        jint count, jboolean stereo) {

    if (!nativePtr || !samples) return;
    auto *handle = reinterpret_cast<projectm_handle>(nativePtr);

    jfloat *pcm = env->GetFloatArrayElements(samples, nullptr);
    if (pcm) {
        const projectm_channels channels = stereo ? PROJECTM_STEREO : PROJECTM_MONO;
        projectm_pcm_add_float(handle, pcm, static_cast<uint32_t>(count), channels);
        env->ReleaseFloatArrayElements(samples, pcm, JNI_ABORT);
    }
}

/**
 * Renders the next visualization frame into the currently bound framebuffer.
 *
 * Must be called from the GL thread with the OpenGL ES context current.
 *
 * @param env       JNI environment pointer.
 * @param thiz      Calling Kotlin object (unused).
 * @param nativePtr Opaque handle returned by [nativeCreate].
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_milkdrop_bridges_ProjectMBridge_nativeRenderFrame(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong nativePtr) {

    if (!nativePtr) return;
    auto *handle = reinterpret_cast<projectm_handle>(nativePtr);
    projectm_opengl_render_frame(handle);
}

/**
 * Loads a Milkdrop preset from its raw text content.
 *
 * Must be called on the GL thread (via [GLSurfaceView.queueEvent]) while the
 * OpenGL ES context is current.  projectM will begin transitioning to the new
 * preset immediately.
 *
 * @param env        JNI environment pointer.
 * @param thiz       Calling Kotlin object (unused).
 * @param nativePtr  Opaque handle returned by [nativeCreate].
 * @param data       Full text content of the `.milk` preset file.
 * @param smooth     When true, projectM cross-fades into the new preset.
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_milkdrop_bridges_ProjectMBridge_nativeLoadPresetData(
        JNIEnv *env, jobject /*thiz*/, jlong nativePtr, jstring data, jboolean smooth) {

    if (!nativePtr || !data) return;
    auto *handle = reinterpret_cast<projectm_handle>(nativePtr);

    const char *presetText = env->GetStringUTFChars(data, nullptr);
    if (presetText) {
        projectm_load_preset_data(handle, presetText, static_cast<bool>(smooth));
        env->ReleaseStringUTFChars(data, presetText);
        BRIDGE_LOGI("Preset data loaded (smooth=%d)", static_cast<int>(smooth));
    }
}

/**
 * Destroys the projectM instance and frees all associated native resources.
 *
 * After this call [nativePtr] is invalid and must not be used again.
 *
 * @param env       JNI environment pointer.
 * @param thiz      Calling Kotlin object (unused).
 * @param nativePtr Opaque handle returned by [nativeCreate].
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_milkdrop_bridges_ProjectMBridge_nativeDestroy(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong nativePtr) {

    if (!nativePtr) return;
    auto *handle = reinterpret_cast<projectm_handle>(nativePtr);
    projectm_destroy(handle);
    BRIDGE_LOGI("projectM instance destroyed: %p", handle);
}

} // extern "C"

