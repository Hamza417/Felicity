#include "uac_negotiator.h"
#include "felicity_usb_dac.h"

#include <cstring>
#include <cstdlib>
#include <climits>

// ------------------------------------------------------------------ //
//  UAC Control Transfer helpers
//  All UAC control requests are USB Control Transfers sent on endpoint 0.
//  The direction, type, and recipient bits are packed into bmRequestType.
// ------------------------------------------------------------------ //

/**
 * Sends a UAC SET_CUR control transfer to a unit/terminal/clock entity on the
 * Audio Control interface. This is how we program the DAC's sample rate, mute
 * state, and volume — think of it as writing a register on the hardware.
 *
 * @param handle          Open libusb device handle.
 * @param entityId        ID of the target AC entity (Feature Unit, Clock Source, …).
 * @param acInterfaceNum  The Audio Control interface number (usually 0).
 * @param controlSelector Which property to set (FU_CS_MUTE, UAC2_CS_SAM_FREQ_CONTROL, …).
 * @param channel         Channel number; 0 = master.
 * @param data            Payload bytes.
 * @param dataLen         Length of [data] in bytes.
 * @return true on success.
 */
static bool send_set_cur(libusb_device_handle *handle,
                         uint8_t entityId, uint8_t acInterfaceNum,
                         uint8_t controlSelector, uint8_t channel,
                         uint8_t *data, uint16_t dataLen) {
    // bmRequestType = 0x21: host-to-device | class | interface
    // wValue = (controlSelector << 8) | channel
    // wIndex = (entityId << 8) | interfaceNumber
    const int transferred = libusb_control_transfer(
            handle,
            /*bmRequestType=*/ 0x21,
            /*bRequest=*/      UAC_SET_CUR,
            /*wValue=*/        static_cast<uint16_t>((controlSelector << 8) | channel),
            /*wIndex=*/        static_cast<uint16_t>((entityId << 8) | acInterfaceNum),
            data, dataLen,
            /*timeout_ms=*/ 1000
    );

    if (transferred < 0) {
        LOGE("SET_CUR failed (entity=%d cs=0x%02X): %s",
             entityId, controlSelector, libusb_strerror((libusb_error) transferred));
        return false;
    }
    return true;
}

/**
 * Sends a UAC GET_CUR control transfer and reads back the current value of a
 * property from an AC entity. Used to verify that SET_CUR was accepted.
 *
 * @return Number of bytes received, or a negative libusb error code.
 */
static int send_get_cur(libusb_device_handle *handle,
                        uint8_t entityId, uint8_t acInterfaceNum,
                        uint8_t controlSelector, uint8_t channel,
                        uint8_t *data, uint16_t dataLen) {
    // bmRequestType = 0xA1: device-to-host | class | interface
    return libusb_control_transfer(
            handle,
            /*bmRequestType=*/ 0xA1,
            /*bRequest=*/      UAC_GET_CUR,
            /*wValue=*/        static_cast<uint16_t>((controlSelector << 8) | channel),
            /*wIndex=*/        static_cast<uint16_t>((entityId << 8) | acInterfaceNum),
            data, dataLen,
            /*timeout_ms=*/ 1000
    );
}

/**
 * UAC1-specific: sends SET_CUR for the sampling frequency directly to the
 * isochronous OUT endpoint rather than to an AC entity.
 *
 * UAC1 §5.2.3.2.3.1 says the SAMPLING_FREQ_CONTROL lives on the endpoint,
 * not on any AC unit.
 *
 * @param endpointAddr The isochronous OUT endpoint address (e.g. 0x01).
 * @param sampleRate   Target sample rate in Hz.
 * @return true on success.
 */
static bool uac1_set_endpoint_sample_rate(libusb_device_handle *handle,
                                          uint8_t endpointAddr,
                                          uint32_t sampleRate) {
    uint8_t buf[3];
    buf[0] = static_cast<uint8_t>( sampleRate & 0xFF);
    buf[1] = static_cast<uint8_t>((sampleRate >> 8) & 0xFF);
    buf[2] = static_cast<uint8_t>((sampleRate >> 16) & 0xFF);

    // bmRequestType = 0x22: host-to-device | class | endpoint
    const int ret = libusb_control_transfer(
            handle,
            /*bmRequestType=*/ 0x22,
            /*bRequest=*/      UAC_SET_CUR,
            /*wValue=*/        static_cast<uint16_t>(UAC1_ENDPOINT_CS_SAMPLING_FREQ << 8),
            /*wIndex=*/        static_cast<uint16_t>(endpointAddr),
            buf, 3,
            /*timeout_ms=*/ 1000
    );

    if (ret < 0) {
        LOGE("UAC1 endpoint sample rate SET_CUR failed: %s",
             libusb_strerror((libusb_error) ret));
        return false;
    }

    // Read it back to confirm the device accepted our request.
    uint8_t verify[3] = {};
    const int vret = libusb_control_transfer(
            handle,
            /*bmRequestType=*/ 0xA2, // device-to-host | class | endpoint
            /*bRequest=*/      UAC_GET_CUR,
            /*wValue=*/        static_cast<uint16_t>(UAC1_ENDPOINT_CS_SAMPLING_FREQ << 8),
            /*wIndex=*/        static_cast<uint16_t>(endpointAddr),
            verify, 3,
            /*timeout_ms=*/ 1000
    );

    if (vret == 3) {
        const uint32_t confirmed = static_cast<uint32_t>(verify[0])
                                   | (static_cast<uint32_t>(verify[1]) << 8)
                                   | (static_cast<uint32_t>(verify[2]) << 16);
        if (confirmed == sampleRate) {
            LOGI("UAC1 sample rate confirmed: %u Hz", confirmed);
        } else {
            LOGW("UAC1 sample rate mismatch: requested %u Hz, device reports %u Hz",
                 sampleRate, confirmed);
        }
    }
    return true;
}

/**
 * UAC2-specific: programs the Clock Source entity with the desired sample rate,
 * then reads back the clock validity flag to confirm the PLL has locked.
 *
 * UAC2 §5.2.5.1.1 — the sample frequency control lives on the Clock Source
 * entity (a 4-byte little-endian integer), not on the endpoint.
 *
 * @param clockId        bClockID of the target Clock Source.
 * @param acInterfaceNum The AC interface number.
 * @param sampleRate     Target sample rate in Hz.
 * @return true when the clock was programmed and reports as valid.
 */
static bool uac2_set_clock_frequency(libusb_device_handle *handle,
                                     uint8_t clockId, uint8_t acInterfaceNum,
                                     uint32_t sampleRate) {
    uint8_t buf[4];
    buf[0] = static_cast<uint8_t>( sampleRate & 0xFF);
    buf[1] = static_cast<uint8_t>((sampleRate >> 8) & 0xFF);
    buf[2] = static_cast<uint8_t>((sampleRate >> 16) & 0xFF);
    buf[3] = static_cast<uint8_t>((sampleRate >> 24) & 0xFF);

    if (!send_set_cur(handle, clockId, acInterfaceNum,
                      UAC2_CS_SAM_FREQ_CONTROL, 0, buf, 4)) {
        return false;
    }
    LOGI("UAC2 clock %d: SET_CUR sample rate → %u Hz", clockId, sampleRate);

    // Read back to confirm.
    uint8_t readback[4] = {};
    const int got = send_get_cur(handle, clockId, acInterfaceNum,
                                 UAC2_CS_SAM_FREQ_CONTROL, 0, readback, 4);
    if (got == 4) {
        const uint32_t confirmed = static_cast<uint32_t>(readback[0])
                                   | (static_cast<uint32_t>(readback[1]) << 8)
                                   | (static_cast<uint32_t>(readback[2]) << 16)
                                   | (static_cast<uint32_t>(readback[3]) << 24);
        if (confirmed != sampleRate) {
            LOGW("UAC2 clock %d rate mismatch: requested %u Hz, confirmed %u Hz",
                 clockId, sampleRate, confirmed);
        } else {
            LOGI("UAC2 clock %d rate confirmed: %u Hz", clockId, confirmed);
        }
    }

    // Check the clock validity flag (bit 0 of the 1-byte CS_CLOCK_VALID_CONTROL).
    // A value of 1 means the internal PLL has locked and the device is ready to stream.
    uint8_t validByte = 0;
    const int vret = send_get_cur(handle, clockId, acInterfaceNum,
                                  UAC2_CS_CLOCK_VALID_CONTROL, 0, &validByte, 1);
    if (vret == 1) {
        if (validByte & 0x01) {
            LOGI("UAC2 clock %d: PLL locked and valid", clockId);
        } else {
            LOGW("UAC2 clock %d: PLL NOT locked after programming — proceeding anyway", clockId);
        }
    } else {
        LOGD("UAC2 clock validity check not supported on this device (ret=%d)", vret);
    }

    return true;
}

// ------------------------------------------------------------------ //
//  Alt-setting selection logic
// ------------------------------------------------------------------ //

/**
 * Scores a candidate alt-setting against the desired format. Higher = better.
 * We prefer an exact match on sample rate and bit depth, with channel count
 * as a tiebreaker. If neither matches we still return a non-zero score for
 * the fallback path so we always end up with something workable.
 */
static int score_alt_setting(const UacAltSetting &alt, const UacFormatRequest &req) {
    if (alt.endpointAddress == 0) return 0; // Zero-bandwidth, skip.
    if (alt.bFormatType != FORMAT_TYPE_I) return 0; // We only handle PCM Type I.

    int score = 0;

    // Bit depth match is critical: playing 24-bit audio through a 16-bit alt-setting
    // truncates the bottom 8 bits and is audibly wrong.
    const int requestedBytes = (req.bitDepth + 7) / 8;
    if (alt.bSubslotSize == requestedBytes) {
        score += 100;
        if (alt.bBitResolution == req.bitDepth) score += 50;
    } else {
        // Bit depth mismatch: prefer the closest without truncation.
        if (alt.bSubslotSize >= requestedBytes) score += 10;
    }

    // Sample rate match. For UAC1 we check the discrete/continuous list;
    // for UAC2 the rate is programmed on the Clock Source so any alt-setting works.
    if (alt.bSamFreqType == 0 && alt.sampleRateCount >= 2) {
        // Continuous range: check that req.sampleRate falls within [min, max].
        if (req.sampleRate >= alt.sampleRates[0] && req.sampleRate <= alt.sampleRates[1]) {
            score += 80;
        }
    } else {
        for (int i = 0; i < alt.sampleRateCount; i++) {
            if (alt.sampleRates[i] == req.sampleRate) {
                score += 80;
                break;
            }
        }
        // UAC2: sampleRateCount == 0 means rate is clock-driven — always eligible.
        if (alt.sampleRateCount == 0) score += 80;
    }

    // Channel count: prefer an exact match; mono is acceptable for stereo requests.
    if (alt.bNrChannels == req.channels) {
        score += 20;
    } else if (alt.bNrChannels > 0 && alt.bNrChannels >= req.channels) {
        score += 5;
    }

    return score;
}

/**
 * Walks the parsed alt-setting list and returns the index of the entry with the
 * highest score for [req]. Returns -1 if no usable alt-setting exists at all.
 */
static int select_best_alt_setting(const UacDeviceInfo *info, const UacFormatRequest &req) {
    int bestIdx = -1;
    int bestScore = 0;

    for (int i = 0; i < info->altSettingCount; i++) {
        const int s = score_alt_setting(info->altSettings[i], req);
        LOGD("Alt %d score=%d (subslot=%d bits=%d ch=%d)",
             info->altSettings[i].bAlternateSetting, s,
             info->altSettings[i].bSubslotSize,
             info->altSettings[i].bBitResolution,
             info->altSettings[i].bNrChannels);
        if (s > bestScore) {
            bestScore = s;
            bestIdx = i;
        }
    }
    return bestIdx;
}

/**
 * Resolves the Clock Source ID to use for a given alt-setting.
 * For UAC2 we look up the Input Terminal that feeds the streaming interface,
 * then return its bClockSourceId. For UAC1 there is no clock entity (returns 0).
 */
static uint8_t resolve_clock_source(const UacDeviceInfo *info) {
    if (info->uacVersion != 2) return 0;

    // The Input Terminal with wTerminalType = 0x0101 (USB Streaming) is the one
    // that feeds audio from the host into the routing graph.
    for (int i = 0; i < info->inputTerminalCount; i++) {
        if (info->inputTerminals[i].wTerminalType == 0x0101) {
            return info->inputTerminals[i].bClockSourceId;
        }
    }

    // If no USB Streaming terminal was tagged (uncommon), fall back to the first
    // programmable clock source we found during parsing.
    for (int i = 0; i < info->clockSourceCount; i++) {
        if ((info->clockSources[i].bmAttributes & 0x03) == 0x03) {
            return info->clockSources[i].bClockID;
        }
    }

    // Last resort: use the first clock source regardless of type.
    return (info->clockSourceCount > 0) ? info->clockSources[0].bClockID : 0;
}

// ------------------------------------------------------------------ //
//  Public API
// ------------------------------------------------------------------ //

int uac_negotiate_format(libusb_device_handle *handle,
                          const UacDeviceInfo *info,
                          const UacFormatRequest &request) {

    LOGI("Format negotiation start — target: %u Hz / %d-bit / %d ch",
         request.sampleRate, request.bitDepth, request.channels);

    // Step 1 — Find the alt-setting that best matches what the DSP engine wants.
    const int bestIdx = select_best_alt_setting(info, request);
    if (bestIdx < 0) {
        LOGE("No usable audio streaming alt-setting found on this device");
        return -1;
    }

    const UacAltSetting &chosen = info->altSettings[bestIdx];
    LOGI("Selected alt-setting %d — subslot=%d bits=%d ch=%d endpoint=0x%02X",
         chosen.bAlternateSetting, chosen.bSubslotSize,
         chosen.bBitResolution, chosen.bNrChannels, chosen.endpointAddress);

    // Activate the chosen alt-setting on the Audio Streaming interface.
    // This is the USB operation that tells the device "switch to this format now".
    int ret = libusb_set_interface_alt_setting(
            handle,
            info->asInterfaceNumber,
            chosen.bAlternateSetting);

    if (ret != LIBUSB_SUCCESS) {
        LOGE("libusb_set_interface_alt_setting(%d, %d) failed: %s",
             info->asInterfaceNumber, chosen.bAlternateSetting,
             libusb_strerror((libusb_error) ret));
        return -1;
    }
    LOGI("Alt-setting %d activated on AS interface %d",
         chosen.bAlternateSetting, info->asInterfaceNumber);

    // Step 2 — Program the clock / sample rate.
    // The mechanism differs between UAC1 and UAC2.
    if (info->uacVersion == 1) {
        // UAC1: the sample rate is written directly to the isochronous endpoint.
        if (chosen.endpointAddress != 0) {
            if (!uac1_set_endpoint_sample_rate(handle,
                                               chosen.endpointAddress,
                                               request.sampleRate)) {
                LOGW("UAC1 sample rate programming failed — device may auto-select a rate");
            }
        }
    } else {
        // UAC2: find the Clock Source entity that feeds this stream and program it.
        const uint8_t clockId = resolve_clock_source(info);
        if (clockId != 0) {
            if (!uac2_set_clock_frequency(handle, clockId,
                                          info->acInterfaceNumber,
                                          request.sampleRate)) {
                LOGW("UAC2 clock programming failed — device may auto-select a rate");
            }
        } else {
            LOGW("No Clock Source entity found — skipping sample rate programming");
        }
    }

    // Step 3 — Unmute the output.
    // Walk the feature unit list and send SET_CUR MUTE_CONTROL = 0x00 (unmuted)
    // to every FU that supports mute control.
    bool unmuted = false;
    for (int i = 0; i < info->featureUnitCount; i++) {
        const UacFeatureUnit &fu = info->featureUnits[i];
        if (!fu.hasMute) continue;

        uint8_t muteByte = 0x00; // 0x00 = not muted
        const bool ok = send_set_cur(handle, fu.bUnitID, info->acInterfaceNumber,
                                     FU_CS_MUTE, /*channel=*/ 0,
                                     &muteByte, 1);
        if (ok) {
            LOGI("Feature Unit %d: output unmuted", fu.bUnitID);
            unmuted = true;

            // Verify the mute state was accepted.
            uint8_t readback = 0xFF;
            const int got = send_get_cur(handle, fu.bUnitID, info->acInterfaceNumber,
                                         FU_CS_MUTE, 0, &readback, 1);
            if (got == 1 && readback == 0x00) {
                LOGI("Feature Unit %d: mute state confirmed as unmuted", fu.bUnitID);
            } else if (got >= 0) {
                LOGW("Feature Unit %d: mute readback = 0x%02X (expected 0x00)", fu.bUnitID,
                     readback);
            }
        } else {
            LOGW("Feature Unit %d: failed to unmute (device may not support software mute control)",
                 fu.bUnitID);
        }
    }

    if (!unmuted && info->featureUnitCount > 0) {
        LOGD("No Feature Units supported mute control — assuming output is already active");
    }

    LOGI("Format negotiation complete — DAC ready to stream %u Hz / %d-bit / %d ch",
         request.sampleRate, request.bitDepth, request.channels);

    // Return the index of the activated alt-setting so the caller can hand it directly
    // to the isochronous stream. This is the key piece that lets the stream use the
    // correct endpoint address and packet size for the negotiated format.
    return bestIdx;
}

void uac_set_volume(libusb_device_handle *handle,
                    const UacDeviceInfo *info,
                    int16_t volumeDb256) {

    for (int i = 0; i < info->featureUnitCount; i++) {
        const UacFeatureUnit &fu = info->featureUnits[i];
        if (!fu.hasVolume) continue;

        // Volume is a signed 16-bit value in 1/256 dB steps (Q8.8 fixed-point).
        // 0x0000 = 0 dB, 0x0100 = +1 dB, 0xFF00 = -1 dB.
        uint8_t buf[2];
        buf[0] = static_cast<uint8_t>( volumeDb256 & 0xFF);
        buf[1] = static_cast<uint8_t>((volumeDb256 >> 8) & 0xFF);

        const bool ok = send_set_cur(handle, fu.bUnitID, info->acInterfaceNumber,
                                     FU_CS_VOLUME, /*channel=*/ 0,
                                     buf, 2);
        if (ok) {
            LOGI("Feature Unit %d: volume set to %d/256 dB", fu.bUnitID, volumeDb256);
        }
        // Only set volume on the first unit that supports it.
        break;
    }
}
