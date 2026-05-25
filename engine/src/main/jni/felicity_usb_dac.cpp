#include "felicity_usb_dac.h"

const int audio_interfaces[] = {USB_AUDIO_CONTROL_INTERFACE, USB_AUDIO_STREAMING_INTERFACE};

// These two globals live for the duration of a single DAC session.
// They are reset to null by nativeReleaseUsb so we can detect stale state.
static libusb_context *g_usb_ctx = nullptr;
static libusb_device_handle *g_usb_handle = nullptr;

// The streaming interface index we claimed. Stored so we can release it cleanly.
static int g_claimed_interface = -1;

/**
 * Attempts to detach a kernel driver from the given interface, if one is attached.
 * On Android, the kernel sometimes binds a dummy driver to USB audio endpoints; we
 * must evict it before libusb can claim the interface for user-space use.
 *
 * Returns true when user-space has free access to the interface (either no driver
 * was present, or we successfully detached it).
 */
static bool detach_kernel_driver_if_needed(libusb_device_handle *handle, int interface_number) {
    int active = libusb_kernel_driver_active(handle, interface_number);
    if (active == 0) {
        // No kernel driver sitting on this interface — nothing to do.
        LOGD("No kernel driver on interface %d", interface_number);
        return true;
    }
    if (active == LIBUSB_ERROR_NOT_SUPPORTED) {
        // Some platforms (especially Android) report this for interfaces that are
        // already accessible from user-space. Treat it as "no driver present".
        LOGD("libusb_kernel_driver_active returned NOT_SUPPORTED on interface %d — "
             "treating as no driver", interface_number);
        return true;
    }
    if (active < 0) {
        LOGE("Could not query kernel driver state on interface %d: %s",
             interface_number, libusb_strerror((libusb_error) active));
        return false;
    }

    // active == 1: a kernel driver is attached. Detach it so we can claim the interface.
    LOGI("Detaching kernel driver from interface %d…", interface_number);
    int ret = libusb_detach_kernel_driver(handle, interface_number);
    if (ret == 0) {
        LOGI("Kernel driver detached from interface %d", interface_number);
        return true;
    }
    if (ret == LIBUSB_ERROR_NOT_FOUND) {
        // The driver disappeared between the check and the detach — that is fine.
        return true;
    }
    LOGE("Failed to detach kernel driver from interface %d: %s",
         interface_number, libusb_strerror((libusb_error) ret));
    return false;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeInitUsb(
        JNIEnv * /*env*/, jobject /*thiz*/,
        jint fileDescriptor, jint vendorId, jint productId) {

    LOGI("nativeInitUsb — FD=%d VID=0x%04X PID=0x%04X", fileDescriptor, vendorId, productId);

    // Clean up any leftover state from a previous session before we start fresh.
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

    // Step 1 — Initialize a fresh libusb context. Every libusb session starts here;
    // the context holds global state like the event-handling thread and log level.
    int ret = libusb_init(&g_usb_ctx);
    if (ret != LIBUSB_SUCCESS) {
        LOGE("libusb_init failed: %s", libusb_strerror((libusb_error) ret));
        return JNI_FALSE;
    }
    LOGI("libusb context initialized successfully");

    // Step 2 — Wrap the Android file descriptor into a libusb device handle.
    // libusb_wrap_sys_device() tells libusb "I already have the kernel FD — use it"
    // instead of trying to enumerate and open the device itself (which would fail
    // on Android because we lack root access to /dev/bus/usb/*).
    ret = libusb_wrap_sys_device(g_usb_ctx,
                                 static_cast<intptr_t>(fileDescriptor),
                                 &g_usb_handle);
    if (ret != LIBUSB_SUCCESS || g_usb_handle == nullptr) {
        LOGE("libusb_wrap_sys_device failed: %s", libusb_strerror((libusb_error) ret));
        libusb_exit(g_usb_ctx);
        g_usb_ctx = nullptr;
        return JNI_FALSE;
    }
    LOGI("libusb device handle wrapped from FD=%d", fileDescriptor);

    // Step 3 — Detach the kernel driver from the Audio Control interface (0) and the
    // Audio Streaming interface (1) so we can claim them for user-space access.
    // Failure to detach is not fatal on Android (the driver is often already absent),
    // but we log a warning so the issue is easy to spot in logcat.
    for (int iface: audio_interfaces) {
        if (!detach_kernel_driver_if_needed(g_usb_handle, iface)) {
            LOGW("Could not detach kernel driver from interface %d — claiming anyway", iface);
        }
    }

    // Step 4 — Claim the Audio Streaming interface so that no other process (including
    // the kernel) can use it while Felicity is active. Interface 0 (Audio Control) is
    // used to send configuration commands; interface 1 carries the actual PCM stream.
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

    ret = libusb_claim_interface(g_usb_handle, USB_AUDIO_STREAMING_INTERFACE);
    if (ret != LIBUSB_SUCCESS) {
        // Not every DAC exposes a separate streaming interface — log and continue.
        LOGW("Could not claim Audio Streaming interface (1): %s — device may use interface 0 only",
             libusb_strerror((libusb_error) ret));
        g_claimed_interface = USB_AUDIO_CONTROL_INTERFACE;
    } else {
        LOGI("Audio Streaming interface (1) claimed");
        g_claimed_interface = USB_AUDIO_STREAMING_INTERFACE;
    }

    LOGI("USB DAC fully initialized — VID=0x%04X PID=0x%04X ready for streaming", vendorId,
         productId);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeReleaseUsb(
        JNIEnv * /*env*/, jobject /*thiz*/) {

    LOGI("nativeReleaseUsb — cleaning up libusb resources");

    if (g_usb_handle != nullptr) {
        // Release whichever interface(s) we managed to claim so the kernel can
        // reclaim them after we disconnect. Skipping this would leave the device
        // in a "claimed by nobody" state until the USB stack resets it.
        if (g_claimed_interface >= 0) {
            libusb_release_interface(g_usb_handle, g_claimed_interface);
            if (g_claimed_interface == USB_AUDIO_STREAMING_INTERFACE) {
                // Also release the control interface we claimed alongside it.
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
}

} // extern "C"

