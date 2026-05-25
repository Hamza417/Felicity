#include "felicity_usb_dac.h"
#include "uac_parser.h"
#include "uac_negotiator.h"
#include "usb_iso_stream.h"

static const int audio_interfaces[] = {USB_AUDIO_CONTROL_INTERFACE, USB_AUDIO_STREAMING_INTERFACE};

// These globals live for the duration of a single DAC session.
// They are cleared by nativeReleaseUsb so stale state is always detectable.
static libusb_context *g_usb_ctx = nullptr;
static libusb_device_handle *g_usb_handle = nullptr;
static int g_claimed_interface = -1;

// Descriptor snapshot populated by nativeNegotiateFormat after the parser runs.
// Kept alive so re-negotiation (e.g. sample rate change) skips the parse step.
static UacDeviceInfo g_device_info{};
static bool g_device_info_valid = false;

// The active ISO stream — created by nativeStartStream, destroyed by nativeStopStream.
// Using a pointer so its constructor does not run at static-init time.
static UsbIsoStream *g_iso_stream = nullptr;

/**
 * Attempts to detach a kernel driver from the given interface, if one is attached.
 * On Android, the kernel sometimes binds a dummy driver to USB audio endpoints; we
 * must evict it before libusb can claim the interface for user-space use.
 *
 * Returns true when user-space has free access to the interface.
 */
static bool detach_kernel_driver_if_needed(libusb_device_handle *handle, int iface) {
    int active = libusb_kernel_driver_active(handle, iface);

    if (active == 0) {
        LOGD("No kernel driver on interface %d", iface);
        return true;
    }
    if (active == LIBUSB_ERROR_NOT_SUPPORTED) {
        // Android reports NOT_SUPPORTED for interfaces already in user-space.
        LOGD("libusb_kernel_driver_active NOT_SUPPORTED on interface %d — treating as no driver",
             iface);
        return true;
    }
    if (active < 0) {
        LOGE("Could not query kernel driver on interface %d: %s",
             iface, libusb_strerror((libusb_error) active));
        return false;
    }

    LOGI("Detaching kernel driver from interface %d…", iface);
    int ret = libusb_detach_kernel_driver(handle, iface);
    if (ret == 0 || ret == LIBUSB_ERROR_NOT_FOUND) {
        LOGI("Kernel driver detached from interface %d", iface);
        return true;
    }
    LOGE("Failed to detach kernel driver from interface %d: %s",
         iface, libusb_strerror((libusb_error) ret));
    return false;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeInitUsb(
        JNIEnv * /*env*/, jobject /*thiz*/,
        jint fileDescriptor, jint vendorId, jint productId) {

    LOGI("nativeInitUsb — FD=%d VID=0x%04X PID=0x%04X", fileDescriptor, vendorId, productId);

    // Release any leftover session before starting fresh.
    if (g_usb_ctx != nullptr) {
        LOGW("Previous libusb context still open — releasing before re-init");
        if (g_usb_handle != nullptr) {
            if (g_claimed_interface >= 0) {
                libusb_release_interface(g_usb_handle, g_claimed_interface);
                g_claimed_interface = -1;
            }
            libusb_close(g_usb_handle);
            g_usb_handle = nullptr;
        }
        libusb_exit(g_usb_ctx);
        g_usb_ctx = nullptr;
    }
    g_device_info_valid = false;

    // 1. Initialize libusb context.
    int ret = libusb_init(&g_usb_ctx);
    if (ret != LIBUSB_SUCCESS) {
        LOGE("libusb_init failed: %s", libusb_strerror((libusb_error) ret));
        return JNI_FALSE;
    }
    LOGI("libusb context initialized");

    // 2. Wrap the Android FD — avoids needing /dev/bus/usb enumeration.
    ret = libusb_wrap_sys_device(g_usb_ctx,
                                 static_cast<intptr_t>(fileDescriptor),
                                 &g_usb_handle);
    if (ret != LIBUSB_SUCCESS || g_usb_handle == nullptr) {
        LOGE("libusb_wrap_sys_device failed: %s", libusb_strerror((libusb_error) ret));
        libusb_exit(g_usb_ctx);
        g_usb_ctx = nullptr;
        return JNI_FALSE;
    }
    LOGI("libusb handle wrapped from FD=%d", fileDescriptor);

    // 3. Detach kernel drivers so we can claim both interfaces.
    for (int iface: audio_interfaces) {
        if (!detach_kernel_driver_if_needed(g_usb_handle, iface)) {
            LOGW("Could not detach kernel driver from interface %d — claiming anyway", iface);
        }
    }

    // 4. Claim Audio Control interface (0) — needed to send control transfers.
    ret = libusb_claim_interface(g_usb_handle, USB_AUDIO_CONTROL_INTERFACE);
    if (ret != LIBUSB_SUCCESS) {
        LOGE("Failed to claim Audio Control interface (0): %s",
             libusb_strerror((libusb_error) ret));
        libusb_close(g_usb_handle);
        g_usb_handle = nullptr;
        libusb_exit(g_usb_ctx);
        g_usb_ctx = nullptr;
        return JNI_FALSE;
    }
    LOGI("Audio Control interface (0) claimed");

    // 5. Claim Audio Streaming interface (1) — carries the isochronous PCM frames.
    ret = libusb_claim_interface(g_usb_handle, USB_AUDIO_STREAMING_INTERFACE);
    if (ret != LIBUSB_SUCCESS) {
        LOGW("Could not claim Audio Streaming interface (1): %s — device may use interface 0 only",
             libusb_strerror((libusb_error) ret));
        g_claimed_interface = USB_AUDIO_CONTROL_INTERFACE;
    } else {
        LOGI("Audio Streaming interface (1) claimed");
        g_claimed_interface = USB_AUDIO_STREAMING_INTERFACE;
    }

    LOGI("USB DAC initialized — VID=0x%04X PID=0x%04X", vendorId, productId);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeNegotiateFormat(
        JNIEnv * /*env*/, jobject /*thiz*/,
        jint sampleRate, jint bitDepth, jint channels) {

    if (g_usb_handle == nullptr) {
        LOGE("nativeNegotiateFormat called but no device is open");
        return JNI_FALSE;
    }

    LOGI("nativeNegotiateFormat — %d Hz / %d-bit / %d ch", sampleRate, bitDepth, channels);

    // Parse descriptors once per device session. The data is in kernel memory so
    // the call is cheap and safe; we still cache the result to avoid redundant work
    // when the DSP engine changes format mid-session (e.g. sample rate switch).
    if (!g_device_info_valid) {
        if (!uac_parse_device(g_usb_handle, &g_device_info)) {
            LOGE("Descriptor parse failed — cannot negotiate format");
            return JNI_FALSE;
        }
        g_device_info_valid = true;
    }

    UacFormatRequest req{};
    req.sampleRate = static_cast<uint32_t>(sampleRate);
    req.bitDepth = static_cast<uint8_t>(bitDepth);
    req.channels = static_cast<uint8_t>(channels);

    const bool ok = uac_negotiate_format(g_usb_handle, &g_device_info, req);
    if (ok) {
        // Reset to unity gain so any previous software attenuation is cleared.
        uac_set_volume(g_usb_handle, &g_device_info, /*0 dB in Q8.8 =*/ 0x0000);
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeReleaseUsb(
        JNIEnv * /*env*/, jobject /*thiz*/) {

    LOGI("nativeReleaseUsb — tearing down USB DAC session");

    // Phase 5 teardown: stop the ISO stream and cancel all pending transfers
    // BEFORE releasing the interface or closing the handle. Attempting to close
    // libusb resources while transfers are still pending causes undefined behavior
    // and may lock up the USB host controller.
    if (g_iso_stream != nullptr) {
        g_iso_stream->stop();
        delete g_iso_stream;
        g_iso_stream = nullptr;
        LOGI("ISO stream torn down");
    }

    if (g_usb_handle != nullptr) {
        if (g_claimed_interface >= 0) {
            libusb_release_interface(g_usb_handle, g_claimed_interface);
            if (g_claimed_interface == USB_AUDIO_STREAMING_INTERFACE) {
                libusb_release_interface(g_usb_handle, USB_AUDIO_CONTROL_INTERFACE);
            }
            g_claimed_interface = -1;
            LOGD("USB interfaces released");
        }
        libusb_close(g_usb_handle);
        g_usb_handle = nullptr;
        LOGI("libusb device handle closed");
    }

    if (g_usb_ctx != nullptr) {
        libusb_exit(g_usb_ctx);
        g_usb_ctx = nullptr;
        LOGI("libusb context destroyed");
    }

    g_device_info_valid = false;
}

JNIEXPORT jboolean JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeStartStream(
        JNIEnv * /*env*/, jobject /*thiz*/) {

    if (g_usb_handle == nullptr) {
        LOGE("nativeStartStream called but no USB device is open");
        return JNI_FALSE;
    }
    if (!g_device_info_valid || g_device_info.altSettingCount == 0) {
        LOGE("nativeStartStream called before format negotiation completed");
        return JNI_FALSE;
    }

    // Tear down any previous stream (e.g., after a sample rate change).
    if (g_iso_stream != nullptr) {
        g_iso_stream->stop();
        delete g_iso_stream;
        g_iso_stream = nullptr;
    }

    // Find the active alt-setting (the one with a valid endpoint).
    // The negotiator will have selected the best one; we look for it by scanning
    // for the first entry with a non-zero endpoint address.
    const UacAltSetting *chosen = nullptr;
    for (int i = 0; i < g_device_info.altSettingCount; i++) {
        if (g_device_info.altSettings[i].endpointAddress != 0) {
            chosen = &g_device_info.altSettings[i];
            break;
        }
    }

    if (chosen == nullptr) {
        LOGE("No alt-setting with an endpoint found — cannot start stream");
        return JNI_FALSE;
    }

    g_iso_stream = new UsbIsoStream();
    const bool ok = g_iso_stream->start(
            g_usb_ctx,
            g_usb_handle,
            *chosen,
            chosen->bSubslotSize,
            chosen->bBitResolution
    );

    if (!ok) {
        delete g_iso_stream;
        g_iso_stream = nullptr;
        LOGE("Failed to start isochronous stream");
        return JNI_FALSE;
    }

    LOGI("Isochronous USB audio stream started");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeStopStream(
        JNIEnv * /*env*/, jobject /*thiz*/) {

    if (g_iso_stream == nullptr) return;

    LOGI("nativeStopStream — halting isochronous pipeline");
    g_iso_stream->stop();
    delete g_iso_stream;
    g_iso_stream = nullptr;
    LOGI("Isochronous USB audio stream stopped");
}

JNIEXPORT jint JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativePushPcm(
        JNIEnv *env, jobject /*thiz*/,
        jfloatArray samples, jint offset, jint count) {

    if (g_iso_stream == nullptr || !g_iso_stream->isRunning()) return 0;

    // GetFloatArrayElements gives us a direct or copied pointer to the float array.
    // We use JNI_ABORT on release since we never modify the array — no need to
    // copy back changes, and this avoids an unnecessary heap copy on some JVMs.
    jfloat *ptr = env->GetFloatArrayElements(samples, nullptr);
    if (ptr == nullptr) return 0;

    const int written = g_iso_stream->pushPcm(ptr + offset, count);
    env->ReleaseFloatArrayElements(samples, ptr, JNI_ABORT);
    return written;
}

} // extern "C"

