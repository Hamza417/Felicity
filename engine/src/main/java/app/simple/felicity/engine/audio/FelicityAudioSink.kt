package app.simple.felicity.engine.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import app.simple.felicity.engine.processors.AaudioOutputProcessor
import app.simple.felicity.engine.processors.NativeDspAudioProcessor
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
 * **DSP execution model**: [DefaultAudioSink]'s processor chain owns the effects for the
 * AudioTrack fallback. When USB DAC or AAudio is the active output, [nativeDsp] is put
 * into bypass mode inside [DefaultAudioSink]'s chain ([NativeDspAudioProcessor.isBypassedForDirectOutput]
 * = true), and this sink slices the raw buffer, converts it to float, and calls
 * [NativeDspAudioProcessor.processInPlace] directly before pushing the result to the
 * hardware. This way the DAC always hears the fully processed signal regardless of whether
 * ExoPlayer's internal hi-res toggle disables the processor array, because we are no
 * longer relying on [DefaultAudioSink] to run the effects — we run them ourselves.
 *
 * **Format sync**: [configure] is the source of truth for the current ExoPlayer format.
 * When a USB DAC attaches mid-session, the [UsbDacManager.Listener] receives the attach
 * event and immediately re-negotiates the DAC to match the live ExoPlayer sample rate,
 * bit depth, and channel count so there is never a rate mismatch between what the DSP
 * outputs and what the DAC expects.
 *
 * @param delegate    The [DefaultAudioSink] owned by the ExoPlayer renderer.
 * @param context     Application context used to query [AudioManager] (Bluetooth detection)
 *                    and to reach [UsbDacDriver] for PCM push calls.
 * @param nativeDsp   The shared [NativeDspAudioProcessor]. It is both present in
 *                    [DefaultAudioSink]'s processor list (for the AudioTrack path) and
 *                    driven directly here (for USB/AAudio). [isBypassedForDirectOutput]
 *                    prevents it from processing the same audio twice.
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class FelicityAudioSink(
        private val delegate: DefaultAudioSink,
        private val context: Context,
        private val nativeDsp: NativeDspAudioProcessor
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
     * A pre-allocated float buffer that gets reused across every call to [handleBuffer].
     * Since this hot path runs dozens to hundreds of times per second, allocating a new
     * array each time would constantly feed the garbage collector — potentially causing
     * brief GC pauses that show up as audio dropouts. Instead, we keep one array and only
     * grow it when an incoming frame is larger than anything we've seen before.
     */
    private var floatScratchBuffer: FloatArray = FloatArray(0)

    /**
     * Holds a reference to the last buffer that [DefaultAudioSink] refused to consume
     * (i.e. [handleBuffer] returned false). When ExoPlayer retries the same buffer, we
     * can detect it by identity and skip re-sending to AAudio or the USB DAC — the
     * exclusive output already received that audio on the first attempt.
     *
     * This is the key fix for the stuttering/gaps that appear when the delegate is running
     * a float [android.media.AudioTrack]: float buffers are 2× larger in bytes for the same
     * frame count, so the AudioTrack fills up and returns false more often. Without this
     * guard, AAudio would be starved every time the delegate needed a retry.
     */
    private var pendingDelegateBuffer: ByteBuffer? = null

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

        /**
         * Explicitly drive the NativeDsp lifecycle here instead of relying on ExoPlayer's
         * internal chain forwarding. When Hi-Res float output is active and
         * [isBypassedForDirectOutput] is true, ExoPlayer can detect the chain as a no-op
         * passthrough and skip forwarding flush() to individual processors — which means
         * neither the DspProcessor retry block nor pushAllParameters() ever fire.
         * By calling configure() + flush() ourselves we guarantee [dspProcessor] is always
         * live and fully loaded with the user's saved EQ / preamp / bass / treble settings
         * before the very first [handleBuffer] call, regardless of ExoPlayer's optimizations.
         */
        val audioFormat = AudioProcessor.AudioFormat(sr, ch, currentEncoding)
        nativeDsp.configure(audioFormat)
        nativeDsp.flush()

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
     * Routes PCM through [DefaultAudioSink] (for clock / state management) and simultaneously
     * pushes fully-processed audio to the active exclusive output.
     *
     * The key insight is that we no longer trust [DefaultAudioSink]'s processor chain to
     * run the effects — ExoPlayer can silently bypass the entire array for hi-res float
     * formats. Instead, when USB DAC or AAudio is live, we:
     *   1. On the FIRST presentation of a buffer: take a snapshot of the raw bytes, convert
     *      to float, run the full native DSP chain in-place, and push to the hardware output.
     *   2. On subsequent RETRIES of the same buffer (when the delegate's [AudioTrack] was
     *      too busy to accept it): skip the hardware push entirely — the exclusive output
     *      already received this audio on the first attempt.
     *   3. Always call super so the delegate eventually consumes the buffer and advances
     *      ExoPlayer's internal clock, regardless of how many retries it takes.
     *   4. Tell [nativeDsp] to act as a transparent passthrough inside [DefaultAudioSink]'s
     *      chain ([NativeDspAudioProcessor.isBypassedForDirectOutput] = true) so the same
     *      audio is not processed a second time by the delegate.
     *   5. Let super consume the original raw buffer for its internal timing bookkeeping.
     *
     * The retry-detection is done by reference identity: ExoPlayer re-presents the exact
     * same [ByteBuffer] object when the previous call returned false. Tracking [pendingDelegateBuffer]
     * lets us distinguish a genuine retry from a new audio frame.
     *
     * When neither exclusive output is active the delegate is unmuted and runs its own
     * processor chain for the AudioTrack fallback — [nativeDsp] bypass is cleared so the
     * DSP effects reach the AudioTrack output as usual.
     */
    override fun handleBuffer(
            buffer: ByteBuffer,
            presentationTimeUs: Long,
            encodedAccessUnitCount: Int
    ): Boolean {
        val usbActive = UsbDacManager.isActive
        val aaudioReady = AudioPreferences.isAaudioEnabled() && aaudioStream?.isReady == true

        if (!usbActive && !aaudioReady) {
            // AudioTrack path — let [DefaultAudioSink] run the full processor chain as normal.
            nativeDsp.isBypassedForDirectOutput = false
            unmuteDelegateIfNeeded()
            return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }

        muteDelegateIfNeeded()

        /**
         * Bypass the DSP inside DefaultAudioSink's chain so the same audio is not
         * processed twice — once here and once (silently) by the muted delegate.
         */
        nativeDsp.isBypassedForDirectOutput = true

        /**
         * Only process and push to the hardware on the FIRST time we see this buffer.
         * [pendingDelegateBuffer] holds a reference to the last buffer the delegate
         * refused. If the incoming buffer is the same object, it's a retry — the
         * exclusive output already has this audio, so we skip straight to the delegate
         * call below. This is what prevents AAudio from being starved when a float
         * [AudioTrack] needs multiple attempts to accept a large buffer.
         */
        if (buffer !== pendingDelegateBuffer) {
            val snapshot = buffer.slice().order(buffer.order())
            val sampleCount = snapshotToFloat(snapshot, currentEncoding)
            if (sampleCount > 0) {
                nativeDsp.processInPlace(floatScratchBuffer, sampleCount)

                if (usbActive) {
                    UsbDacDriver.getInstance(context).nativePushPcm(floatScratchBuffer, 0, sampleCount)
                } else {
                    aaudioStream?.write(floatScratchBuffer, sampleCount)
                }
            }
        }

        val consumed = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)

        // When the delegate finally accepts the buffer, clear the pending reference so
        // the next buffer is treated as fresh. When rejected, record it so retries are
        // identified correctly.
        pendingDelegateBuffer = if (consumed) null else buffer

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
     * frames on seeks and discontinuities. Also clears [pendingDelegateBuffer] so the
     * first buffer after the seek is always treated as fresh — without this, a buffer
     * presented just before the seek and never consumed by the delegate would suppress
     * the first audio frame after the seek from reaching AAudio.
     *
     * USB ring buffer is NOT flushed here because the native isochronous pipeline will
     * drain the stale bytes as silence before new data arrives — abruptly flushing
     * mid-transfer is more disruptive than letting it drain.
     */
    override fun flush() {
        super.flush()
        pendingDelegateBuffer = null
        aaudioStream?.apply {
            stop()
            start()
        }
    }

    /** Resets the delegate, clears any pending buffer state, and releases the native AAudio stream. */
    override fun reset() {
        super.reset()
        pendingDelegateBuffer = null
        releaseAaudioStream()
    }

    /**
     * Releases all resources. Unregisters from [UsbDacManager] so this instance does not
     * receive callbacks after the ExoPlayer session ends. Also clears the bypass flag so
     * if a new [FelicityAudioSink] is created with the same [nativeDsp] instance, it starts
     * in the correct unmodified state.
     */
    override fun release() {
        nativeDsp.isBypassedForDirectOutput = false
        UsbDacManager.removeListener(usbDacListener)
        releaseAaudioStream()
        super.release()
    }

    /**
     * Converts the PCM content of [snapshot] to an interleaved [FloatArray] using the given
     * [encoding]. Both the raw decoder output encoding and the float fast path are handled:
     *   - [C.ENCODING_PCM_FLOAT]: bulk copy via [java.nio.FloatBuffer.get], zero per-sample cost.
     *   - All other encodings: per-sample conversion through [PcmUtils.readFloat].
     *
     * @param snapshot  A read-only view of the raw PCM bytes for the current render cycle.
     * @param encoding  The Media3 encoding constant that describes the sample layout.
     */
    /**
     * Fills the reusable [floatScratchBuffer] with the PCM content of [snapshot] and
     * returns how many samples were written. The array is only reallocated when the current
     * frame needs more capacity than what we have — so the common steady-state case
     * (same buffer size every frame) does zero heap allocation.
     *
     * @return The number of valid samples written into [floatScratchBuffer], or 0 if the
     *         snapshot was empty.
     */
    private fun snapshotToFloat(snapshot: ByteBuffer, encoding: Int): Int {
        val bps = PcmUtils.bytesPerSample(encoding)
        val totalSamples = snapshot.remaining() / bps
        if (totalSamples <= 0) return 0

        if (floatScratchBuffer.size < totalSamples) {
            floatScratchBuffer = FloatArray(totalSamples)
        }

        if (encoding == C.ENCODING_PCM_FLOAT) {
            snapshot.asFloatBuffer().get(floatScratchBuffer, 0, totalSamples)
        } else {
            for (i in 0 until totalSamples) {
                floatScratchBuffer[i] = PcmUtils.readFloat(snapshot, encoding)
            }
        }
        return totalSamples
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
