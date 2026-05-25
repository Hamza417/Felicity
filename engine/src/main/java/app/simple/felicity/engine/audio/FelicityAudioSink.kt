package app.simple.felicity.engine.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import app.simple.felicity.engine.processors.AaudioOutputProcessor
import app.simple.felicity.engine.usb.UsbDacDriver
import app.simple.felicity.engine.usb.UsbDacManager
import app.simple.felicity.engine.utils.PcmUtils
import app.simple.felicity.preferences.AudioPreferences
import java.nio.ByteBuffer

/**
 * An [androidx.media3.exoplayer.audio.AudioSink] that routes fully-processed float PCM
 * through the Android AAudio direct-to-HAL path when [AudioPreferences.AAUDIO_ENABLED]
 * is on, while keeping the inner [DefaultAudioSink] alive (muted) for ExoPlayer's clock
 * and state machine.
 *
 * **USB DAC direct output**: When a USB audio DAC is connected and [UsbDacManager.isActive]
 * is true, this sink acts as an exclusive splitter: the [DefaultAudioSink] is kept muted
 * (for clock), AAudio is bypassed entirely, and all float PCM is pushed directly into the
 * USB isochronous ring buffer via [UsbDacDriver.nativePushPcm]. This avoids double-output
 * to the DAC that would otherwise happen because Android's audio routing also directs
 * AudioTrack data to the USB device.
 *
 * **Output priority (exclusive):**
 *   1. USB DAC direct (when [UsbDacManager.isActive] — highest fidelity, bypasses Android mixer)
 *   2. AAudio direct-to-HAL (when [AudioPreferences.isAaudioEnabled] and USB DAC not active)
 *   3. DefaultAudioSink / AudioTrack (fallback)
 *
 * **Format sync**: [configure] is the source of truth for the current ExoPlayer format.
 * When a USB DAC attaches mid-session, the [UsbDacManager.Listener] receives the attach
 * event and immediately re-negotiates the DAC to match the live ExoPlayer sample rate,
 * bit depth, and channel count so there is never a rate mismatch between what the DSP
 * outputs and what the DAC expects.
 *
 * @param delegate  The [DefaultAudioSink] owned by the ExoPlayer renderer.
 * @param context   Application context used to query [AudioManager] (Bluetooth detection)
 *                  and to reach [UsbDacDriver] for PCM push calls.
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class FelicityAudioSink(
        private val delegate: DefaultAudioSink,
        private val context: Context
) : ForwardingAudioSink(delegate) {

    /** Native AAudio stream; null when AAudio is disabled or not yet configured. */
    private var aaudioStream: AaudioOutputProcessor? = null

    /** PCM encoding of the most recently configured format. */
    private var currentEncoding: Int = C.ENCODING_PCM_16BIT

    /** Sample rate of the most recently configured format, in Hz. */
    private var currentSampleRate: Int = 0

    /** Channel count of the most recently configured format. */
    private var currentChannelCount: Int = 0

    /**
     * Volume last requested by ExoPlayer. Stored so the AudioTrack volume can be
     * unmuted correctly if AAudio is later disabled between configure calls.
     */
    private var pendingVolume: Float = 1f

    /**
     * Tracks whether the delegate [DefaultAudioSink] is currently muted (volume = 0)
     * because AAudio or the USB DAC is producing the audible output.
     */
    private var delegateMuted: Boolean = false

    /**
     * Registered with [UsbDacManager] so we hear about DAC connect/disconnect events.
     * On attach we immediately re-negotiate the USB DAC format to match whatever ExoPlayer
     * is currently configured for — this handles the case where the DAC is plugged in
     * while a song is already playing at a different rate than the DAC's default 96 kHz.
     * On detach we restore the delegate volume so normal output resumes.
     */
    private val usbDacListener = object : UsbDacManager.Listener {
        override fun onUsbDacAttached(sampleRate: Int, channelCount: Int) {
            // If ExoPlayer has already configured us with a valid format, sync the
            // USB DAC to it immediately so there is no sample-rate mismatch.
            if (currentSampleRate > 0 && currentChannelCount > 0) {
                val bitDepth = encodingToBitDepth(currentEncoding)
                Log.i(TAG, "USB DAC attached mid-stream — re-negotiating to " +
                        "$currentSampleRate Hz / ${bitDepth}-bit / $currentChannelCount ch")
                UsbDacDriver.getInstance(context)
                    .negotiateFormat(currentSampleRate, bitDepth, currentChannelCount)
            }
            // Mute the delegate so Android's AudioTrack → USB routing path produces silence
            // while our direct USB pipeline carries the real audio.
            muteDelegateIfNeeded()
        }

        override fun onUsbDacDetached() {
            // Restore normal delegate output now that the direct USB path is gone.
            // If AAudio is still enabled and its stream is ready, keep the delegate muted
            // so we don't accidentally unmute while AAudio is still driving output.
            val aaudioStillActive = AudioPreferences.isAaudioEnabled() && aaudioStream?.isReady == true
            if (!aaudioStillActive) {
                unmuteDelegateIfNeeded()
            }
            Log.i(TAG, "USB DAC detached — reverted to ${if (aaudioStillActive) "AAudio" else "AudioTrack"} output")
        }
    }

    init {
        UsbDacManager.addListener(usbDacListener)
    }

    /**
     * Configures the sink for [inputFormat]. Always delegates to [DefaultAudioSink],
     * then — when [AudioPreferences.isAaudioEnabled] is true and no USB DAC is active —
     * creates or recreates the native AAudio stream.
     *
     * If a USB DAC is already active when [configure] is called (e.g. after an ExoPlayer
     * format switch), the DAC is re-negotiated so its alt-setting matches the new rate.
     */
    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        super.configure(inputFormat, specifiedBufferSize, outputChannels)

        val enc = inputFormat.pcmEncoding
        if (enc != Format.NO_VALUE) {
            currentEncoding = enc
        }
        val sr = inputFormat.sampleRate.takeIf { it > 0 } ?: return
        val ch = inputFormat.channelCount.takeIf { it > 0 } ?: return

        // Capture old values before we overwrite them so the format-change check below
        // can detect whether a new AAudio stream or DAC re-negotiation is actually needed.
        val prevSampleRate = currentSampleRate
        val prevChannelCount = currentChannelCount
        currentSampleRate = sr
        currentChannelCount = ch

        // When USB DAC is active, keep AAudio dormant to avoid double output.
        // Re-negotiate the DAC format if ExoPlayer switched to a new sample rate.
        if (UsbDacManager.isActive) {
            if (aaudioStream != null) releaseAaudioStream()
            val bitDepth = encodingToBitDepth(currentEncoding)
            Log.i(TAG, "USB DAC active — syncing DAC format to $sr Hz / ${bitDepth}-bit / $ch ch")
            UsbDacDriver.getInstance(context).negotiateFormat(sr, bitDepth, ch)
            muteDelegateIfNeeded()
            return
        }

        if (!AudioPreferences.isAaudioEnabled()) {
            if (aaudioStream != null) {
                releaseAaudioStream()
            }
            return
        }

        if (sr != prevSampleRate || ch != prevChannelCount || aaudioStream?.isReady != true) {
            releaseAaudioStream()

            val useSafeBuffers = isBluetoothOutputActive()
            if (useSafeBuffers) {
                Log.i(TAG, "Bluetooth output detected — opening AAudio stream in safe-buffer mode")
            }

            val stream = AaudioOutputProcessor(sr, ch, useSafeBuffers)
            if (stream.isReady) {
                aaudioStream = stream
                isAAudioStreamActive = true
                muteDelegateIfNeeded()
                Log.i(TAG, "AAudio stream configured — sampleRate=$sr, channels=$ch, " +
                        "encoding=$enc, actualFormat=${stream.getActualFormatName()}, " +
                        "safeBuffers=$useSafeBuffers")
            } else {
                Log.e(TAG, "AAudio stream creation failed for sampleRate=$sr, channels=$ch")
                isAAudioStreamActive = false
                unmuteDelegateIfNeeded()
            }
        }
    }

    /** Starts the delegate and, when AAudio is enabled (and USB DAC is not active), starts the native stream. */
    override fun play() {
        super.play()
        if (UsbDacManager.isActive) {
            muteDelegateIfNeeded()
            return
        }
        if (AudioPreferences.isAaudioEnabled()) {
            muteDelegateIfNeeded()
            aaudioStream?.start()
        }
    }

    /** Pauses the delegate and stops the native stream. */
    override fun pause() {
        super.pause()
        aaudioStream?.stop()
    }

    /**
     * Routes the PCM buffer through [DefaultAudioSink] (for clock / state management) and
     * — depending on the active output path — also pushes the same data to either the
     * native AAudio stream or the USB DAC ring buffer.
     *
     * Only one external output is active at a time:
     *   - USB DAC active   → push to USB ring buffer; AAudio bypassed; delegate muted.
     *   - AAudio active    → push to AAudio stream; delegate muted.
     *   - Neither active   → delegate drives AudioTrack output directly (unmuted).
     *
     * The buffer snapshot is taken BEFORE delegation to preserve the readable region
     * regardless of how [DefaultAudioSink] advances the position internally.
     * Its byte order is restored to match ExoPlayer's little-endian convention so
     * float / short / int reads in the conversion path are always correct.
     */
    override fun handleBuffer(
            buffer: ByteBuffer,
            presentationTimeUs: Long,
            encodedAccessUnitCount: Int
    ): Boolean {
        val usbActive = UsbDacManager.isActive
        val aaudioReady = AudioPreferences.isAaudioEnabled() && aaudioStream?.isReady == true

        if (!usbActive && !aaudioReady) {
            unmuteDelegateIfNeeded()
            return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }

        muteDelegateIfNeeded()

        // Preserve the readable slice — same byte-order fix as before.
        val snapshot: ByteBuffer = buffer.slice().order(buffer.order())
        val consumed = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)

        if (consumed) {
            // Convert once; push to whichever output is live.
            val floatBuf = snapshotToFloat(snapshot)
            if (usbActive) {
                UsbDacDriver.getInstance(context).nativePushPcm(floatBuf, 0, floatBuf.size)
            } else {
                aaudioStream?.write(floatBuf)
            }
        }

        return consumed
    }

    /**
     * Updates the muting decision when ExoPlayer changes the stream volume. USB DAC and
     * AAudio both require the delegate to stay muted while they are the active output.
     */
    override fun setVolume(volume: Float) {
        pendingVolume = volume
        val exclusiveOutputActive = UsbDacManager.isActive ||
                (AudioPreferences.isAaudioEnabled() && aaudioStream?.isReady == true)
        if (exclusiveOutputActive) {
            muteDelegateIfNeeded()
        } else {
            unmuteDelegateIfNeeded()
        }
    }

    /**
     * Flushes the delegate and restarts the AAudio stream (if active) to clear in-flight
     * frames on seeks and discontinuities. USB ring buffer is NOT flushed here because the
     * native isochronous pipeline will drain the stale bytes as silence before new data
     * arrives — abruptly flushing mid-transfer is more disruptive than letting it drain.
     */
    override fun flush() {
        super.flush()
        aaudioStream?.apply {
            stop()
            start()
        }
    }

    /** Resets the delegate and releases the native AAudio stream. */
    override fun reset() {
        super.reset()
        releaseAaudioStream()
    }

    /**
     * Releases all resources. Unregisters from [UsbDacManager] so this instance does not
     * receive callbacks after the ExoPlayer session ends.
     */
    override fun release() {
        UsbDacManager.removeListener(usbDacListener)
        releaseAaudioStream()
        super.release()
    }

    /**
     * Converts the PCM content of [snapshot] to an interleaved [FloatArray].
     *
     * For [C.ENCODING_PCM_FLOAT] input, uses a single bulk [java.nio.FloatBuffer.get] call.
     * For all other encodings, [PcmUtils.readFloat] converts each sample individually.
     * Both paths produce values nominally in [-1.0, 1.0] (float PCM may exceed this with headroom).
     */
    private fun snapshotToFloat(snapshot: ByteBuffer): FloatArray {
        val bps = PcmUtils.bytesPerSample(currentEncoding)
        val totalSamples = snapshot.remaining() / bps
        if (totalSamples <= 0) return FloatArray(0)

        val floatBuf = FloatArray(totalSamples)
        if (currentEncoding == C.ENCODING_PCM_FLOAT) {
            snapshot.asFloatBuffer().get(floatBuf)
        } else {
            for (i in 0 until totalSamples) {
                floatBuf[i] = PcmUtils.readFloat(snapshot, currentEncoding)
            }
        }
        return floatBuf
    }

    /**
     * Maps a Media3 PCM encoding constant to the closest integer bit depth for use
     * in USB DAC format negotiation.
     */
    private fun encodingToBitDepth(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT -> 16
        C.ENCODING_PCM_24BIT -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
        else -> 16
    }

    /**
     * Returns true when at least one Bluetooth A2DP or BLE audio output device is
     * currently connected according to [AudioManager.getDevices].
     */
    private fun isBluetoothOutputActive(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return false
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            (device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                                    device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
        }
    }

    /** Releases the native [AaudioOutputProcessor] and resets format tracking state. */
    private fun releaseAaudioStream() {
        aaudioStream?.release()
        aaudioStream = null
        isAAudioStreamActive = false
        if (!UsbDacManager.isActive) {
            unmuteDelegateIfNeeded()
        }
        Log.i(TAG, "AAudio stream released")
    }

    /** Mutes the delegate [DefaultAudioSink] so only the active exclusive output is audible. */
    private fun muteDelegateIfNeeded() {
        if (!delegateMuted) {
            super.setVolume(0f)
            delegateMuted = true
        }
    }

    /**
     * Restores the delegate [DefaultAudioSink] volume so the normal AudioTrack path
     * is audible again. No-op if not currently muted.
     */
    private fun unmuteDelegateIfNeeded() {
        if (delegateMuted) {
            super.setVolume(pendingVolume)
            delegateMuted = false
        }
    }

    companion object {
        private const val TAG = "FelicityAudioSink"

        /**
         * Reflects whether the native AAudio stream is currently open and running.
         * The snapshot builder reads this to show the true hardware state.
         */
        @Volatile
        var isAAudioStreamActive: Boolean = false
    }
}
