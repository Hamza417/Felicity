#include "uac_parser.h"
#include "felicity_usb_dac.h"

#include <cstring>

// ------------------------------------------------------------------ //
//  Helpers for reading little-endian multibyte values from raw descriptor
//  bytes. The USB spec says all multibyte fields are little-endian.
// ------------------------------------------------------------------ //

static inline uint16_t read_u16(const uint8_t *p) {
    return static_cast<uint16_t>(p[0]) | (static_cast<uint16_t>(p[1]) << 8);
}

static inline uint32_t read_u24(const uint8_t *p) {
    return static_cast<uint32_t>(p[0])
           | (static_cast<uint32_t>(p[1]) << 8)
           | (static_cast<uint32_t>(p[2]) << 16);
}

static inline uint32_t read_u32(const uint8_t *p) {
    return static_cast<uint32_t>(p[0])
           | (static_cast<uint32_t>(p[1]) << 8)
           | (static_cast<uint32_t>(p[2]) << 16)
           | (static_cast<uint32_t>(p[3]) << 24);
}

// ------------------------------------------------------------------ //
//  AC class-specific descriptor parsers
// ------------------------------------------------------------------ //

/**
 * Reads one class-specific AC descriptor from [p] (length = bLength) and appends
 * whatever entity it describes to [info]. We skip unknown sub-types gracefully
 * so a device with extra UAC 3.0 or vendor-specific descriptors does not crash us.
 */
static void parse_ac_cs_descriptor(const uint8_t *p, UacDeviceInfo *info) {
    if (p[0] < 3) return; // bLength, bDescriptorType, bDescriptorSubtype minimum

    const uint8_t subtype = p[2];

    switch (subtype) {
        case AC_SUBTYPE_HEADER: {
            // The AC Header is the first descriptor in the AC interface's extra bytes.
            // It tells us the UAC version (bcdADC) and — for UAC1 — which other
            // interface numbers belong to the audio function.
            if (p[0] < 8) return;
            info->bcdADC = read_u16(p + 3);
            info->uacVersion = (info->bcdADC >= 0x0200) ? 2 : 1;
            LOGI("UAC Header: bcdADC=0x%04X → UAC %d", info->bcdADC, info->uacVersion);
            break;
        }

        case AC_SUBTYPE_INPUT_TERMINAL: {
            if (info->inputTerminalCount >= UAC_MAX_TERMINALS) return;
            if (p[0] < 12) return;

            UacInputTerminal &t = info->inputTerminals[info->inputTerminalCount++];
            t.bTerminalID = p[3];
            t.wTerminalType = read_u16(p + 4);
            t.bAssocTerminal = p[6];
            t.bNrChannels = p[7];
            t.wChannelConfig = read_u16(p + 8);

            // UAC2 Input Terminal has bClockSourceID right after the channel info
            t.bClockSourceId = (info->uacVersion == 2 && p[0] >= 17) ? p[12] : 0;

            LOGI("Input Terminal ID=%d type=0x%04X ch=%d clockSrc=%d",
                 t.bTerminalID, t.wTerminalType, t.bNrChannels, t.bClockSourceId);
            break;
        }

        case AC_SUBTYPE_OUTPUT_TERMINAL: {
            if (info->outputTerminalCount >= UAC_MAX_TERMINALS) return;
            if (p[0] < 9) return;

            UacOutputTerminal &t = info->outputTerminals[info->outputTerminalCount++];
            t.bTerminalID = p[3];
            t.wTerminalType = read_u16(p + 4);
            t.bAssocTerminal = p[6];
            t.bSourceId = p[7];

            // UAC2 Output Terminal stores bClockSourceID at byte index 8
            t.bClockSourceId = (info->uacVersion == 2 && p[0] >= 12) ? p[8] : 0;

            LOGI("Output Terminal ID=%d type=0x%04X src=%d clockSrc=%d",
                 t.bTerminalID, t.wTerminalType, t.bSourceId, t.bClockSourceId);
            break;
        }

        case AC_SUBTYPE_FEATURE_UNIT: {
            if (info->featureUnitCount >= UAC_MAX_FEATURE_UNITS) return;
            if (p[0] < 7) return;

            UacFeatureUnit &fu = info->featureUnits[info->featureUnitCount++];
            fu.bUnitID = p[3];
            fu.bSourceId = p[4];

            if (info->uacVersion == 1) {
                // UAC1: bControlSize tells us how many bytes each bmaControls entry is.
                // We only read the master channel (index 0) for the mute/volume check.
                const uint8_t bControlSize = p[5];
                fu.masterControls = (bControlSize >= 1 && p[0] >= 7u + bControlSize) ? p[6] : 0;
                fu.hasMute = (fu.masterControls & UAC1_FU_CTRL_MUTE) != 0;
                fu.hasVolume = (fu.masterControls & UAC1_FU_CTRL_VOLUME) != 0;
            } else {
                // UAC2: bmaControls is always 4 bytes per channel; master at index 0.
                const uint32_t ctrl = (p[0] >= 10) ? read_u32(p + 6) : 0;
                // Bits 0-1 = mute present/programmable, bits 2-3 = volume present/programmable
                fu.masterControls = static_cast<uint8_t>(ctrl & 0xFF);
                fu.hasMute = (ctrl & 0x03) != 0;
                fu.hasVolume = (ctrl & 0x0C) != 0;
            }

            LOGI("Feature Unit ID=%d src=%d mute=%d volume=%d",
                 fu.bUnitID, fu.bSourceId, fu.hasMute, fu.hasVolume);
            break;
        }

        case AC_SUBTYPE_CLOCK_SOURCE: {
            // Only exists in UAC2. Stores the programmable clock that drives sample rate.
            if (info->uacVersion != 2) return;
            if (info->clockSourceCount >= UAC_MAX_CLOCK_SOURCES) return;
            if (p[0] < 8) return;

            UacClockSource &cs = info->clockSources[info->clockSourceCount++];
            cs.bClockID = p[3];
            cs.bmAttributes = p[4];
            cs.bmControls = p[5];
            cs.bAssocTerminal = p[6];

            // Bits 0-1 of bmAttributes: 00=external, 01=fixed, 10=variable, 11=programmable
            const char *clockType[] = {"external", "internal fixed",
                                       "internal variable", "internal programmable"};
            LOGI("Clock Source ID=%d type=%s SAM_FREQ_CTRL=%s",
                 cs.bClockID,
                 clockType[cs.bmAttributes & 0x03],
                 (cs.bmControls & 0x03) ? "programmable" : "read-only");
            break;
        }

        default:
            // Unknown or unneeded AC sub-type (e.g. Mixer Unit, Selector Unit).
            // We silently skip it rather than aborting the parse.
            break;
    }
}

/**
 * Walks the raw `extra` bytes of an AC interface alt-setting and calls
 * [parse_ac_cs_descriptor] for every class-specific descriptor found.
 */
static void parse_ac_interface(const libusb_interface_descriptor *alt, UacDeviceInfo *info,
                               uint8_t interfaceNumber) {
    info->acInterfaceNumber = interfaceNumber;
    const uint8_t *p = alt->extra;
    int rem = alt->extra_length;

    while (rem >= 3) {
        const uint8_t bLength = p[0];
        const uint8_t bDescType = p[1];

        if (bLength < 3 || bLength > rem) break;

        if (bDescType == CS_INTERFACE) {
            parse_ac_cs_descriptor(p, info);
        }

        rem -= bLength;
        p += bLength;
    }
}

// ------------------------------------------------------------------ //
//  AS class-specific descriptor parsers (one alt-setting at a time)
// ------------------------------------------------------------------ //

/**
 * Parses the class-specific descriptors and isochronous endpoint for one
 * Audio Streaming alternate setting and appends the result to [info].
 *
 * Alt-setting 0 is always the zero-bandwidth placeholder (no endpoints);
 * callers skip it before calling here.
 */
static void parse_as_alt_setting(const libusb_interface_descriptor *alt,
                                 UacDeviceInfo *info) {
    if (info->altSettingCount >= UAC_MAX_ALT_SETTINGS) return;

    UacAltSetting &as = info->altSettings[info->altSettingCount];
    memset(&as, 0, sizeof(UacAltSetting));
    as.bAlternateSetting = alt->bAlternateSetting;

    const uint8_t *p = alt->extra;
    int rem = alt->extra_length;

    bool foundGeneral = false;
    bool foundFormatType = false;

    while (rem >= 3) {
        const uint8_t bLength = p[0];
        const uint8_t bDescType = p[1];
        const uint8_t bSubtype = p[2];

        if (bLength < 3 || bLength > rem) break;

        if (bDescType == CS_INTERFACE) {
            if (bSubtype == AS_SUBTYPE_GENERAL && !foundGeneral) {
                foundGeneral = true;
                if (info->uacVersion == 1) {
                    // UAC1 AS_GENERAL: bTerminalLink(3), bDelay(4), wFormatTag(5-6)
                    if (bLength >= 7) {
                        as.bTerminalLink = p[3];
                        as.formatTag = read_u16(p + 5);
                    }
                } else {
                    // UAC2 AS_GENERAL: bTerminalLink(3), bmControls(4), bFormatType(5),
                    //                  bmFormats(6-9), bNrChannels(10), bmChannelConfig(11-14),
                    //                  iChannelNames(15)
                    if (bLength >= 10) {
                        as.bTerminalLink = p[3];
                        as.bFormatType = p[5];
                        as.formatTag = read_u32(p + 6);
                        as.bNrChannels = (bLength >= 11) ? p[10] : 0;
                    }
                }
            }

            if (bSubtype == AS_SUBTYPE_FORMAT_TYPE && !foundFormatType) {
                foundFormatType = true;
                if (bLength < 4) {
                    rem -= bLength;
                    p += bLength;
                    continue;
                }

                as.bFormatType = p[3];

                if (as.bFormatType == FORMAT_TYPE_I) {
                    if (info->uacVersion == 1) {
                        // UAC1 Type I: bNrChannels(4), bSubframeSize(5), bBitResolution(6),
                        //              bSamFreqType(7), then sample rates follow
                        if (bLength >= 8) {
                            as.bNrChannels = p[4];
                            as.bSubslotSize = p[5];
                            as.bBitResolution = p[6];
                            as.bSamFreqType = p[7];

                            if (as.bSamFreqType == 0) {
                                // Continuous range: 3 three-byte entries (min, max, step)
                                if (bLength >= 8 + 9) {
                                    as.sampleRates[0] = read_u24(p + 8);
                                    as.sampleRates[1] = read_u24(p + 11);
                                    as.sampleRates[2] = read_u24(p + 14);
                                    as.sampleRateCount = 3;
                                }
                            } else {
                                // Discrete list: bSamFreqType entries × 3 bytes each
                                const int count = as.bSamFreqType;
                                for (int i = 0; i < count && i < UAC_MAX_SAMPLE_RATES; i++) {
                                    const int offset = 8 + i * 3;
                                    if (offset + 3 > bLength) break;
                                    as.sampleRates[as.sampleRateCount++] = read_u24(p + offset);
                                }
                            }
                        }
                    } else {
                        // UAC2 Type I: bSubslotSize(4), bBitResolution(5)
                        // Sample rate is managed by the Clock Source, not stored here.
                        if (bLength >= 6) {
                            as.bSubslotSize = p[4];
                            as.bBitResolution = p[5];
                        }
                    }
                }
            }
        }

        rem -= bLength;
        p += bLength;
    }

    // Walk the endpoint list to find the isochronous OUT endpoint.
    for (int e = 0; e < alt->bNumEndpoints; e++) {
        const libusb_endpoint_descriptor &ep = alt->endpoint[e];

        // Isochronous transfer type = bits 1:0 of bmAttributes set to 0b01 (0x01).
        // OUT direction = bit 7 of bEndpointAddress is 0 (host-to-device).
        const bool isIsochronous = (ep.bmAttributes & 0x03) == LIBUSB_TRANSFER_TYPE_ISOCHRONOUS;
        const bool isOut = (ep.bEndpointAddress & LIBUSB_ENDPOINT_DIR_MASK)
                           == LIBUSB_ENDPOINT_OUT;

        if (isIsochronous && isOut) {
            as.endpointAddress = ep.bEndpointAddress;
            as.endpointAttributes = ep.bmAttributes;
            as.wMaxPacketSize = ep.wMaxPacketSize;
            as.bInterval = ep.bInterval;
            LOGI("Alt %d: ISO OUT endpoint 0x%02X maxPkt=%d interval=%d",
                 as.bAlternateSetting, as.endpointAddress, as.wMaxPacketSize, as.bInterval);
            break;
        }
    }

    // Only register this alt-setting if it actually carries audio data.
    if (as.endpointAddress != 0 || foundGeneral) {
        LOGI("Alt %d: fmt=0x%04X subslot=%d bits=%d ch=%d rates=%d",
             as.bAlternateSetting, as.formatTag,
             as.bSubslotSize, as.bBitResolution, as.bNrChannels, as.sampleRateCount);
        info->altSettingCount++;
    }
}

// ------------------------------------------------------------------ //
//  Top-level parse entry point
// ------------------------------------------------------------------ //

bool uac_parse_device(libusb_device_handle *handle, UacDeviceInfo *info) {
    memset(info, 0, sizeof(UacDeviceInfo));

    libusb_device *dev = libusb_get_device(handle);

    // Read and log the standard USB device descriptor so we know VID/PID and
    // USB spec version even before we dive into audio-class specifics.
    libusb_device_descriptor devDesc{};
    int ret = libusb_get_device_descriptor(dev, &devDesc);
    if (ret != LIBUSB_SUCCESS) {
        LOGE("Failed to read device descriptor: %s", libusb_strerror((libusb_error) ret));
        return false;
    }
    LOGI("Device descriptor: VID=0x%04X PID=0x%04X bcdUSB=0x%04X bDeviceClass=0x%02X",
         devDesc.idVendor, devDesc.idProduct, devDesc.bcdUSB, devDesc.bDeviceClass);

    // Read the active configuration so we can iterate all interfaces on the device.
    libusb_config_descriptor *config = nullptr;
    ret = libusb_get_active_config_descriptor(dev, &config);
    if (ret != LIBUSB_SUCCESS) {
        LOGE("Failed to read config descriptor: %s", libusb_strerror((libusb_error) ret));
        return false;
    }

    LOGI("Config descriptor: bNumInterfaces=%d", config->bNumInterfaces);

    for (int i = 0; i < config->bNumInterfaces; i++) {
        const libusb_interface *iface = &config->interface[i];

        for (int a = 0; a < iface->num_altsetting; a++) {
            const libusb_interface_descriptor &alt = iface->altsetting[a];

            // Skip interfaces that are not USB Audio Class.
            if (alt.bInterfaceClass != LIBUSB_CLASS_AUDIO) continue;

            const uint8_t ifaceNum = alt.bInterfaceNumber;
            const uint8_t subClass = alt.bInterfaceSubClass;
            const uint8_t altIndex = alt.bAlternateSetting;

            if (subClass == UAC_SUBCLASS_AUDIOCONTROL && altIndex == 0) {
                // The Audio Control interface has exactly one alt-setting (index 0).
                // Its class-specific extra bytes map the entire routing topology.
                LOGI("Parsing AC interface %d (alt %d)", ifaceNum, altIndex);
                parse_ac_interface(&alt, info, ifaceNum);
                info->asInterfaceNumber = ifaceNum + 1; // sensible default; overwritten below

            } else if (subClass == UAC_SUBCLASS_AUDIOSTREAMING) {
                // Record which interface carries the stream.
                info->asInterfaceNumber = ifaceNum;

                if (altIndex == 0) {
                    // Alt 0 is always the zero-bandwidth placeholder; skip it.
                    continue;
                }

                LOGI("Parsing AS interface %d alt-setting %d", ifaceNum, altIndex);
                parse_as_alt_setting(&alt, info);
            }
        }
    }

    libusb_free_config_descriptor(config);

    LOGI("Parse complete: UAC%d | AC iface=%d AS iface=%d | "
         "inTerminals=%d outTerminals=%d FUs=%d clockSrcs=%d altSettings=%d",
         info->uacVersion,
         info->acInterfaceNumber, info->asInterfaceNumber,
         info->inputTerminalCount, info->outputTerminalCount,
         info->featureUnitCount, info->clockSourceCount,
         info->altSettingCount);

    return info->altSettingCount > 0;
}
