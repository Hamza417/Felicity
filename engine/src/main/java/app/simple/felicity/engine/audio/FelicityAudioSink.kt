package app.simple.felicity.engine.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import app.simple.felicity.engine.processors.AaudioOutputProcessor
import app.simple.felicity.engine.processors.NativeDspAudioProcessor
import app.simple.felicity.engine.processors.OboeOutputProcessor
import app.simple.felicity.engine.processors.VisualizerProcessor
import app.simple.felicity.engine.usb.UsbDacDriver
import app.simple.felicity.engine.usb.UsbDacManager
import app.simple.felicity.engine.utils.PcmUtils
import app.simple.felicity.preferences.AudioPreferences
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An [AudioSink] that acts as an exclusive audio router, directing all PCM output
 * to exactly one output path at a time: USB DAC, AAudio, Oboe, or the standard
 * [DefaultAudioSink] / AudioTrack fallback.
 *
 * Unlike the previous splitter-based design, this sink never runs two output paths
 * simultaneously. Whichever path [configure] selects becomes the sole active route
 * until the next [configure] call changes it. The [DefaultAudioSink] is only
 * touched when it is the selected route — it is otherwise idle, so there is no
 * muting bookkeeping and no risk of the AudioTrack silently consuming audio behind
 * the native stream.
 *
 * **Routing priority (exclusive):**
 *   1. USB DAC direct (when [UsbDacManager.isActive] — bypasses Android mixer)
 *   2. AAudio direct-to-HAL (when [AudioPreferences.SINK_AAUDIO])
 *   3. Oboe (when [AudioPreferences.SINK_OBOE])
 *   4. [DefaultAudioSink] / AudioTrack (fallback)
 *
 * **DSP model:** For native routes (AAudio, Oboe, USB) the DSP is applied directly
 * inside [handleBuffer] before writing to the hardware stream, giving the same
 * full-quality signal regardless of which path is active. For the [DefaultAudioSink]
 * path the DSP runs through its AudioProcessor chain as normal.
 *
 * **Position source of truth:** Each route reports its own [getCurrentPositionUs]
 * using the hardware clock directly (AAudio/Oboe timestamps, USB ring-buffer depth)
 * rather than delegating to [DefaultAudioSink]. This is the key correctness fix that
 * allows dropping [DefaultAudioSink] as a clock when native outputs are active.
 *
 * @param defaultSinkProvider Factory that creates the [DefaultAudioSink] on demand.
 *                             It is called lazily the first time [DefaultSinkWrapper]
 *                             needs the sink — so if a native route is always used,
 *                             the [DefaultAudioSink] and its AudioTrack are never
 *                             created at all.
 * @param context             Application context used to query [AudioManager].
 * @param nativeDsp           Shared [NativeDspAudioProcessor] for direct-path processing.
 * @param visualizer          Shared [VisualizerProcessor] for the spectrum display.
 *
 * @author Hamza417
 */
@OptIn(UnstableApi::class)
class FelicityAudioSink(
        private val defaultSinkProvider: () -> DefaultAudioSink,
        private val context: Context,
        private val nativeDsp: NativeDspAudioProcessor,
        private val visualizer: VisualizerProcessor
) : AudioSink {

    // -----------------------------------------------------------------------------------------
    // Internal route interface
    // -----------------------------------------------------------------------------------------

    /**
     * The minimal contract that every output route must fulfill so [FelicityAudioSink]
     * can delegate to it without knowing which hardware is behind it.
     *
     * All implementations are private inner classes so they can freely access the
     * outer class's scratch buffers, format state, and audio processors.
     */
    private interface InternalSink {
        /** True when the underlying hardware stream was opened successfully. */
        val isReady: Boolean

        /** Sets up the route for the given format. May be a no-op for native routes. */
        fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?)

        /**
         * Processes and writes one buffer of audio. Returns true when the buffer
         * was fully consumed; false when the caller should retry with the same buffer.
         */
        fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean

        /** Returns the estimated playback position in microseconds. */
        fun getCurrentPositionUs(sourceEnded: Boolean): Long

        // Tells ExoPlayer if the hardware buffer contains audio that hasn't played out yet.
        fun hasPendingData(): Boolean

        /** Starts or resumes the output stream. */
        fun play()

        /** Pauses the output stream without closing it. */
        fun pause()

        /**
         * Discards queued audio and leaves the stream ready for [play] to restart
         * it from a fresh position.
         */
        fun flush()

        /** Stops the stream and frees all hardware resources. */
        fun release()

        /** Applies the given volume level (0.0–1.0) to the output. */
        fun setVolume(volume: Float)
    }

    // -----------------------------------------------------------------------------------------
    // Route implementations
    // -----------------------------------------------------------------------------------------

    /**
     * Wraps [DefaultAudioSink] so it fits the [InternalSink] contract.
     * All calls forward straight through, letting the AudioProcessor chain
     * handle DSP the normal ExoPlayer way.
     */
    private inner class DefaultSinkWrapper : InternalSink {
        override val isReady = true
        override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) = defaultSink.configure(inputFormat, specifiedBufferSize, outputChannels)
        override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int) = defaultSink.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        override fun getCurrentPositionUs(sourceEnded: Boolean): Long = defaultSink.getCurrentPositionUs(sourceEnded)
        override fun hasPendingData(): Boolean = defaultSink.hasPendingData()
        override fun play() = defaultSink.play()
        override fun pause() = defaultSink.pause()
        override fun flush() = defaultSink.flush()
        override fun release() = defaultSink.release()
        override fun setVolume(volume: Float) = defaultSink.setVolume(volume)
    }

    /**
     * Routes audio to the native AAudio stream. The DSP and visualizer are applied
     * inline inside [handleBuffer] so the hardware always receives the fully-processed
     * signal, even though [DefaultAudioSink]'s processor chain is not involved.
     */
    private inner class AaudioNativeSink(
            val stream: AaudioOutputProcessor,
            private val channelCount: Int
    ) : InternalSink {
        override val isReady get() = stream.isReady

        private var isFirstBuffer = true
        private var basePresentationTimeUs = 0L
        private var startSystemTimeUs = 0L
        private var accumulatedPlayedUs = 0L
        private var isClockRunning = false

        override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {}

        override fun hasPendingData(): Boolean {
            // If we have started writing data, there is audio pending in the hardware queue
            return !isFirstBuffer
        }

        override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
            // Only auto-start the stream if ExoPlayer is actively playing.
            // If paused, we still process and write the data (pre-buffering), but keep the hardware asleep.
            if (!isPaused && !stream.isRunning) {
                stream.start()
                startClock()
            }

            // If paused, only accept the very first buffer to establish the base presentation time.
            // Reject everything else so ExoPlayer stops decoding and feeding the void.
            if (isPaused && !isFirstBuffer) {
                return false
            }

            if (isFirstBuffer) {
                basePresentationTimeUs = presentationTimeUs
                isFirstBuffer = false

                // Only start the clock if we are actually playing.
                // If paused, play() will call startClock() later.
                if (!isPaused) {
                    startClock()
                }

                // For UsbNativeSink, adjust the latency calculation line accordingly as per your original code
                val latencyMs = stream.getLatencyMs().coerceAtLeast(10)
                audioSinkListener?.onPositionAdvancing(System.currentTimeMillis() + latencyMs)
            }

            Log.d(TAG, "AAudioNativeSink.handleBuffer: writing ${buffer.remaining()} bytes at $presentationTimeUs us")

            val snapshot = buffer.slice().order(buffer.order())
            val sampleCount = snapshotToFloat(snapshot, currentEncoding)
            if (sampleCount > 0) {
                nativeDsp.processInPlace(floatScratchBuffer, sampleCount)
                visualizer.feedFloat(floatScratchBuffer, sampleCount, channelCount)
                stream.write(floatScratchBuffer, sampleCount)
            }
            buffer.position(buffer.limit())
            return true
        }

        private fun startClock() {
            if (!isClockRunning) {
                startSystemTimeUs = System.nanoTime() / 1000L
                isClockRunning = true
            }
        }

        private fun pauseClock() {
            if (isClockRunning) {
                val nowUs = System.nanoTime() / 1000L
                accumulatedPlayedUs += (nowUs - startSystemTimeUs)
                isClockRunning = false
            }
        }

        override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
            if (isFirstBuffer) return Long.MIN_VALUE

            val currentElapsedUs = if (isClockRunning) {
                (System.nanoTime() / 1000L) - startSystemTimeUs
            } else 0L

            val totalPlayedUs = accumulatedPlayedUs + currentElapsedUs
            val latencyUs = stream.getLatencyMs().coerceAtLeast(0) * 1000L
            val actualElapsedUs = (totalPlayedUs - latencyUs).coerceAtLeast(0L)

            return basePresentationTimeUs + actualElapsedUs
        }

        override fun play() {
            if (!stream.isRunning) stream.start()
            startClock()
        }

        override fun pause() {
            stream.pause()
            pauseClock()
        }

        override fun flush() {
            stream.stop()
            isFirstBuffer = true
            accumulatedPlayedUs = 0L
            isClockRunning = false
            if (!isPaused) {
                stream.start()
                startClock()
            }
        }

        override fun release() {
            stream.release()
            isAAudioStreamActive = false
            Log.i(TAG, "AAudio native sink released")
        }

        override fun setVolume(volume: Float) {
            // Native streams run at unity gain; volume is applied upstream by the DSP chain.
        }
    }

    /**
     * Routes audio to the native Oboe stream. Structurally identical to [AaudioNativeSink] —
     * Oboe just provides a different backend that picks AAudio or OpenSL ES at runtime.
     */
    private inner class OboeNativeSink(
            val stream: OboeOutputProcessor,
            private val channelCount: Int
    ) : InternalSink {
        override val isReady get() = stream.isReady

        private var isFirstBuffer = true
        private var basePresentationTimeUs = 0L
        private var startSystemTimeUs = 0L
        private var accumulatedPlayedUs = 0L
        private var isClockRunning = false

        override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {}

        override fun hasPendingData(): Boolean = !isFirstBuffer

        override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
            // Respect the paused state to prevent micro-stutters
            if (!isPaused && !stream.isRunning) {
                stream.start()
                startClock()
            }

            // If paused, only accept the very first buffer to establish the base presentation time.
            // Reject everything else so ExoPlayer stops decoding and feeding the void.
            if (isPaused && !isFirstBuffer) {
                return false
            }

            if (isFirstBuffer) {
                basePresentationTimeUs = presentationTimeUs
                isFirstBuffer = false

                // Only start the clock if we are actually playing.
                // If paused, play() will call startClock() later.
                if (!isPaused) {
                    startClock()
                }

                // For UsbNativeSink, adjust the latency calculation line accordingly as per your original code
                val latencyMs = stream.getLatencyMs().coerceAtLeast(10)
                audioSinkListener?.onPositionAdvancing(System.currentTimeMillis() + latencyMs)
            }

            val snapshot = buffer.slice().order(buffer.order())
            val sampleCount = snapshotToFloat(snapshot, currentEncoding)
            if (sampleCount > 0) {
                nativeDsp.processInPlace(floatScratchBuffer, sampleCount)
                visualizer.feedFloat(floatScratchBuffer, sampleCount, channelCount)
                stream.write(floatScratchBuffer, sampleCount)
            }
            buffer.position(buffer.limit())
            return true
        }

        private fun startClock() {
            if (!isClockRunning) {
                startSystemTimeUs = System.nanoTime() / 1000L
                isClockRunning = true
            }
        }

        private fun pauseClock() {
            if (isClockRunning) {
                val nowUs = System.nanoTime() / 1000L
                accumulatedPlayedUs += (nowUs - startSystemTimeUs)
                isClockRunning = false
            }
        }

        override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
            if (isFirstBuffer) return Long.MIN_VALUE

            val currentElapsedUs = if (isClockRunning) {
                (System.nanoTime() / 1000L) - startSystemTimeUs
            } else 0L

            val totalPlayedUs = accumulatedPlayedUs + currentElapsedUs
            val latencyUs = stream.getLatencyMs().coerceAtLeast(0) * 1000L
            val actualElapsedUs = (totalPlayedUs - latencyUs).coerceAtLeast(0L)

            return basePresentationTimeUs + actualElapsedUs
        }

        override fun play() {
            if (!stream.isRunning) stream.start()
            startClock()
        }

        override fun pause() {
            stream.stop()
            pauseClock()
        }

        override fun flush() {
            stream.stop()
            isFirstBuffer = true
            accumulatedPlayedUs = 0L
            isClockRunning = false
            if (!isPaused) {
                stream.start()
                startClock()
            }
        }

        override fun release() {
            stream.release()
            isOboeStreamActive = false
            Log.i(TAG, "Oboe native sink released")
        }

        override fun setVolume(volume: Float) {
            // Unity gain — volume is handled upstream by the DSP chain.
        }
    }

    /**
     * Routes audio to the USB DAC via the isochronous ring buffer. DSP is applied
     * inline before each push so the DAC always hears the fully-processed signal.
     */
    private inner class UsbNativeSink(
            private val driver: UsbDacDriver,
            private val sampleRate: Int,
            private val channelCount: Int
    ) : InternalSink {
        override val isReady = true

        private var isFirstBuffer = true
        private var basePresentationTimeUs = 0L
        private var startSystemTimeUs = 0L
        private var accumulatedPlayedUs = 0L
        private var isClockRunning = false

        override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {}

        override fun hasPendingData(): Boolean = !isFirstBuffer

        override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
            // If paused, only accept the very first buffer to establish the base presentation time.
            // Reject everything else so ExoPlayer stops decoding and feeding the void.
            if (isPaused && !isFirstBuffer) {
                return false
            }

            if (isFirstBuffer) {
                basePresentationTimeUs = presentationTimeUs
                isFirstBuffer = false

                // Only start the clock if we are actually playing.
                // If paused, play() will call startClock() later.
                if (!isPaused) {
                    startClock()
                }

                audioSinkListener?.onPositionAdvancing(System.currentTimeMillis() + 50)
            }

            val snapshot = buffer.slice().order(buffer.order())
            val sampleCount = snapshotToFloat(snapshot, currentEncoding)
            if (sampleCount > 0) {
                nativeDsp.processInPlace(floatScratchBuffer, sampleCount)
                visualizer.feedFloat(floatScratchBuffer, sampleCount, channelCount)
                driver.nativePushPcm(floatScratchBuffer, 0, sampleCount)
            }
            buffer.position(buffer.limit())
            return true
        }

        private fun startClock() {
            if (!isClockRunning) {
                startSystemTimeUs = System.nanoTime() / 1000L
                isClockRunning = true
            }
        }

        private fun pauseClock() {
            if (isClockRunning) {
                val nowUs = System.nanoTime() / 1000L
                accumulatedPlayedUs += (nowUs - startSystemTimeUs)
                isClockRunning = false
            }
        }

        override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
            if (isFirstBuffer) return Long.MIN_VALUE

            val currentElapsedUs = if (isClockRunning) {
                (System.nanoTime() / 1000L) - startSystemTimeUs
            } else 0L

            val totalPlayedUs = accumulatedPlayedUs + currentElapsedUs
            val latencyUs = 50_000L // 50ms estimated USB latency
            val actualElapsedUs = (totalPlayedUs - latencyUs).coerceAtLeast(0L)

            return basePresentationTimeUs + actualElapsedUs
        }

        override fun play() {
            driver.startStream()
            startClock()
        }

        override fun pause() {
            driver.stopStream()
            pauseClock()
        }

        override fun flush() {
            // Keep the isochronous pipeline running — just drain the ring buffer.
            // A full stop/start cycle takes 20–500 ms and creates an audible gap
            // on every seek; flushing plays silence seamlessly until fresh audio arrives.
            driver.flushRingBuffer()
            isFirstBuffer = true
            accumulatedPlayedUs = 0L
            isClockRunning = false
        }

        override fun release() {
            driver.stopStream()
            Log.i(TAG, "USB DAC native sink released")
        }

        override fun setVolume(volume: Float) {
            // USB volume is set via UAC control transfers at negotiation time.
        }
    }

    // -----------------------------------------------------------------------------------------
    // Routing state
    // -----------------------------------------------------------------------------------------

    /** The four possible output destinations. */
    private enum class SinkType { DEFAULT, AAUDIO, OBOE, USB }

    /**
     * Lazily created [DefaultAudioSink]. It is only instantiated when the DEFAULT
     * route is selected or when ExoPlayer calls a method only [DefaultAudioSink]
     * can handle (like [supportsFormat]).
     */
    private val defaultSinkDelegate = lazy { defaultSinkProvider() }
    private val defaultSink: DefaultAudioSink by defaultSinkDelegate

    /** The route that is currently producing audio. Starts at DEFAULT. */
    private var activeSink: InternalSink = DefaultSinkWrapper()

    /** Which type of route [activeSink] represents. */
    private var activeSinkType = SinkType.DEFAULT

    /**
     * The listener that ExoPlayer's audio renderer registered via [setListener].
     * Stored here so native sinks can forward position and state callbacks directly
     * instead of routing them through the idle [DefaultAudioSink], which would never
     * fire those callbacks when a native route is active.
     */
    private var audioSinkListener: AudioSink.Listener? = null

    // -----------------------------------------------------------------------------------------
    // Format state
    // -----------------------------------------------------------------------------------------

    /** PCM encoding of the most recently configured format. */
    private var currentEncoding: Int = C.ENCODING_PCM_16BIT

    /** Sample rate of the most recently configured format, in Hz. */
    private var currentSampleRate: Int = 0

    /** Channel count of the most recently configured format. */
    private var currentChannelCount: Int = 0

    // -----------------------------------------------------------------------------------------
    // Playback state
    // -----------------------------------------------------------------------------------------

    /**
     * Whether the player is currently paused. Initialized to true so a [flush] during
     * ExoPlayer's initial preparation does not accidentally start the hardware stream
     * before the user has pressed play.
     */
    private var isPaused: Boolean = true

    /**
     * Whether the stream that is currently open was created with Bluetooth-safe settings.
     * Compared before rerouting so we only tear down and recreate when the mode actually
     * changes — not on every device-change event.
     */
    private var currentStreamSafeBufferMode: Boolean = false

    /**
     * Pre-allocated float buffer reused across every [handleBuffer] call to avoid
     * creating garbage that could trigger GC pauses at exactly the wrong time.
     * It grows only when an incoming frame needs more capacity than before.
     */
    private var floatScratchBuffer: FloatArray = FloatArray(0)

    /**
     * Set to true by [audioDeviceCallback] when a Bluetooth device is added or removed.
     * Checked at the top of [handleBuffer] so the reroute happens on the audio thread
     * that already owns all stream references — no extra locking needed.
     */
    private val rerouteNeeded = AtomicBoolean(false)

    /** Posts Bluetooth reroute requests to the main thread to avoid duplicate scheduling. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Set to true when [playToEndOfStream] is called, which signals that no more
     * audio data will be written to this sink for the current track.
     */
    private var nativeSourceEnded = false

    /**
     * Wall-clock time in milliseconds at which [playToEndOfStream] was called plus the
     * estimated latency of the active stream. [isEnded] returns true only after this
     * timestamp, giving the hardware buffer time to play out all remaining audio.
     */
    private var nativeEndDeadlineMs = 0L

    // -----------------------------------------------------------------------------------------
    // Device change & USB DAC callbacks
    // -----------------------------------------------------------------------------------------

    /**
     * Fires when a Bluetooth device is added or removed. We set a flag here and let
     * [handleBuffer] pick it up on the audio thread, which is the safe place to
     * touch stream references.
     */
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (addedDevices.any { it.isBluetoothType() }) {
                Log.i(TAG, "Bluetooth output device added — scheduling stream reroute")
                rerouteNeeded.set(true)
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.isBluetoothType() }) {
                Log.i(TAG, "Bluetooth output device removed — scheduling stream reroute")
                rerouteNeeded.set(true)
            }
        }
    }

    /**
     * Reacts to USB DAC connect/disconnect events so the active route can be
     * swapped immediately without waiting for a new ExoPlayer [configure] call.
     */
    private val usbDacListener = object : UsbDacManager.Listener {
        override fun onUsbDacAttached(sampleRate: Int, channelCount: Int) {
            if (currentSampleRate > 0 && currentChannelCount > 0) {
                val bitDepth = encodingToBitDepth(currentEncoding)
                Log.i(TAG, "USB DAC attached mid-stream — switching to USB route " +
                        "($currentSampleRate Hz / ${bitDepth}-bit / $currentChannelCount ch)")
                activeSink.release()
                val driver = UsbDacDriver.getInstance(context)
                driver.negotiateFormat(currentSampleRate, bitDepth, currentChannelCount)
                activeSink = UsbNativeSink(driver, currentSampleRate, currentChannelCount)
                activeSinkType = SinkType.USB
                isAAudioStreamActive = false
                isOboeStreamActive = false
                if (!isPaused) driver.startStream()
            }
        }

        override fun onUsbDacDetached() {
            Log.i(TAG, "USB DAC detached — reverting to configured output sink")
            activeSink.release()
            if (currentSampleRate > 0 && currentChannelCount > 0) {
                val targetType = targetSinkType()
                activeSink = createSink(targetType, currentSampleRate, currentChannelCount)
                activeSinkType = targetType
                if (!isPaused) activeSink.play()
            } else {
                activeSink = DefaultSinkWrapper()
                activeSinkType = SinkType.DEFAULT
            }
        }
    }

    init {
        UsbDacManager.addListener(usbDacListener)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
    }

    // -----------------------------------------------------------------------------------------
    // AudioSink implementation — routing core
    // -----------------------------------------------------------------------------------------

    /**
     * Selects the right output route for [inputFormat] and configures it. If the route
     * type or audio format has changed since the last call, the previous route is released
     * first and a fresh one is opened in its place.
     *
     * The DSP is always re-synchronized here so its filter coefficients match the new
     * format before the first [handleBuffer] call arrives.
     */
    @Throws(AudioSink.ConfigurationException::class)
    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        val enc = inputFormat.pcmEncoding.takeIf { it != Format.NO_VALUE } ?: currentEncoding
        currentEncoding = enc

        val sr = inputFormat.sampleRate.takeIf { it > 0 } ?: return
        val ch = inputFormat.channelCount.takeIf { it > 0 } ?: return

        val prevSr = currentSampleRate
        val prevCh = currentChannelCount
        currentSampleRate = sr
        currentChannelCount = ch

        // Re-configure the DSP before any audio reaches handleBuffer so the
        // filter coefficients are correct from the very first frame.
        val audioFormat = AudioProcessor.AudioFormat(sr, ch, currentEncoding)
        nativeDsp.configure(audioFormat)
        nativeDsp.flush()

        val targetType = targetSinkType()
        val formatChanged = sr != prevSr || ch != prevCh
        val typeChanged = targetType != activeSinkType
        val streamBroken = !activeSink.isReady

        if (typeChanged || formatChanged || streamBroken) {
            activeSink.release()
            activeSink = createSink(targetType, sr, ch)
            activeSinkType = targetType
        }

        activeSink.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    @Throws(AudioSink.InitializationException::class, AudioSink.WriteException::class)
    override fun handleBuffer(
            buffer: ByteBuffer,
            presentationTimeUs: Long,
            encodedAccessUnitCount: Int
    ): Boolean {
        // Pick up any pending Bluetooth reroute before writing audio, so the
        // stream objects are always in the right state before we touch them.
        if (rerouteNeeded.compareAndSet(true, false)) {
            rerouteStreamForDeviceChange()
        }
        return activeSink.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        return activeSink.getCurrentPositionUs(sourceEnded)
    }

    override fun play() {
        isPaused = false
        activeSink.play()
    }

    override fun pause() {
        isPaused = true
        activeSink.pause()
    }

    override fun flush() {
        nativeSourceEnded = false
        nativeEndDeadlineMs = 0L
        activeSink.flush()
    }

    /**
     * Called when all audio data for the current track has been submitted.
     * For native sinks (AAudio, Oboe, USB) the writes are synchronous, so the
     * hardware buffer already holds the last frames. We record a deadline that
     * is roughly the stream's current latency from now — [isEnded] returns true
     * once that window has elapsed, giving the hardware time to play the tail out.
     */
    @Throws(AudioSink.WriteException::class)
    override fun playToEndOfStream() {
        if (activeSinkType == SinkType.DEFAULT) {
            defaultSink.playToEndOfStream()
        } else {
            nativeSourceEnded = true
            // Give the hardware enough time to drain its buffer before signaling done.
            val latencyMs = when (activeSinkType) {
                SinkType.AAUDIO -> (activeSink as? AaudioNativeSink)
                    ?.stream?.getLatencyMs()?.coerceAtLeast(40) ?: 100
                SinkType.OBOE -> (activeSink as? OboeNativeSink)
                    ?.stream?.getLatencyMs()?.coerceAtLeast(40) ?: 100
                else -> 200 // USB pipeline depth estimate
            }
            nativeEndDeadlineMs = System.currentTimeMillis() + latencyMs
        }
    }

    /**
     * Returns true when the active sink has finished playing all submitted audio.
     * For native sinks (synchronous writes) this becomes true shortly after
     * [playToEndOfStream] is called, once the hardware latency window elapses.
     */
    override fun isEnded(): Boolean {
        if (activeSinkType == SinkType.DEFAULT) return defaultSink.isEnded()
        return nativeSourceEnded && System.currentTimeMillis() >= nativeEndDeadlineMs
    }

    override fun setVolume(volume: Float) {
        activeSink.setVolume(volume)
    }

    override fun reset() {
        nativeSourceEnded = false
        nativeEndDeadlineMs = 0L
        activeSink.release()
        activeSink = DefaultSinkWrapper()
        activeSinkType = SinkType.DEFAULT
        isAAudioStreamActive = false
        isOboeStreamActive = false
    }

    override fun release() {
        UsbDacManager.removeListener(usbDacListener)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
        activeSink.release()
        if (defaultSinkDelegate.isInitialized()) defaultSink.release()
    }

    // -----------------------------------------------------------------------------------------
    // AudioSink implementation — ExoPlayer state methods delegated to DefaultAudioSink
    //
    // These are calls ExoPlayer makes for stream management, format queries, and renderer
    // setup. They all go to the DefaultAudioSink because it is the only implementation that
    // has the full AudioTrack machinery needed to answer them correctly.
    // -----------------------------------------------------------------------------------------

    override fun setListener(listener: AudioSink.Listener) {
        // Store the listener locally so native sinks (AAudio, Oboe, USB) can forward
        // position and state callbacks directly. We still delegate to DefaultAudioSink
        // so that the DEFAULT route works exactly as before.
        audioSinkListener = listener
        defaultSink.setListener(listener)
    }

    override fun supportsFormat(format: Format): Boolean {
        return defaultSink.supportsFormat(format)
    }

    override fun getFormatSupport(format: Format): Int {
        return defaultSink.getFormatSupport(format)
    }

    override fun hasPendingData(): Boolean {
        // Delegate to the active route so ExoPlayer knows the hardware buffer isn't empty
        return activeSink.hasPendingData()
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        defaultSink.playbackParameters = playbackParameters
    }

    override fun getPlaybackParameters(): PlaybackParameters {
        return defaultSink.playbackParameters
    }

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        defaultSink.setSkipSilenceEnabled(skipSilenceEnabled)
    }

    override fun getSkipSilenceEnabled(): Boolean {
        return defaultSink.skipSilenceEnabled
    }

    override fun handleDiscontinuity() {
        if (activeSinkType == SinkType.DEFAULT) {
            defaultSink.handleDiscontinuity()
        } else {
            // Notify ExoPlayer that the audio position has jumped (e.g. after a seek).
            // Without this the renderer may continue to report the old position and
            // the seekbar will not update until new audio data arrives.
            audioSinkListener?.onPositionDiscontinuity()
        }
    }

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        defaultSink.setAudioAttributes(audioAttributes)
    }

    override fun getAudioAttributes(): AudioAttributes {
        return defaultSink.getAudioAttributes()
    }

    override fun setAudioSessionId(audioSessionId: Int) {
        defaultSink.setAudioSessionId(audioSessionId)
    }

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
        defaultSink.setAuxEffectInfo(auxEffectInfo)
    }

    override fun getAudioTrackBufferSizeUs(): Long {
        return when (activeSinkType) {
            SinkType.DEFAULT -> defaultSink.getAudioTrackBufferSizeUs()
            SinkType.AAUDIO -> (activeSink as? AaudioNativeSink)
                ?.stream?.getLatencyMs()
                ?.coerceAtLeast(10)
                ?.let { it * 1000L }
                ?: 40_000L // ~40ms default for AAudio
            SinkType.OBOE -> (activeSink as? OboeNativeSink)
                ?.stream?.getLatencyMs()
                ?.coerceAtLeast(10)
                ?.let { it * 1000L }
                ?: 40_000L // ~40ms default for Oboe
            SinkType.USB -> 200_000L // ~200ms estimate for USB isochronous pipeline
        }
    }

    override fun enableTunnelingV21() {
        defaultSink.enableTunnelingV21()
    }

    override fun disableTunneling() {
        defaultSink.disableTunneling()
    }

    // -----------------------------------------------------------------------------------------
    // Routing helpers
    // -----------------------------------------------------------------------------------------

    /**
     * Figures out which [SinkType] should be active right now, given the current
     * USB connection state and the user's output preference. USB always wins when
     * a DAC is physically connected.
     */
    private fun targetSinkType(): SinkType = when {
        UsbDacManager.isActive -> SinkType.USB
        AudioPreferences.getOutputSink() == AudioPreferences.SINK_AAUDIO -> SinkType.AAUDIO
        AudioPreferences.getOutputSink() == AudioPreferences.SINK_OBOE -> SinkType.OBOE
        else -> SinkType.DEFAULT
    }

    /**
     * Opens and returns the appropriate [InternalSink] for [type]. If native stream
     * creation fails for any reason, it falls back transparently to [DefaultSinkWrapper]
     * so audio never stops playing due to a stream-open error.
     */
    private fun createSink(type: SinkType, sr: Int, ch: Int): InternalSink = when (type) {
        SinkType.DEFAULT -> DefaultSinkWrapper()

        SinkType.AAUDIO -> {
            val useSafeBuffers = isBluetoothOutputActive()
            if (useSafeBuffers) Log.i(TAG, "Bluetooth detected — opening AAudio stream in safe-buffer mode")
            val stream = AaudioOutputProcessor(sr, ch, useSafeBuffers)
            if (stream.isReady) {
                currentStreamSafeBufferMode = useSafeBuffers
                isAAudioStreamActive = true
                Log.i(TAG, "AAudio route created — ${sr} Hz / ${ch} ch / " +
                        "${stream.getActualFormatName()} / safeBuffers=$useSafeBuffers")
                AaudioNativeSink(stream, ch)
            } else {
                Log.e(TAG, "AAudio stream creation failed for ${sr} Hz / ${ch} ch — falling back to AudioTrack")
                isAAudioStreamActive = false
                DefaultSinkWrapper()
            }
        }

        SinkType.OBOE -> {
            val useSafeBuffers = isBluetoothOutputActive()
            if (useSafeBuffers) Log.i(TAG, "Bluetooth detected — opening Oboe stream in safe-buffer mode")
            val stream = OboeOutputProcessor(sr, ch, useSafeBuffers)
            if (stream.isReady) {
                currentStreamSafeBufferMode = useSafeBuffers
                isOboeStreamActive = true
                Log.i(TAG, "Oboe route created — ${sr} Hz / ${ch} ch / " +
                        "api=${stream.getActualApiName()} / safeBuffers=$useSafeBuffers")
                OboeNativeSink(stream, ch)
            } else {
                Log.e(TAG, "Oboe stream creation failed for ${sr} Hz / ${ch} ch — falling back to AudioTrack")
                isOboeStreamActive = false
                DefaultSinkWrapper()
            }
        }

        SinkType.USB -> {
            val bitDepth = encodingToBitDepth(currentEncoding)
            val driver = UsbDacDriver.getInstance(context)
            driver.negotiateFormat(sr, bitDepth, ch)
            Log.i(TAG, "USB DAC route created — ${sr} Hz / ${bitDepth}-bit / ${ch} ch")
            UsbNativeSink(driver, sr, ch)
        }
    }

    /**
     * Tears down the current native stream and opens a fresh one with the updated
     * Bluetooth-safe buffer mode. Called from [handleBuffer] on the audio thread
     * when [rerouteNeeded] is raised by [audioDeviceCallback].
     *
     * Skips the teardown entirely when the safe-buffer mode has not actually changed,
     * so connecting a non-audio Bluetooth device (e.g. a keyboard) never interrupts
     * playback.
     */
    private fun rerouteStreamForDeviceChange() {
        if (currentSampleRate <= 0 || currentChannelCount <= 0) return
        if (UsbDacManager.isActive) return

        val sink = AudioPreferences.getOutputSink()
        if (sink != AudioPreferences.SINK_AAUDIO && sink != AudioPreferences.SINK_OBOE) return

        val needSafeBuffers = isBluetoothOutputActive()
        if (needSafeBuffers == currentStreamSafeBufferMode && activeSink.isReady) {
            Log.d(TAG, "Route change detected but stream mode is already correct (safeBuffers=$needSafeBuffers) — skipping")
            return
        }

        Log.i(TAG, "Rerouting — safeBuffers: $currentStreamSafeBufferMode → $needSafeBuffers")
        activeSink.release()
        val targetType = if (sink == AudioPreferences.SINK_AAUDIO) SinkType.AAUDIO else SinkType.OBOE
        activeSink = createSink(targetType, currentSampleRate, currentChannelCount)
        activeSinkType = targetType
        if (!isPaused) activeSink.play()
    }

    // -----------------------------------------------------------------------------------------
    // PCM conversion helpers
    // -----------------------------------------------------------------------------------------

    /**
     * Converts the PCM bytes in [snapshot] to interleaved float32 and fills
     * [floatScratchBuffer]. The scratch buffer only grows when the incoming frame
     * needs more capacity than before — in steady state, no heap allocation occurs.
     *
     * @param snapshot  A read-only view of the raw PCM bytes for one render cycle.
     * @param encoding  The Media3 encoding constant describing the sample layout.
     * @return Number of valid samples written into [floatScratchBuffer], or 0 if empty.
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
     * Maps a Media3 PCM encoding constant to the closest integer bit depth for
     * use in USB DAC format negotiation.
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
     * currently connected, according to [AudioManager.getDevices].
     */
    private fun isBluetoothOutputActive(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return false
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.isBluetoothType() }
    }

    /**
     * Returns true if this [AudioDeviceInfo] is any flavor of Bluetooth audio output
     * (A2DP classic, SCO, or the BLE audio types added in Android 12).
     */
    private fun AudioDeviceInfo.isBluetoothType(): Boolean {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        (type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                                type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
    }

    companion object {
        private const val TAG = "FelicityAudioSink"

        /**
         * Whether the AAudio route is currently open and streaming.
         * The pipeline snapshot display reads this to show the real hardware state.
         */
        @Volatile
        var isAAudioStreamActive: Boolean = false

        /**
         * Whether the Oboe route is currently open and streaming.
         * The pipeline snapshot display reads this to show the real hardware state.
         */
        @Volatile
        var isOboeStreamActive: Boolean = false
    }
}
