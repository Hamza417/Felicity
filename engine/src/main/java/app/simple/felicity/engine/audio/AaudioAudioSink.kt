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
import app.simple.felicity.engine.utils.PcmUtils
import app.simple.felicity.preferences.AudioPreferences
import java.nio.ByteBuffer

/**
 * An [androidx.media3.exoplayer.audio.AudioSink] that routes fully-processed float PCM
 * through the Android AAudio direct-to-HAL path when [AudioPreferences.AAUDIO_ENABLED]
 * is on, while keeping the inner [DefaultAudioSink] alive (muted) for ExoPlayer's clock
 * and state machine.
 *
 * Two production correctness issues are handled transparently:
 *
 * **Format fallback (Bug 1)**: [AaudioOutputProcessor] detects whether the HAL honoured
 * [AAUDIO_FORMAT_PCM_FLOAT] or fell back to [AAUDIO_FORMAT_PCM_I16]. The native write
 * path converts float→int16 via NEON when needed; this class is unaffected.
 *
 * **Bluetooth buffer starvation (Bug 2)**: [isBluetoothOutputActive] inspects the
 * system's connected output devices at [configure] time. When a Bluetooth device is
 * found, [AaudioOutputProcessor] is created with `useSafeBuffers = true`, which opens
 * the stream with [AAUDIO_PERFORMANCE_MODE_NONE] and an 8× burst buffer instead of the
 * tiny low-latency buffer that causes A2DP / BLE stuttering.
 *
 * @param delegate  The [DefaultAudioSink] owned by the ExoPlayer renderer.
 * @param context   Application context; used solely to query [AudioManager] for the
 *                  currently connected output devices (Bluetooth detection).
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class AaudioAudioSink(
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
     * un-muted correctly if AAudio is later disabled between configure calls.
     */
    private var pendingVolume: Float = 1f

    /**
     * Tracks whether the delegate [DefaultAudioSink] is currently muted (volume = 0)
     * because AAudio is producing the audible output. Used to avoid redundant
     * [AudioTrack.setVolume] calls on every [handleBuffer] frame.
     */
    private var delegateMuted: Boolean = false

    /**
     * Configures the sink for [inputFormat]. Always delegates to [DefaultAudioSink],
     * then — when [AudioPreferences.isAaudioEnabled] is true — creates or recreates the
     * native AAudio stream. Bluetooth routing is detected at this point so the stream
     * is opened with the correct performance mode and buffer sizing.
     */
    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        super.configure(inputFormat, specifiedBufferSize, outputChannels)

        val enc = inputFormat.pcmEncoding
        if (enc != Format.NO_VALUE) {
            currentEncoding = enc
        }
        val sr = inputFormat.sampleRate.takeIf { it > 0 } ?: return
        val ch = inputFormat.channelCount.takeIf { it > 0 } ?: return

        if (!AudioPreferences.isAaudioEnabled()) {
            /**
             * AAudio is off. If a stale stream exists from a previous session,
             * release it and restore the delegate's volume.
             */
            if (aaudioStream != null) {
                releaseAaudioStream()
            }
            return
        }

        if (sr != currentSampleRate || ch != currentChannelCount || aaudioStream?.isReady != true) {
            releaseAaudioStream()

            /**
             * Bug 2 fix: detect Bluetooth output at configure time and open the stream
             * in safe-buffer mode so the A2DP / BLE stack is not starved.
             */
            val useSafeBuffers = isBluetoothOutputActive()
            if (useSafeBuffers) {
                Log.i(TAG, "Bluetooth output detected — opening AAudio stream in safe-buffer mode")
            }

            val stream = AaudioOutputProcessor(sr, ch, useSafeBuffers)
            if (stream.isReady) {
                aaudioStream = stream
                currentSampleRate = sr
                currentChannelCount = ch

                /**
                 * Mute the delegate AudioTrack immediately so only the AAudio stream
                 * produces audible output. Without this, both the AudioTrack and the
                 * AAudio stream play the same PCM simultaneously, causing double
                 * volume and a timing-offset echo.
                 */
                muteDelegateIfNeeded()

                Log.i(TAG, "AAudio stream configured — sampleRate=$sr, channels=$ch, " +
                        "encoding=$enc, actualFormat=${stream.getActualFormatName()}, " +
                        "safeBuffers=$useSafeBuffers")
            } else {
                Log.e(TAG, "AAudio stream creation failed for sampleRate=$sr, channels=$ch")
                unmuteDelegateIfNeeded()
            }
        }
    }

    /** Starts the delegate and, when AAudio is enabled, starts the native stream. */
    override fun play() {
        super.play()
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
     * Routes a PCM buffer through [DefaultAudioSink] (for clock / state) and, if AAudio
     * is active and the buffer was consumed, also writes it to the native AAudio stream.
     *
     * The buffer is snapshotted via [ByteBuffer.slice] before delegation so its readable
     * region is preserved for the AAudio write regardless of how [DefaultAudioSink]
     * advances the position internally.
     */
    override fun handleBuffer(
            buffer: ByteBuffer,
            presentationTimeUs: Long,
            encodedAccessUnitCount: Int
    ): Boolean {
        val stream = aaudioStream
        if (!AudioPreferences.isAaudioEnabled() || stream?.isReady != true) {
            unmuteDelegateIfNeeded()
            return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }

        /**
         * Ensure the delegate stays muted while AAudio is driving output. This
         * covers the edge case where ExoPlayer called [setVolume] before the
         * AAudio stream was created and the delegate inherited a non-zero volume.
         */
        muteDelegateIfNeeded()

        /**
         * Snapshot the readable slice BEFORE the delegate potentially advances
         * buffer.position(), so we can re-read the same bytes for AAudio.
         *
         * [ByteBuffer.slice] resets byte order to [java.nio.ByteOrder.BIG_ENDIAN] on
         * Android API < 34. ExoPlayer audio buffers are always
         * [java.nio.ByteOrder.LITTLE_ENDIAN] (native ARM byte order). Without explicitly
         * restoring the byte order here, every [java.nio.ByteBuffer.getFloat] /
         * [java.nio.ByteBuffer.getShort] call in [writeSnapshotToAaudio] would read bytes
         * in the wrong order, producing garbage samples and causing constant static noise.
         */
        val snapshot: ByteBuffer = buffer.slice().order(buffer.order())

        val consumed = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)

        if (consumed) {
            writeSnapshotToAaudio(snapshot, stream)
        }

        return consumed
    }

    /**
     * When AAudio is active, mutes the [DefaultAudioSink]'s [AudioTrack] (volume = 0)
     * so only the AAudio stream produces audible output. The requested volume is stored
     * in [pendingVolume] in case AAudio is later disabled and the AudioTrack needs to be
     * unmuted.
     */
    override fun setVolume(volume: Float) {
        pendingVolume = volume
        val aaudioActive = AudioPreferences.isAaudioEnabled() && aaudioStream?.isReady == true
        if (aaudioActive) {
            muteDelegateIfNeeded()
        } else {
            unmuteDelegateIfNeeded()
        }
    }

    /**
     * Flushes the delegate sink and restarts the AAudio stream to clear any in-flight
     * frames, matching seek and discontinuity behaviour.
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

    /** Releases the native stream, then the delegate. */
    override fun release() {
        releaseAaudioStream()
        super.release()
    }

    /**
     * Converts the PCM content of [snapshot] to a [FloatArray] and writes it to the
     * AAudio stream.
     *
     * **Float path ([C.ENCODING_PCM_FLOAT])**: uses [java.nio.ByteBuffer.asFloatBuffer]
     * plus a single bulk [java.nio.FloatBuffer.get] call. This avoids thousands of
     * individual [java.nio.ByteBuffer.getFloat] calls per buffer, respects the byte order
     * that was restored in [handleBuffer], and keeps the hot audio path allocation-free
     * in steady state.
     *
     * **All other encodings**: delegates to [PcmUtils.readFloat] per sample. [PcmUtils]
     * reads 24-bit data byte-by-byte (byte-order neutral) and uses [java.nio.ByteBuffer.getShort]
     * / [java.nio.ByteBuffer.getInt] for 16-bit and 32-bit (both of which are now correct
     * because [snapshot]'s byte order was fixed in [handleBuffer]).
     *
     * The native layer handles any further format conversion (e.g., float→int16) transparently.
     */
    private fun writeSnapshotToAaudio(snapshot: ByteBuffer, stream: AaudioOutputProcessor) {
        val bps = PcmUtils.bytesPerSample(currentEncoding)
        val totalSamples = snapshot.remaining() / bps
        if (totalSamples <= 0) return

        val floatBuf = FloatArray(totalSamples)

        if (currentEncoding == C.ENCODING_PCM_FLOAT) {
            /**
             * Bulk transfer: [ByteBuffer.asFloatBuffer] creates a view that inherits the
             * corrected byte order, then [FloatBuffer.get] copies all samples in one call.
             */
            snapshot.asFloatBuffer().get(floatBuf)
        } else {
            for (i in 0 until totalSamples) {
                floatBuf[i] = PcmUtils.readFloat(snapshot, currentEncoding)
            }
        }

        stream.write(floatBuf)
    }

    /**
     * Returns true when at least one Bluetooth A2DP or BLE audio output device is
     * currently connected according to [AudioManager.getDevices].
     *
     * Checked at [configure] time so the AAudio stream is opened with the correct
     * performance mode and buffer sizing from the start of each playback session.
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
        currentSampleRate = 0
        currentChannelCount = 0
        unmuteDelegateIfNeeded()
        Log.i(TAG, "AAudio stream released")
    }

    /**
     * Mutes the delegate [DefaultAudioSink] (volume = 0) so only the AAudio stream
     * is audible. No-op if already muted.
     */
    private fun muteDelegateIfNeeded() {
        if (!delegateMuted) {
            super.setVolume(0f)
            delegateMuted = true
        }
    }

    /**
     * Restores the delegate [DefaultAudioSink] volume to the last value ExoPlayer
     * requested ([pendingVolume]) so the normal AudioTrack path is audible again.
     * No-op if not currently muted.
     */
    private fun unmuteDelegateIfNeeded() {
        if (delegateMuted) {
            super.setVolume(pendingVolume)
            delegateMuted = false
        }
    }

    companion object {
        private const val TAG = "AaudioAudioSink"
    }
}
