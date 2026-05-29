#pragma once

#include "uac_descriptors.h"
#include <libusb.h>

/**
 * Given a fully-parsed [UacDeviceInfo] and a desired output format, this module
 * performs the three-step hardware handshake that locks the DAC into a known state:
 *
 *   1. Pick the best matching alt-setting and activate it on the USB interface.
 *   2. Program the DAC's internal clock to run at exactly the requested sample rate.
 *   3. Confirm the clock has locked and turn off the mute on the output path.
 *
 * Returns the index (into [UacDeviceInfo::altSettings]) of the alt-setting that was
 * activated, or -1 on failure. The caller should store this index and pass the
 * corresponding [UacAltSetting] to the isochronous stream so it uses the exact
 * endpoint address and packet parameters for the negotiated format.
 *
 * @author Hamza417
 */
int uac_negotiate_format(libusb_device_handle *handle,
                         const UacDeviceInfo *info,
                         const UacFormatRequest &request);

/**
 * Attempts to set the volume on the first Feature Unit that reports volume control
 * to the given linear gain in 1/256 dB steps (UAC1: signed Q8.8; UAC2: signed Q7.8).
 * Pass 0x0000 for 0 dB (unity gain).
 *
 * This is a best-effort call — many DACs ignore software volume requests when
 * hardware volume knobs are present. Failure is logged but does not abort playback.
 */
void uac_set_volume(libusb_device_handle *handle,
                    const UacDeviceInfo *info,
                    int16_t volumeDb256);
