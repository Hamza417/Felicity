package app.simple.felicity.engine.usb

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.os.Build
import android.util.Log
import app.simple.felicity.engine.usb.UsbDacDriver.Companion.MAX_ATTENUATION_DB
import kotlin.math.pow

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

    /** Guards against double-registration of [permissionReceiver]. */
    private var isAttached = false

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
     * Call this once when the service is created. Safe to call multiple times — only
     * the first call actually registers the receiver.
     */
    fun attach() {
        if (isAttached) {
            Log.d(TAG, "UsbDacDriver already attached, skipping duplicate registration")
            return
        }
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(permissionReceiver, filter)
        }
        isAttached = true
        Log.d(TAG, "UsbDacDriver attached")
    }

    /**
     * Unregisters the permission receiver and releases any open USB connection.
     * Call this when the service is destroyed.
     */
    fun detach() {
        isAttached = false
        runCatching { context.unregisterReceiver(permissionReceiver) }
        UsbDacManager.notifyDetached()
        nativeStopStream()
        releaseConnection()
        Log.d(TAG, "UsbDacDriver detached")
    }

    /**
     * Pulses the USB subsystem at app launch to discover any DAC that was already
     * plugged in before the app process started. Without this, a DAC that was
     * connected before launch is invisible because Android only sends the
     * ACTION_USB_DEVICE_ATTACHED broadcast when a device is physically plugged in
     * *while* a registered receiver exists.
     *
     * Call this once from [MainActivity] after the driver is attached. If an audio
     * device is found it follows the normal permission→open→stream flow. If nothing
     * is found a single log line is emitted so the silence is not mysterious.
     */
    fun checkForExistingDac() {
        // The permission receiver must be registered before we request permission,
        // otherwise the user grant lands in a black hole.
        if (!isAttached) {
            Log.w(TAG, "checkForExistingDac called before attach() — calling attach() now")
            attach()
        }

        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = usbManager.deviceList

        if (devices.isEmpty()) {
            Log.i(TAG, "No USB devices connected at app launch — DAC pulse complete")
            return
        }

        Log.d(TAG, "DAC pulse: scanning ${devices.size} connected USB device(s)")

        var foundAudioDevice = false
        for ((_, device) in devices) {
            if (isAudioDevice(device)) {
                foundAudioDevice = true
                Log.i(TAG, "DAC pulse: found pre-connected audio device — " +
                        "VID=0x${device.vendorId.toString(16).uppercase()} " +
                        "PID=0x${device.productId.toString(16).uppercase()} " +
                        "name=${device.deviceName}")
                onDeviceAttached(device)
                break // Only handle the first audio device found
            }
        }

        if (!foundAudioDevice) {
            Log.i(TAG, "DAC pulse complete — no USB audio device found among ${devices.size} connected device(s)")
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
        // negotiateFormat() also syncs the DAC hardware volume to the current system
        // volume so the user does not get blasted at 100% when plugging in a DAC.
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

    /**
     * Re-runs format negotiation on the currently open DAC with the given parameters,
     * then restarts the isochronous stream in the new format.
     * Safe to call at any time after a successful [onDeviceAttached]; returns silently
     * if no device is connected. Useful when the user changes the DSP output sample
     * rate or bit depth in the app settings.
     *
     * @param sampleRate Target sample rate in Hz (e.g. 48000, 96000).
     * @param bitDepth   Desired bit depth (e.g. 16, 24, 32).
     * @param channels   Number of output channels.
     */
    fun negotiateFormat(sampleRate: Int, bitDepth: Int, channels: Int) {
        if (connection == null) {
            Log.d(TAG, "negotiateFormat skipped — no DAC connected")
            return
        }
        Log.i(TAG, "Negotiating format: $sampleRate Hz / ${bitDepth}-bit / $channels ch")

        // Stop the current stream before changing the interface alt-setting.
        // Sending control transfers to the device while isochronous transfers are
        // in-flight can confuse some DAC firmware.
        nativeStopStream()

        val ok = nativeNegotiateFormat(sampleRate, bitDepth, channels)
        if (ok) {
            Log.i(TAG, "Format negotiation succeeded — call startStream() to begin playback")
            // The native negotiator resets volume to unity (0 dB). Re-apply the
            // current system volume so the DAC does not suddenly blast at 100%.
            syncSystemVolumeToDac()
        } else {
            Log.w(TAG, "Format negotiation failed — DAC may use a fallback format")
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

    /**
     * Discards any audio sitting in the ring buffer without tearing down the
     * isochronous USB pipeline. The pipeline keeps running and plays silence
     * until the DSP delivers fresh audio, so there is no audible gap.
     *
     * Use this instead of [stopStream] + [startStream] on seeks and
     * ExoPlayer flush events — it is orders of magnitude cheaper.
     */
    fun flushRingBuffer() {
        if (connection != null) {
            nativeFlushStream()
        }
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
     * Clears stale audio from the ring buffer without stopping the isochronous
     * pipeline. The pipeline keeps sending packets (silence until new audio arrives)
     * so there is no audible gap on seeks or format discontinuities.
     */
    private external fun nativeFlushStream()

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
     * Sends a volume control transfer to the DAC's Feature Unit in 1/256 dB steps.
     * 0x0000 = 0 dB (unity gain), negative values = attenuation.
     *
     * This is a best-effort call — many DACs with physical volume knobs ignore
     * software volume requests. Failure is logged but playback continues normally.
     *
     * @param volumeDb256 Signed 16-bit value in Q8.8 fixed-point (1/256 dB steps).
     */
    private external fun nativeSetVolume(volumeDb256: Short)

    /**
     * Public wrapper around [nativeSetVolume] that guards against calls when no
     * DAC is connected. Safe to call from any thread at any time.
     *
     * @param volumeDb256 Signed 16-bit value in 1/256 dB steps (Q8.8).
     */
    fun setHardwareVolume(volumeDb256: Short) {
        if (connection != null) {
            nativeSetVolume(volumeDb256)
        }
    }

    /**
     * Reads the current system music stream volume from [AudioManager] and pushes
     * the equivalent attenuation to the DAC's Feature Unit so the DAC output level
     * tracks the system volume.
     *
     * Call this whenever the system volume may have changed — on DAC attach, after
     * volume button presses, and on app startup.
     */
    fun syncSystemVolumeToDac() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumeDb256 = mapSystemVolumeToDacVolume(currentVolume, maxVolume)
        val approxDb = volumeDb256.toFloat() / 256f
        Log.d(TAG, "Syncing system volume ($currentVolume/$maxVolume) → DAC ${volumeDb256}/256 dB (≈ ${"%.1f".format(approxDb)} dB)")
        setHardwareVolume(volumeDb256)

        // Always update the software gain as well. For DACs that support hardware
        // volume this is harmless (gain stays at 1.0). For DACs without hardware
        // volume control, this is the fallback that actually makes volume work.
        softwareVolumeGain = mapSystemVolumeToLinearGain(currentVolume, maxVolume)
    }

    /**
     * Linear gain factor applied to PCM samples in the DSP thread before they are
     * pushed to the USB DAC. Ranges from ~0.001 (−60 dB, near-silent) to 1.0 (unity).
     *
     * Updated by [syncSystemVolumeToDac] and read by the audio sink's [handleBuffer]
     * on the ExoPlayer render thread. Marked [Volatile] so a write on the main thread
     * is eventually visible to the audio thread without heavyweight synchronization.
     */
    @Volatile
    var softwareVolumeGain: Float = 1f

    /** Tells the native layer to stop the stream, release libusb, and exit. */
    private external fun nativeReleaseUsb()

    companion object {
        private const val TAG = "UsbDacDriver"
        private const val ACTION_USB_PERMISSION = "app.simple.felicity.USB_PERMISSION"

        /**
         * Maximum attenuation in dB to apply at the lowest system volume step.
         * -60 dB is effectively silent for most practical listening scenarios while
         * still leaving enough headroom for the DAC's internal amp to operate.
         */
        private const val MAX_ATTENUATION_DB = -60f

        /**
         * Maps an Android system volume index to a UAC hardware volume value in
         * 1/256 dB steps (Q8.8 fixed-point).
         *
         * The mapping is linear across the system volume range:
         *   - max system volume → 0 dB (0x0000, unity gain)
         *   - min system volume → [MAX_ATTENUATION_DB] dB
         *
         * Android's own volume curve already applies logarithmic scaling to the
         * step indices, so a linear mapping here produces a natural-sounding ramp
         * without double-log compensation.
         *
         * @param current Current stream volume index (0..max).
         * @param max     Maximum stream volume index.
         * @return Signed 16-bit Q8.8 volume value for the DAC Feature Unit.
         */
        fun mapSystemVolumeToDacVolume(current: Int, max: Int): Short {
            if (max <= 0 || current <= 0) return (MAX_ATTENUATION_DB * 256f).toInt().toShort()
            if (current >= max) return 0 // 0 dB = unity

            val fraction = current.toFloat() / max.toFloat()
            // Linear mapping: fraction 0→1 maps to attenuation MAX_ATTENUATION_DB→0 dB
            val db = MAX_ATTENUATION_DB * (1f - fraction)
            val db256 = (db * 256f).toInt()
            return db256.toShort()
        }

        /**
         * Maps an Android system volume index to a linear gain multiplier for
         * software volume control. Used as a fallback when the DAC does not support
         * hardware (UAC Feature Unit) volume control.
         *
         * The mapping converts the linear volume fraction to dB attenuation, then
         * to a linear gain using gain = 10^(dB/20). This produces a logarithmic
         * taper that matches human loudness perception:
         *   - max system volume → 0 dB → gain = 1.0 (no change)
         *   - 50% system volume → −30 dB → gain ≈ 0.032
         *   - min system volume → −60 dB → gain ≈ 0.001 (near-silent)
         *
         * @param current Current stream volume index (0..max).
         * @param max     Maximum stream volume index.
         * @return Linear gain multiplier in the range (0.0, 1.0].
         */
        fun mapSystemVolumeToLinearGain(current: Int, max: Int): Float {
            if (max <= 0 || current <= 0) return Math.pow(10.0, MAX_ATTENUATION_DB / 20.0).toFloat()
            if (current >= max) return 1f

            val fraction = current.toFloat() / max.toFloat()
            val db = MAX_ATTENUATION_DB * (1f - fraction)
            return 10.0.pow(db.toDouble() / 20.0).toFloat()
        }

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

