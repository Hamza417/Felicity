#include "felicity_usb_dac.h"
#include "uac_parser.h"
#include "uac_negotiator.h"
#include "usb_iso_stream.h"

static const int audio_interfaces[] = {USB_AUDIO_CONTROL_INTERFACE, USB_AUDIO_STREAMING_INTERFACE};

// These globals live for the duration of a single DAC session.
// They are cleared by nativeReleaseUsb so stale state is always detectable.
static libusb_context *g_usb_ctx = nullptr;
static libusb_device_handle *g_usb_handle = nullptr;

// Replaced single int with a bitmask so we can selectively claim and release interfaces.
static uint32_t g_claimed_interfaces_mask = 0;

// Descriptor snapshot populated by nativeNegotiateFormat after the parser runs.
// Kept alive so re-negotiation (e.g. sample rate change) skips the parse step.
static UacDeviceInfo g_device_info{};
static bool g_device_info_valid = false;

// The active ISO stream — created by nativeStartStream, destroyed by nativeStopStream.
// Using a pointer so its constructor does not run at static-init time.
static UsbIsoStream *g_iso_stream = nullptr;

// The sample rate last negotiated with the DAC.  nativeStartStream() reads this to
// tell the isochronous stream how many audio frames belong in each 125 µs USB
// microframe.  Stored separately from the alt-setting because UacAltSetting only
// carries *supported* rates, not the one we actually programmed.
static int g_negotiated_sample_rate = 0;

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

extern "C"
{

JNIEXPORT jboolean JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeInitUsb(
        JNIEnv * /*env*/, jobject /*thiz*/,
        jint fileDescriptor, jint vendorId, jint productId) {

    LOGI("nativeInitUsb — FD=%d VID=0x%04X PID=0x%04X", fileDescriptor, vendorId, productId);

    // Release any leftover session before starting fresh.
    if (g_usb_ctx != nullptr) {
        LOGW("Previous libusb context still open — releasing before re-init");
        if (g_usb_handle != nullptr) {
            if (g_claimed_interfaces_mask != 0) {
                for (int i = 31; i >= 0; i--) {
                    if (g_claimed_interfaces_mask & (1U << i)) {
                        libusb_release_interface(g_usb_handle, i);
                    }
                }
                g_claimed_interfaces_mask = 0;
            }
            libusb_close(g_usb_handle);
            g_usb_handle = nullptr;
        }

        libusb_exit(g_usb_ctx);
        g_usb_ctx = nullptr;
    }
    g_device_info_valid = false;
    g_active_alt_idx = -1;
    g_negotiated_sample_rate = 0;

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
    ret = libusb_get_active_config_descriptor(device, &config);

    if (ret != LIBUSB_SUCCESS || config == nullptr) {
        LOGE("Failed to get config descriptor: %s", libusb_strerror((libusb_error) ret));
        libusb_close(g_usb_handle);
        g_usb_handle = nullptr;
        libusb_exit(g_usb_ctx);
        g_usb_ctx = nullptr;
        return JNI_FALSE;
    }

    int num_interfaces = config->bNumInterfaces;
    LOGI("Device has %d interfaces. Selectively detaching and claiming audio paths...",
         num_interfaces);

    // --- FIX FOR COMPOSITE HEADSET DACS ---
    for (int i = 0; i < num_interfaces; i++) {
        const libusb_interface *iface = &config->interface[i];
        if (iface->num_altsetting == 0) continue;

        const libusb_interface_descriptor *desc = &iface->altsetting[0];

        // Skip non-audio interfaces (like HID / Media Control buttons)
        if (desc->bInterfaceClass != LIBUSB_CLASS_AUDIO) {
            LOGD("Skipping interface %d (Class %d) — Not USB Audio", i, desc->bInterfaceClass);
            continue;
        }

        // Safely skip Capture/Microphone interfaces
        bool is_capture_only = false;
        if (desc->bInterfaceSubClass == 2) { // 2 = AUDIOSTREAMING
            bool has_playback = false;
            bool has_capture = false;

            for (int j = 0; j < iface->num_altsetting; j++) {
                const libusb_interface_descriptor *alt = &iface->altsetting[j];
                for (int k = 0; k < alt->bNumEndpoints; k++) {
                    // Endpoint directions: 0x80 bit 0 = OUT (Playback), 1 = IN (Capture)
                    if ((alt->endpoint[k].bEndpointAddress & 0x80) == 0) {
                        LOGD("Interface %d alt %d has OUT endpoint 0x%02X — marking as Playback",
                             i, alt->bAlternateSetting, alt->endpoint[k].bEndpointAddress);
                        has_playback = true;
                    } else {
                        LOGD("Interface %d alt %d has IN endpoint 0x%02X — marking as Capture",
                             i, alt->bAlternateSetting, alt->endpoint[k].bEndpointAddress);
                        has_capture = true;
                    }
                }
            }
            if (has_capture && !has_playback) {
                is_capture_only = true;
            }
        }

        if (is_capture_only) {
            LOGI("Skipping interface %d — Capture/Microphone path", i);
            continue;
        }

        // If it's a playback or control interface, claim it safely.
        if (!detach_kernel_driver_if_needed(g_usb_handle, i)) {
            LOGW("Could not detach kernel driver from interface %d — claiming anyway", i);
        }

        ret = libusb_claim_interface(g_usb_handle, i);
        if (ret != LIBUSB_SUCCESS) {
            LOGE("Failed to claim interface %d: %s", i, libusb_strerror((libusb_error) ret));
        } else {
            LOGI("Interface %d claimed successfully", i);
            g_claimed_interfaces_mask |= (1U << i);
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

    // Parse descriptors once per device session.
    if (!g_device_info_valid) {
        if (!uac_parse_device(g_usb_handle, &g_device_info)) {
            LOGE("Descriptor parse failed — cannot negotiate format");
            return JNI_FALSE;
        }

        // Keep the endpoint filter as a fail-safe in case uac_parse_device
        // aggressively grabs endpoints from unclaimed interfaces.
        int playbackSettingCount = 0;
        for (int i = 0; i < g_device_info.altSettingCount; i++) {
            uint8_t epAddr = g_device_info.altSettings[i].endpointAddress;

            if ((epAddr & 0x80) == 0) {
                if (playbackSettingCount != i) {
                    g_device_info.altSettings[playbackSettingCount] = g_device_info.altSettings[i];
                }
                playbackSettingCount++;
            } else {
                LOGD("Filtering out recording alt-setting index %d (endpoint=0x%02X)", i, epAddr);
            }
        }

        g_device_info.altSettingCount = playbackSettingCount;

        if (g_device_info.altSettingCount == 0) {
            LOGE("Sanitization error: No valid OUT/Playback interfaces found.");
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
        g_active_alt_idx = chosenIdx;
        g_negotiated_sample_rate = sampleRate;
        uac_set_volume(g_usb_handle, &g_device_info, /*0 dB in Q8.8 =*/0x0000);
    }
    return chosenIdx >= 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeReleaseUsb(
        JNIEnv * /*env*/, jobject /*thiz*/) {

    LOGI("nativeReleaseUsb — tearing down USB DAC session");

    // Phase 5 teardown: stop the ISO stream and cancel all pending transfers
    if (g_iso_stream != nullptr) {
        g_iso_stream->stop();
        delete g_iso_stream;
        g_iso_stream = nullptr;
        LOGI("ISO stream torn down");
    }

    if (g_usb_handle != nullptr) {
        if (g_claimed_interfaces_mask != 0) {
            // Loop backwards over all possible interface indices
            for (int i = 31; i >= 0; i--) {
                if (g_claimed_interfaces_mask & (1U << i)) {
                    libusb_release_interface(g_usb_handle, i);
                }
            }
            g_claimed_interfaces_mask = 0;
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
    g_negotiated_sample_rate = 0;
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

    if (g_iso_stream != nullptr) {
        g_iso_stream->stop();
        delete g_iso_stream;
        g_iso_stream = nullptr;
    }

    const UacAltSetting &chosen = g_device_info.altSettings[g_active_alt_idx];
    LOGI("Starting stream with negotiated alt-setting %d (endpoint=0x%02X, subslot=%d, bits=%d, rate=%d Hz)",
         chosen.bAlternateSetting, chosen.endpointAddress,
         chosen.bSubslotSize, chosen.bBitResolution, g_negotiated_sample_rate);

    g_iso_stream = new UsbIsoStream();
    const bool ok = g_iso_stream->start(
            g_usb_ctx, g_usb_handle, chosen,
            chosen.bSubslotSize, chosen.bBitResolution, g_negotiated_sample_rate);

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

    if (g_iso_stream == nullptr)
        return;

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

    if (g_iso_stream == nullptr || !g_iso_stream->isRunning()) {
        LOGE("nativePushPcm called but ISO stream is not running");
        return 0;
    }

    jfloat *ptr = env->GetFloatArrayElements(samples, nullptr);
    if (ptr == nullptr) {
        LOGE("Failed to get float array elements");
        return 0;
    }

    const int written = g_iso_stream->pushPcm(ptr + offset, count);
    env->ReleaseFloatArrayElements(samples, ptr, JNI_ABORT);
    return written;
}

JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeFlushStream(
        JNIEnv * /*env*/, jobject /*thiz*/) {

    if (g_iso_stream == nullptr || !g_iso_stream->isRunning()) {
        LOGD("nativeFlushStream — stream not running, nothing to flush");
        return;
    }

    g_iso_stream->flushRingBuffer();
    LOGD("nativeFlushStream — ring buffer cleared after seek");
}

JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeSetVolume(
        JNIEnv * /*env*/, jobject /*thiz*/,
        jshort volumeDb256) {

    if (g_usb_handle == nullptr || !g_device_info_valid) {
        LOGD("nativeSetVolume — no active DAC to set volume on, ignoring");
        return;
    }

    LOGI("nativeSetVolume — pushing %d/256 dB (≈ %.1f dB) to DAC",
         volumeDb256, static_cast<float>(volumeDb256) / 256.0f);
    uac_set_volume(g_usb_handle, &g_device_info, static_cast<int16_t>(volumeDb256));
}

} // extern "C"