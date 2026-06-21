#include "felicity_usb_dac.h"
#include "uac_parser.h"
#include "uac_negotiator.h"
#include "usb_iso_stream.h"

#include <atomic>

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

/**
 * Atomic flag that gates PCM pushes so the DSP thread never feeds audio into
 * a stream that hasn't finished spinning up its URBs.  It is set to true only
 * after nativeStartStream() has successfully submitted all isochronous transfers
 * and the USB event thread is running.  Without this guard the DSP can flood the
 * log with "ISO stream not running" errors during the brief window between
 * negotiation and the actual stream start — a window that widens significantly
 * on slower DACs like the HiBy FC3 and Fiio K3.
 */
static std::atomic<bool> g_iso_stream_ready{false};

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
 * Snapshot of the most recent successful format negotiation.  When the audio engine
 * pauses and resumes (or transitions between tracks that share the same sample rate),
 * we reuse the cached alt-setting instead of hitting the DAC with another
 * libusb_set_interface_alt_setting() handshake.  This avoids transient "Entity not
 * found" errors some DACs return when asked to re-activate an already-active
 * alt-setting, and it makes pause/resume effectively instant.
 */
static int g_cached_sample_rate = 0;
static int g_cached_bit_depth = 0;
static int g_cached_channels = 0;

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
    g_iso_stream_ready.store(false, std::memory_order_release);
    g_cached_sample_rate = 0;
    g_cached_bit_depth = 0;
    g_cached_channels = 0;

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
        if (iface->num_altsetting == 0)
            continue;

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

        // Correct the device-level asInterfaceNumber to match the remaining
        // playback alt-settings.  The parser naively stores the *last* AS
        // interface it encounters, which is wrong for DACs that expose
        // separate playback and capture AudioStreaming interfaces (e.g.
        // HiBy FC3 where playback is on interface 1 but capture is on
        // interface 2).  Since we just filtered out all capture alt-settings,
        // every remaining entry points at the playback AS interface.
        if (g_device_info.altSettingCount > 0) {
            g_device_info.asInterfaceNumber =
                    g_device_info.altSettings[0].asInterfaceNumber;
            LOGD("Corrected asInterfaceNumber to %d based on filtered alt-settings",
                 g_device_info.asInterfaceNumber);
        }

        g_device_info_valid = true;
    }

    // Smart re-negotiation: if the requested format exactly matches the last
    // successful negotiation, skip the USB handshake entirely.  Some DACs
    // (notably the HiBy FC3) return "Entity not found" when asked to
    // re-activate an already-active alt-setting via libusb_set_interface_alt_setting.
    // Reusing the cached state avoids that error and makes pause/resume gapless.
    if (g_active_alt_idx >= 0 && g_cached_sample_rate == sampleRate &&
        g_cached_bit_depth == bitDepth && g_cached_channels == channels) {
        LOGI("Format unchanged (%d Hz / %d-bit / %d ch) — reusing cached alt-setting %d",
             sampleRate, bitDepth, channels, g_active_alt_idx);
        return JNI_TRUE;
    }

    UacFormatRequest req{};
    req.sampleRate = static_cast<uint32_t>(sampleRate);
    req.bitDepth = static_cast<uint8_t>(bitDepth);
    req.channels = static_cast<uint8_t>(channels);

    const int chosenIdx = uac_negotiate_format(g_usb_handle, &g_device_info, req);
    if (chosenIdx >= 0) {
        g_active_alt_idx = chosenIdx;
        g_negotiated_sample_rate = sampleRate;
        g_cached_sample_rate = sampleRate;
        g_cached_bit_depth = bitDepth;
        g_cached_channels = channels;
        uac_set_volume(g_usb_handle, &g_device_info, /*0 dB in Q8.8 =*/0x0000);
    } else {
        LOGE("Format negotiation failed — DAC cannot stream at %d Hz / %d-bit / %d ch. "
             "Check that the requested format is supported and the DAC is still connected.",
             sampleRate, bitDepth, channels);
    }
    return chosenIdx >= 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeReleaseUsb(
        JNIEnv * /*env*/, jobject /*thiz*/) {

    LOGI("nativeReleaseUsb — tearing down USB DAC session");

    // Clear the ready flag before touching the stream so any concurrent
    // PCM pushes bail out immediately.
    g_iso_stream_ready.store(false, std::memory_order_release);

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
    g_iso_stream_ready.store(false, std::memory_order_release);
    g_cached_sample_rate = 0;
    g_cached_bit_depth = 0;
    g_cached_channels = 0;
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
        g_iso_stream_ready.store(false, std::memory_order_release);
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

    // Mark the stream as fully ready only after URBs are in flight.
    // This is the synchronization point that gates nativePushPcm so the DSP
    // thread never tries to feed audio into a half-initialized pipeline.
    g_iso_stream_ready.store(true, std::memory_order_release);

    LOGI("Isochronous USB audio stream started");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_app_simple_felicity_engine_usb_UsbDacDriver_nativeStopStream(
        JNIEnv * /*env*/, jobject /*thiz*/) {

    // Clear the ready flag first so any in-flight PCM pushes bail out
    // immediately instead of racing with the teardown below.
    g_iso_stream_ready.store(false, std::memory_order_release);

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

    // Use the atomic ready flag as the primary gate.  This is more precise
    // than checking g_iso_stream->isRunning() because it guarantees the
    // stream object exists AND all URBs are in flight.  Dropping PCM frames
    // here is harmless — the ring buffer will fill once the DSP restarts
    // after the stream comes online, and logging every dropped frame at
    // ERROR level (as we used to) flooded logcat on slower DACs.
    if (!g_iso_stream_ready.load(std::memory_order_acquire)) {
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

    if (!g_iso_stream_ready.load(std::memory_order_acquire) || g_iso_stream == nullptr) {
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