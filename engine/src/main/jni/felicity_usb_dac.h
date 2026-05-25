#pragma once

#include <jni.h>
#include <libusb.h>
#include <android/log.h>

#define FELICITY_USB_TAG "felicity_usb_dac"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  FELICITY_USB_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  FELICITY_USB_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, FELICITY_USB_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, FELICITY_USB_TAG, __VA_ARGS__)

/**
 * USB audio class constants from the USB Device Class Definition for Audio Devices spec.
 * Interface 0 is always the Audio Control interface (configuration/routing metadata).
 * Interface 1 (and sometimes 2) carry the actual AudioStreaming data.
 */
static constexpr int USB_AUDIO_CONTROL_INTERFACE = 0;
static constexpr int USB_AUDIO_STREAMING_INTERFACE = 1;

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Called from UsbDacDriver.nativeInitUsb. Receives the raw Android file descriptor
 * and device identifiers, wraps the FD into a libusb handle, and claims the audio
 * interface so user-space has exclusive control.
 */
JNIEXPORT jboolean JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeInitUsb(
        JNIEnv *env, jobject thiz,
        jint fileDescriptor, jint vendorId, jint productId);

/**
 * Called from UsbDacDriver.nativeReleaseUsb. Releases the interface, closes the
 * libusb handle, and deinitializes the libusb context cleanly.
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeReleaseUsb(
        JNIEnv *env, jobject thiz);

#ifdef __cplusplus
}
#endif

