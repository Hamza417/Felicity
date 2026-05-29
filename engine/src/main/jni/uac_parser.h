#pragma once

#include "uac_descriptors.h"
#include <libusb.h>
#include <stdbool.h>

/**
 * Reads the device and configuration descriptors from the already-open libusb handle
 * and fills [info] with every UAC entity the driver needs to know about:
 * terminals, feature units, clock sources (UAC2), and audio streaming alt-settings.
 *
 * Returns true on success. Logs detailed diagnostics at every step so the full
 * capability map shows up in logcat for easy debugging.
 *
 * @author Hamza417
 */
bool uac_parse_device(libusb_device_handle *handle, UacDeviceInfo *info);

