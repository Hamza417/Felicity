package app.simple.felicity.engine.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

/**
 * Listens for USB devices being plugged in or removed and delegates the relevant
 * events to [UsbDacDriver] so the driver can request access and initialize the DAC.
 *
 * Android delivers [UsbManager.ACTION_USB_DEVICE_ATTACHED] as a system broadcast the
 * moment a USB device is connected. We check every interface on that device; if any
 * interface belongs to the USB audio class we hand the device off to [UsbDacDriver].
 *
 * @author Hamza417
 */
class UsbDacReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
        device ?: return

        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                Log.d(TAG, "USB device attached — VID=0x${device.vendorId.toString(16).uppercase()} " +
                        "PID=0x${device.productId.toString(16).uppercase()} name=${device.deviceName}")

                if (isAudioDevice(device)) {
                    Log.i(TAG, "USB audio class device detected, requesting access…")
                    UsbDacDriver.getInstance(context).onDeviceAttached(device)
                } else {
                    Log.d(TAG, "Device is not USB audio class, ignoring.")
                }
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                Log.i(TAG, "USB device detached — VID=0x${device.vendorId.toString(16).uppercase()} " +
                        "PID=0x${device.productId.toString(16).uppercase()}")
                UsbDacDriver.getInstance(context).onDeviceDetached(device)
            }
        }
    }

    /**
     * Returns true when at least one interface on this USB device belongs to the audio
     * class (class code 0x01 per the USB spec). We scan all interfaces rather than relying
     * solely on the top-level device class because some DACs set the device class to
     * "Miscellaneous" (0xEF) and only expose the audio class at the interface level.
     */
    private fun isAudioDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_AUDIO) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val TAG = "UsbDacReceiver"
    }
}

