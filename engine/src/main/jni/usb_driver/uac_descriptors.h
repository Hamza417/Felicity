#pragma once

#include <cstdint>

/**
 * Constants and in-memory data structures that mirror the USB Audio Class
 * (UAC) specification — UAC 1.0 (bcdADC = 0x0100) and UAC 2.0 (0x0200).
 *
 * Nothing in this file touches libusb directly; it is purely a data-model
 * layer so the parser and negotiator can talk the same language.
 *
 * Reference documents:
 *   - USB Audio Class 1.0 Spec (usbclass_uac10.pdf)
 *   - USB Audio Class 2.0 Spec (USB_Audio_v2.0_final.pdf)
 *
 * @author Hamza417
 */

// ------------------------------------------------------------------ //
//  USB descriptor type tags (bDescriptorType)
// ------------------------------------------------------------------ //

/** Class-specific interface descriptor (tagged 0x24 per the USB spec). */
static constexpr uint8_t CS_INTERFACE = 0x24;

/** Class-specific endpoint descriptor (tagged 0x25). */
static constexpr uint8_t CS_ENDPOINT = 0x25;

// ------------------------------------------------------------------ //
//  Audio Interface Sub-Class codes (bInterfaceSubClass)
// ------------------------------------------------------------------ //

static constexpr uint8_t UAC_SUBCLASS_AUDIOCONTROL = 0x01;
static constexpr uint8_t UAC_SUBCLASS_AUDIOSTREAMING = 0x02;
static constexpr uint8_t UAC_SUBCLASS_MIDISTREAMING = 0x03;

// ------------------------------------------------------------------ //
//  AC Interface descriptor sub-types (bDescriptorSubtype, subclass = AC)
// ------------------------------------------------------------------ //

static constexpr uint8_t AC_SUBTYPE_HEADER = 0x01;
static constexpr uint8_t AC_SUBTYPE_INPUT_TERMINAL = 0x02;
static constexpr uint8_t AC_SUBTYPE_OUTPUT_TERMINAL = 0x03;
static constexpr uint8_t AC_SUBTYPE_MIXER_UNIT = 0x04;
static constexpr uint8_t AC_SUBTYPE_SELECTOR_UNIT = 0x05;
static constexpr uint8_t AC_SUBTYPE_FEATURE_UNIT = 0x06;
static constexpr uint8_t AC_SUBTYPE_PROCESSING_UNIT = 0x07; // UAC1
static constexpr uint8_t AC_SUBTYPE_EXTENSION_UNIT = 0x08;
static constexpr uint8_t AC_SUBTYPE_CLOCK_SOURCE = 0x0A;     // UAC2
static constexpr uint8_t AC_SUBTYPE_CLOCK_SELECTOR = 0x0B;   // UAC2
static constexpr uint8_t AC_SUBTYPE_CLOCK_MULTIPLIER = 0x0C; // UAC2

// ------------------------------------------------------------------ //
//  AS Interface descriptor sub-types (bDescriptorSubtype, subclass = AS)
// ------------------------------------------------------------------ //

static constexpr uint8_t AS_SUBTYPE_GENERAL = 0x01;
static constexpr uint8_t AS_SUBTYPE_FORMAT_TYPE = 0x02;

// ------------------------------------------------------------------ //
//  UAC Format Type codes (bFormatType in AS_FORMAT_TYPE)
// ------------------------------------------------------------------ //

static constexpr uint8_t FORMAT_TYPE_I = 0x01;   // PCM / linear audio
static constexpr uint8_t FORMAT_TYPE_II = 0x02;  // compressed (MP3, AC3 …)
static constexpr uint8_t FORMAT_TYPE_III = 0x03; // IEC 60958 pass-through

// ------------------------------------------------------------------ //
//  UAC1 wFormatTag values (for Type I)
// ------------------------------------------------------------------ //

static constexpr uint16_t UAC1_FORMAT_PCM = 0x0001;
static constexpr uint16_t UAC1_FORMAT_PCM8 = 0x0002;
static constexpr uint16_t UAC1_FORMAT_IEEE_FLOAT = 0x0003;

// ------------------------------------------------------------------ //
//  UAC2 bmFormats bitmask bits (for Type I)
// ------------------------------------------------------------------ //

static constexpr uint32_t UAC2_FORMAT_PCM = (1u << 0);
static constexpr uint32_t UAC2_FORMAT_PCM8 = (1u << 1);
static constexpr uint32_t UAC2_FORMAT_IEEE_FLOAT = (1u << 2);
static constexpr uint32_t UAC2_FORMAT_DSD = (1u << 31);

// ------------------------------------------------------------------ //
//  UAC1 Feature Unit control selector bitmasks (bmControls byte)
// ------------------------------------------------------------------ //

static constexpr uint8_t UAC1_FU_CTRL_MUTE = (1 << 0);
static constexpr uint8_t UAC1_FU_CTRL_VOLUME = (1 << 1);

// ------------------------------------------------------------------ //
//  UAC control request codes (bRequest)
// ------------------------------------------------------------------ //

static constexpr uint8_t UAC_SET_CUR = 0x01;
static constexpr uint8_t UAC_GET_CUR = 0x81;
static constexpr uint8_t UAC_GET_MIN = 0x82;
static constexpr uint8_t UAC_GET_MAX = 0x83;
static constexpr uint8_t UAC_GET_RES = 0x84;

// ------------------------------------------------------------------ //
//  UAC1 control selectors
// ------------------------------------------------------------------ //

/** CS for the sampling frequency on an isochronous endpoint (UAC1). */
static constexpr uint8_t UAC1_ENDPOINT_CS_SAMPLING_FREQ = 0x01;

/** CS for mute on a Feature Unit (UAC1 and UAC2). */
static constexpr uint8_t FU_CS_MUTE = 0x01;

/** CS for volume on a Feature Unit (UAC1 and UAC2). */
static constexpr uint8_t FU_CS_VOLUME = 0x02;

// ------------------------------------------------------------------ //
//  UAC2 clock source control selectors
// ------------------------------------------------------------------ //

/** CS for the sample frequency on a Clock Source entity (UAC2). */
static constexpr uint8_t UAC2_CS_SAM_FREQ_CONTROL = 0x01;

/** CS that tells us whether the clock has locked onto its source (UAC2). */
static constexpr uint8_t UAC2_CS_CLOCK_VALID_CONTROL = 0x02;

// ------------------------------------------------------------------ //
//  Parsed data structures (filled in by the parser, consumed by the negotiator)
// ------------------------------------------------------------------ //

/** Maximum number of entries per list — raised later if a real device overflows. */
static constexpr int UAC_MAX_TERMINALS = 8;
static constexpr int UAC_MAX_FEATURE_UNITS = 8;
static constexpr int UAC_MAX_CLOCK_SOURCES = 4;
static constexpr int UAC_MAX_ALT_SETTINGS = 16;
static constexpr int UAC_MAX_SAMPLE_RATES = 12;

/**
 * Represents a single USB audio input terminal (where audio enters the DAC's
 * internal routing graph — e.g. the USB streaming endpoint on the host side).
 */
struct UacInputTerminal {
    uint8_t bTerminalID;
    uint16_t wTerminalType;
    uint8_t bAssocTerminal;
    uint8_t bNrChannels;
    uint16_t wChannelConfig;
    uint8_t bClockSourceId; // UAC2 only; 0 if UAC1
};

/**
 * Represents a USB audio output terminal (where audio leaves the routing graph —
 * e.g. the analog line out or headphone jack on a USB DAC).
 */
struct UacOutputTerminal {
    uint8_t bTerminalID;
    uint16_t wTerminalType;
    uint8_t bAssocTerminal;
    uint8_t bSourceId;
    uint8_t bClockSourceId; // UAC2 only; 0 if UAC1
};

/**
 * Represents a Feature Unit, which is the UAC name for a block that can apply
 * volume, mute, bass, treble, etc. Most DACs expose at least one to control
 * the analog output level.
 */
struct UacFeatureUnit {
    uint8_t bUnitID;
    uint8_t bSourceId;
    /** Bitfield of UAC1_FU_CTRL_* for the master channel. */
    uint8_t masterControls;
    bool hasMute;
    bool hasVolume;
};

/**
 * Represents a UAC 2.0 Clock Source entity. UAC1 devices have no clock
 * entity — their sample rate is set directly on the isochronous endpoint.
 */
struct UacClockSource {
    uint8_t bClockID;
    /** Bit 0-1: 00 = external, 01 = internal fixed, 10 = internal variable, 11 = internal programmable. */
    uint8_t bmAttributes;
    uint8_t bmControls;
    uint8_t bAssocTerminal;
};

/**
 * Everything we know about one alternate setting on an Audio Streaming interface.
 * Alt-setting 0 is always zero-bandwidth (no audio data); 1 and above carry real
 * PCM streams, each potentially at a different bit depth and sample rate.
 */
struct UacAltSetting {
    uint8_t bAlternateSetting;
    uint8_t bTerminalLink;
    uint8_t bFormatType;

    /** UAC1: wFormatTag (PCM = 0x0001). UAC2: bmFormats bitmask. */
    uint32_t formatTag;

    uint8_t bNrChannels;
    uint8_t bSubslotSize;   // bytes per sample slot (e.g. 3 for 24-bit packed)
    uint8_t bBitResolution; // actual significant bits  (e.g. 24 inside a 3-byte slot)

    /**
     * UAC1: 0 = continuous range (rates[0]=min, rates[1]=max, rates[2]=step).
     *       N = N discrete rates stored in rates[0..N-1].
     * UAC2: always 0 (rate is set on the Clock Source, not here).
     */
    uint8_t bSamFreqType;
    uint32_t sampleRates[UAC_MAX_SAMPLE_RATES];
    int sampleRateCount;

    /**
     * The USB interface number (bInterfaceNumber) this alt-setting belongs to.
     * We store this per alt-setting rather than relying on a single device-level
     * field because some DACs expose multiple AudioStreaming interfaces — for
     * instance one for playback and one for capture — and the negotiator needs
     * the exact interface number to call libusb_set_interface_alt_setting().
     */
    uint8_t asInterfaceNumber;

    /** Address of the isochronous OUT endpoint that carries audio to the DAC. */
    uint8_t endpointAddress;
    uint8_t endpointAttributes;
    uint16_t wMaxPacketSize;
    uint8_t bInterval;

    /** For UAC2: the entity ID of the Clock Source linked to this stream. */
    uint8_t bClockId;
};

/**
 * The top-level result of the descriptor parsing phase.
 * Produced by the parser, consumed by the negotiator.
 */
struct UacDeviceInfo {
    /** bcdADC from the AC Header descriptor: 0x0100 = UAC1, 0x0200 = UAC2. */
    uint16_t bcdADC;
    int uacVersion; // 1 or 2 for readability

    /** Interface numbers on the device. */
    uint8_t acInterfaceNumber;
    uint8_t asInterfaceNumber;

    UacInputTerminal inputTerminals[UAC_MAX_TERMINALS];
    int inputTerminalCount;

    UacOutputTerminal outputTerminals[UAC_MAX_TERMINALS];
    int outputTerminalCount;

    UacFeatureUnit featureUnits[UAC_MAX_FEATURE_UNITS];
    int featureUnitCount;

    UacClockSource clockSources[UAC_MAX_CLOCK_SOURCES];
    int clockSourceCount;

    UacAltSetting altSettings[UAC_MAX_ALT_SETTINGS];
    int altSettingCount;
};

/**
 * What the DSP engine wants the DAC to do. Filled in by the Kotlin side
 * (or defaulted by the negotiator) and passed into format negotiation.
 */
struct UacFormatRequest {
    uint32_t sampleRate; // e.g. 48000, 96000
    uint8_t bitDepth;    // e.g. 16, 24, 32
    uint8_t channels;    // e.g. 2
};
