package app.simple.felicity.engine.usb

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

/**
 * Manages the full lifecycle of a USB audio DAC from the Android side: asking the user
 * for permission, opening an exclusive connection, extracting the raw file descriptor,
 * and handing everything off to the native layer where libusb takes over.
 *
 * Call [attach] when the service starts to register for runtime permission results, and
 * [detach] when the service stops to avoid memory leaks. The actual USB work begins in
 * [onDeviceAttached], which is called by [UsbDacReceiver] whenever a USB audio device
 * is plugged in.
 *
 * @author Hamza417
 */
class UsbDacDriver private constructor(private val context: Context) {

    /** The active USB connection to the DAC. Null when no DAC is connected. */
    private var connection: UsbDeviceConnection? = null

    /** The USB device that is currently connected and initialized. */
    private var activeDevice: UsbDevice? = null

    /**
     * These three fields remember the last format we successfully programmed on
     * the DAC. When [negotiateFormat] is called with the same values we skip the
     * full stop/reprogram/restart cycle, which prevents the ring buffer from
     * being unnecessarily wiped while audio is playing.
     */
    private var lastNegotiatedSampleRate: Int = 0
    private var lastNegotiatedBitDepth: Int = 0
    private var lastNegotiatedChannels: Int = 0

    /**
     * Receives the result of [UsbManager.requestPermission]. Android delivers this
     * as a broadcast with a boolean extra telling us whether the user said yes or no.
     */
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) throw IllegalStateException("Unexpected intent action: ${intent.action}")

            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }

            device ?: throw IllegalStateException("USB permission result received with no device")
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

            if (granted) {
                Log.i(TAG, "USB permission granted for ${device.deviceName}")
                openDeviceAndInitNative(device)
            } else {
                Log.w(TAG, "USB permission denied for ${device.deviceName}")
            }
        }
    }

    /**
     * Registers the permission result receiver so we can hear back from Android after
     * the user dismisses the "Allow Felicity to access the USB device?" dialog.
     * Call this once when the service is created.
     */
    fun attach() {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(permissionReceiver, filter)
        }
        Log.d(TAG, "UsbDacDriver attached")
    }

    /**
     * Unregisters the permission receiver and releases any open USB connection.
     * Call this when the service is destroyed.
     */
    fun detach() {
        runCatching { context.unregisterReceiver(permissionReceiver) }
        UsbDacManager.notifyDetached()
        nativeStopStream()
        releaseConnection()
        resetNegotiatedFormat()
        Log.d(TAG, "UsbDacDriver detached")
    }

    /**
     * Called by [UsbDacReceiver] when a USB audio device has been plugged in.
     * If we already have permission we open the device immediately; otherwise we
     * show the system dialog and wait for [permissionReceiver] to fire.
     */
    fun onDeviceAttached(device: UsbDevice) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        if (usbManager.hasPermission(device)) {
            Log.d(TAG, "Permission already held for ${device.deviceName}, opening immediately")
            openDeviceAndInitNative(device)
        } else {
            val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            Log.d(TAG, "Requesting USB permission for ${device.deviceName}")
            usbManager.requestPermission(device, pendingIntent)
        }
    }

    /**
     * Called by [UsbDacReceiver] when the USB device is physically removed.
     * We stop the stream and release the connection so the native layer can
     * clean up libusb resources before the file descriptor becomes invalid.
     */
    fun onDeviceDetached(device: UsbDevice) {
        if (activeDevice?.deviceId == device.deviceId) {
            Log.i(TAG, "Active DAC unplugged — halting stream and releasing resources")
            UsbDacManager.notifyDetached()
            nativeStopStream()
            nativeReleaseUsb()
            releaseConnection()
            resetNegotiatedFormat()
        }
    }

    /**
     * Opens an exclusive connection to the device via [UsbManager.openDevice], extracts
     * the raw Linux file descriptor from that connection, and passes the FD together with
     * the vendor ID and product ID across the JNI boundary so libusb can take ownership.
     */
    private fun openDeviceAndInitNative(device: UsbDevice) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        // Release any previous connection before opening a new one so we do not
        // accidentally hold two file descriptors to the same USB device.
        releaseConnection()

        val conn = usbManager.openDevice(device)
        if (conn == null) {
            Log.e(TAG, "Failed to open USB device ${device.deviceName}")
            return
        }

        connection = conn
        activeDevice = device

        // The raw file descriptor is the bridge between the Android framework and libusb.
        // libusb_wrap_sys_device() will use this FD to control the USB device in user-space.
        val fd = conn.fileDescriptor
        val vendorId = device.vendorId
        val productId = device.productId

        Log.i(TAG, "Handing off to native layer — FD=$fd VID=0x${vendorId.toString(16).uppercase()} " +
                "PID=0x${productId.toString(16).uppercase()}")

        val success = nativeInitUsb(fd, vendorId, productId)
        if (!success) {
            Log.e(TAG, "Native USB initialization failed, releasing connection")
            releaseConnection()
            return
        }

        // With the device claimed, immediately negotiate the format.
        // We default to 24-bit / 96 kHz stereo — the negotiator will fall back to
        // the best available alt-setting if the device does not support that exactly.
        negotiateFormat(sampleRate = 96_000, bitDepth = 24, channels = 2)

        // Notify the audio sink and service that the DAC is live. They will
        // re-negotiate the format to match whatever ExoPlayer is currently playing.
        UsbDacManager.notifyAttached(96_000, 2)
    }

    /** Closes and clears the current [UsbDeviceConnection]. */
    private fun releaseConnection() {
        connection?.close()
        connection = null
        activeDevice = null
    }

    /** Resets the cached negotiated format so the next [negotiateFormat] call always re-programs the DAC. */
    private fun resetNegotiatedFormat() {
        lastNegotiatedSampleRate = 0
        lastNegotiatedBitDepth = 0
        lastNegotiatedChannels = 0
    }

    /**
     * Re-runs format negotiation on the currently open DAC with the given parameters,
     * then restarts the isochronous stream in the new format.
     * Safe to call at any time after a successful [onDeviceAttached]; returns silently
     * if no device is connected. Useful when the user changes the DSP output sample
     * rate or bit depth in the app settings.
     *
     * If the format is unchanged since the last successful negotiation, this call
     * is a no-op and returns `false` — no stop/restart happens and the stream keeps
     * running uninterrupted.
     *
     * @param sampleRate Target sample rate in Hz (e.g. 48000, 96000).
     * @param bitDepth   Desired bit depth (e.g. 16, 24, 32).
     * @param channels   Number of output channels.
     * @return `true` when the DAC was re-programmed and a [startStream] call is now
     *         needed; `false` when the format was already in place and no restart is required.
     */
    fun negotiateFormat(sampleRate: Int, bitDepth: Int, channels: Int): Boolean {
        if (connection == null) {
            Log.d(TAG, "negotiateFormat skipped — no DAC connected")
            return false
        }

        // Skip the full stop/reprogram cycle when nothing actually changed.
        // This is the common case when ExoPlayer calls configure() between buffers
        // with the same format — without this guard, every configure call would wipe
        // the ring buffer and restart the stream, causing persistent underruns.
        if (sampleRate == lastNegotiatedSampleRate &&
                bitDepth == lastNegotiatedBitDepth &&
                channels == lastNegotiatedChannels) {
            Log.d(TAG, "Format unchanged ($sampleRate Hz / ${bitDepth}-bit / $channels ch) — skipping re-negotiation")
            return false
        }

        Log.i(TAG, "Negotiating format: $sampleRate Hz / ${bitDepth}-bit / $channels ch")

        // Stop the current stream before changing the interface alt-setting.
        // Sending control transfers to the device while isochronous transfers are
        // in-flight can confuse some DAC firmware.
        nativeStopStream()

        val ok = nativeNegotiateFormat(sampleRate, bitDepth, channels)
        if (ok) {
            lastNegotiatedSampleRate = sampleRate
            lastNegotiatedBitDepth = bitDepth
            lastNegotiatedChannels = channels
            Log.i(TAG, "Format negotiation succeeded — call startStream() to begin playback")
        } else {
            Log.w(TAG, "Format negotiation failed — DAC may use a fallback format")
        }

        // Return true regardless of whether nativeNegotiateFormat succeeded — the
        // stream was stopped so the caller needs to restart it either way.
        return true
    }

    /**
     * Returns true when the isochronous USB stream is currently live and pushing audio.
     * Used by the audio sink to skip a redundant [startStream] call after a simple
     * pause/resume where the stream was intentionally kept alive.
     */
    fun isStreamRunning(): Boolean {
        return connection != null && nativeIsStreamRunning()
    }

    /**
     * Discards all pending audio samples from the internal ring buffer without
     * stopping or restarting the isochronous USB stream.
     *
     * Call this during seeks and track transitions instead of a full stop/restart
     * cycle. In-flight USB transfers will briefly output zeros while the DSP thread
     * refills the buffer, which is far less disruptive than the audible gap caused
     * by tearing down and rebuilding the whole USB pipeline.
     */
    fun flushRingBuffer() {
        if (connection != null) {
            nativeFlushRingBuffer()
        }
    }

    /**
     * Starts the isochronous USB stream if a DAC is currently connected.
     * Safe to call at any time; silently no-ops when no device is open.
     * The audio sink calls this when playback begins or resumes.
     */
    fun startStream() {
        if (connection != null) {
            nativeStartStream()
        }
    }

    /**
     * Stops the isochronous USB stream. Idempotent — safe to call even if the
     * stream was never started or the DAC has already been detached.
     * The audio sink calls this when playback is paused or flushed.
     */
    fun stopStream() {
        nativeStopStream()
    }

    // JNI declarations — implemented in felicity_usb_dac.cpp

    /**
     * Hands the raw file descriptor and device identifiers to libusb so it can wrap
     * the existing kernel FD into its own device handle, claim the audio streaming
     * interface, and detach any kernel driver that may be sitting on top of it.
     *
     * @param fileDescriptor The raw FD obtained from [UsbDeviceConnection.getFileDescriptor].
     * @param vendorId       The USB vendor ID (VID) of the DAC.
     * @param productId      The USB product ID (PID) of the DAC.
     * @return `true` if libusb initialization succeeded and the interface was claimed.
     */
    private external fun nativeInitUsb(fileDescriptor: Int, vendorId: Int, productId: Int): Boolean

    /**
     * Runs the UAC descriptor parser on the open device, then selects the best
     * matching alt-setting and programs the DAC's clock and mute state to match.
     *
     * @param sampleRate Target sample rate in Hz (e.g. 48000, 96000).
     * @param bitDepth   Desired bit depth (e.g. 16, 24, 32).
     * @param channels   Number of output channels (1 = mono, 2 = stereo).
     * @return `true` if format negotiation succeeded and the DAC is ready to stream.
     */
    private external fun nativeNegotiateFormat(sampleRate: Int, bitDepth: Int, channels: Int): Boolean

    /**
     * Allocates the isochronous URB pool and spawns the high-priority USB event
     * thread. Must be called after [nativeNegotiateFormat] succeeds.
     */
    private external fun nativeStartStream(): Boolean

    /**
     * Cancels all pending isochronous transfers, joins the event thread, and frees
     * the URB pool. Idempotent — safe to call even if the stream was never started.
     */
    private external fun nativeStopStream()

    /**
     * Pushes interleaved float PCM into the ring buffer that feeds the isochronous
     * USB pipeline. Call this from the DSP output thread.
     *
     * @param samples Interleaved float samples in [-1.0, 1.0].
     * @param offset  Starting index within [samples].
     * @param count   Number of floats to push (frames × channels).
     * @return Number of samples actually accepted.
     */
    external fun nativePushPcm(samples: FloatArray, offset: Int, count: Int): Int

    /**
     * Discards pending samples from the ring buffer without touching the
     * isochronous stream. Safe to call from any thread while the stream is running.
     */
    private external fun nativeFlushRingBuffer()

    /** Tells the native layer to stop the stream, release libusb, and exit. */
    private external fun nativeReleaseUsb()

    /**
     * Returns true when the native isochronous stream is currently running.
     * Fast — just reads an atomic flag, no USB I/O.
     */
    private external fun nativeIsStreamRunning(): Boolean

    companion object {
        private const val TAG = "UsbDacDriver"
        private const val ACTION_USB_PERMISSION = "app.simple.felicity.USB_PERMISSION"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: UsbDacDriver? = null

        /** Returns the process-wide singleton, creating it on first call. */
        fun getInstance(context: Context): UsbDacDriver {
            return instance ?: synchronized(this) {
                instance ?: UsbDacDriver(context.applicationContext).also { instance = it }
            }
        }

        init {
            System.loadLibrary("felicity_usb_dac")
        }
    }
}

