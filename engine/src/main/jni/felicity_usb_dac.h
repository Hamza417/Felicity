#pragma once

#include <jni.h>
#include <libusb.h>
#include <android/log.h>
#include "uac_descriptors.h"

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
 * Called from UsbDacDriver.nativeNegotiateFormat. Runs the UAC descriptor parser
 * and then the format negotiator so the DAC is locked into the requested sample
 * rate, bit depth, and channel count.
 */
JNIEXPORT jboolean JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeNegotiateFormat(
        JNIEnv *env, jobject thiz,
        jint sampleRate, jint bitDepth, jint channels);

/**
 * Called from UsbDacDriver.nativeReleaseUsb. Releases the interface, closes the
 * libusb handle, and deinitializes the libusb context cleanly.
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeReleaseUsb(
        JNIEnv *env, jobject thiz);

/**
 * Allocates the isochronous URB pool and spawns the SCHED_FIFO USB event thread.
 * Must be called after a successful nativeNegotiateFormat.
 */
JNIEXPORT jboolean JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeStartStream(
        JNIEnv *env, jobject thiz);

/**
 * Cancels all pending isochronous transfers, joins the event thread, and frees
 * the URB pool. Safe to call even if the stream was never started.
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeStopStream(
        JNIEnv *env, jobject thiz);

/**
 * Pushes interleaved float PCM samples from the DSP thread into the ring buffer
 * that feeds the isochronous USB pipeline. Thread-safe (SPSC).
 *
 * @param samples FloatArray of interleaved samples in [-1.0, 1.0].
 * @param offset  Starting index within the array.
 * @param count   Number of floats to write (frames × channels).
 * @return Number of samples actually accepted (may be less if buffer is full).
 */
JNIEXPORT jint JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativePushPcm(
        JNIEnv *env, jobject thiz,
        jfloatArray samples, jint offset, jint count);

/**
 * Discards all pending samples from the ring buffer without stopping the
 * isochronous stream. Use this during seeks and track transitions instead of
 * a full stop/restart cycle — the stream keeps running and new audio arrives
 * from the next handleBuffer call without any gap or underrun burst.
 */
JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeFlushRingBuffer(
        JNIEnv *env, jobject thiz);

#ifdef __cplusplus
}
#endif

