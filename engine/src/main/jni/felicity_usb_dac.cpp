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
 * Index into g_device_info.altSettings[] of the alt-setting that was last activated by
 * uac_negotiate_format(). nativeStartStream() uses this to open the isochronous stream
 * with the exact endpoint address and packet parameters that match the negotiated format.
 *
 * Storing -1 here means "no format has been negotiated yet" — nativeStartStream() will
 * refuse to run in that state rather than silently picking the wrong alt-setting.
 */
static int g_active_alt_idx = -1;

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
                for (int i = g_claimed_interface; i >= 0; i--) {
                    libusb_release_interface(g_usb_handle, i);
                }
                g_claimed_interface = -1;
            }
            libusb_close(g_usb_handle);
            g_usb_handle = nullptr;
        }

        libusb_exit(g_usb_ctx);
        g_usb_ctx = nullptr;
    }
    g_device_info_valid = false;
    g_active_alt_idx = -1;

    // --- CRITICAL FIX FOR ANDROID SELINUX ---
    // Prevent libusb from scanning the device tree, which is blocked by Android.
    libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);

    // Initialize libusb context.
    int ret = libusb_init(&g_usb_ctx);
    if (ret != LIBUSB_SUCCESS) {
        LOGE("libusb_init failed: %s", libusb_strerror((libusb_error) ret));
        return JNI_FALSE;
    }
    LOGI("libusb context initialized");

    // Wrap the Android FD — avoids needing /dev/bus/usb enumeration.
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

    // Extract the physical device pointer from our open handle
    libusb_device *device = libusb_get_device(g_usb_handle);

    // Get the active configuration to see how many interfaces this DAC actually has
    libusb_config_descriptor *config = nullptr;
    ret = libusb_get_active_config_descriptor(device,
                                              &config); // Pass 'device' instead of 'g_usb_handle'

    if (ret != LIBUSB_SUCCESS || config == nullptr) {
        LOGE("Failed to get config descriptor: %s", libusb_strerror((libusb_error) ret));
        libusb_close(g_usb_handle);
        g_usb_handle = nullptr;
        libusb_exit(g_usb_ctx);
        g_usb_ctx = nullptr;
        return JNI_FALSE;
    }

    int num_interfaces = config->bNumInterfaces;
    LOGI("Device has %d interfaces. Detaching kernel drivers and claiming...", num_interfaces);

    // Dynamically detach and claim EVERY interface on the device
    for (int i = 0; i < num_interfaces; i++) {
        if (!detach_kernel_driver_if_needed(g_usb_handle, i)) {
            LOGW("Could not detach kernel driver from interface %d — claiming anyway", i);
        }

        ret = libusb_claim_interface(g_usb_handle, i);
        if (ret != LIBUSB_SUCCESS) {
            LOGE("Failed to claim interface %d: %s", i, libusb_strerror((libusb_error) ret));
        } else {
            LOGI("Interface %d claimed successfully", i);
            // Track the highest interface claimed so our teardown logic can clean it up
            g_claimed_interface = i;
        }
    }

    // Free the config descriptor since we are done with it
    libusb_free_config_descriptor(config);

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

    const int chosenIdx = uac_negotiate_format(g_usb_handle, &g_device_info, req);
    if (chosenIdx >= 0) {
        // Remember which alt-setting is now live so nativeStartStream() can open the
        // isochronous pipeline with the exact matching endpoint and packet parameters.
        g_active_alt_idx = chosenIdx;
        // Reset to unity gain so any previous software attenuation is cleared.
        uac_set_volume(g_usb_handle, &g_device_info, /*0 dB in Q8.8 =*/ 0x0000);
    }
    return chosenIdx >= 0 ? JNI_TRUE : JNI_FALSE;
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
            // Loop backwards from the highest interface we claimed down to 0
            for (int i = g_claimed_interface; i >= 0; i--) {
                libusb_release_interface(g_usb_handle, i);
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
    g_active_alt_idx = -1;
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
    if (g_active_alt_idx < 0 || g_active_alt_idx >= g_device_info.altSettingCount) {
        LOGE("nativeStartStream called but no alt-setting has been negotiated yet");
        return JNI_FALSE;
    }

    // Tear down any previous stream (e.g., after a sample rate change).
    if (g_iso_stream != nullptr) {
        g_iso_stream->stop();
        delete g_iso_stream;
        g_iso_stream = nullptr;
    }

    // Use the exact alt-setting that uac_negotiate_format() activated. This is the
    // only alt-setting whose endpoint address and packet parameters match the format
    // that was programmed on the device — using a different one would cause the DAC
    // to receive data with the wrong packet size and would prevent the LED from
    // reflecting the correct sample rate.
    const UacAltSetting &chosen = g_device_info.altSettings[g_active_alt_idx];
    LOGI("Starting stream with negotiated alt-setting %d (endpoint=0x%02X, subslot=%d, bits=%d)",
         chosen.bAlternateSetting, chosen.endpointAddress,
         chosen.bSubslotSize, chosen.bBitResolution);

    g_iso_stream = new UsbIsoStream();
    const bool ok = g_iso_stream->start(
            g_usb_ctx,
            g_usb_handle,
            chosen,
            chosen.bSubslotSize,
            chosen.bBitResolution
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

    LOGI("nativePushPcm — count=%d offset=%d", count, offset);

    if (g_iso_stream == nullptr || !g_iso_stream->isRunning()) {
        LOGE("nativePushPcm called but ISO stream is not running");
        return 0;
    }

    // GetFloatArrayElements gives us a direct or copied pointer to the float array.
    // We use JNI_ABORT on release since we never modify the array — no need to
    // copy back changes, and this avoids an unnecessary heap copy on some JVMs.
    jfloat *ptr = env->GetFloatArrayElements(samples, nullptr);
    if (ptr == nullptr) {
        LOGE("Failed to get float array elements");
        return 0;
    }

    const int written = g_iso_stream->pushPcm(ptr + offset, count);
    env->ReleaseFloatArrayElements(samples, ptr, JNI_ABORT);
    LOGI("nativePushPcm — wrote %d samples to ISO stream", written);
    return written;
}

} // extern "C"

