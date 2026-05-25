package app.simple.felicity.engine.usb

import android.util.Log

/**
 * Process-wide singleton that tracks USB DAC connection state and notifies
 * the audio pipeline of attach/detach events.
 *
 * This object is the single source of truth that bridges three layers:
 *  - [UsbDacDriver] writes state (calls notifyAttached / notifyDetached).
 *  - [app.simple.felicity.engine.audio.AaudioAudioSink] reads state on every
 *    buffer cycle and syncs the DAC format when a format change is detected.
 *  - [app.simple.felicity.engine.services.FelicityPlayerService] registers a
 *    listener to rebuild the audio pipeline snapshot and log routing changes.
 *
 * Using a singleton avoids passing the driver reference through the whole
 * processor/sink chain while keeping the dependency graph acyclic.
 *
 * @author Hamza417
 */
object UsbDacManager {

    private const val TAG = "UsbDacManager"

    /**
     * True when a USB audio device is connected and the isochronous stream
     * is actively running. Read from the ExoPlayer audio thread on every buffer.
     */
    @Volatile
    var isActive: Boolean = false
        private set

    /**
     * The sample rate the USB DAC was last successfully negotiated at.
     * Zero when no DAC is connected.
     */
    @Volatile
    var activeSampleRate: Int = 0
        private set

    /**
     * The channel count the USB DAC was last successfully negotiated at.
     * Zero when no DAC is connected.
     */
    @Volatile
    var activeChannelCount: Int = 0
        private set

    /**
     * Receives notifications when a USB DAC is connected or disconnected.
     * Implementations must be thread-safe because calls may originate from
     * the USB receiver thread or the main thread.
     */
    interface Listener {
        /**
         * Called after the isochronous stream has started successfully.
         *
         * @param sampleRate  The sample rate the DAC was initially negotiated at.
         * @param channelCount The channel count (usually 2 for stereo).
         */
        fun onUsbDacAttached(sampleRate: Int, channelCount: Int)

        /** Called after the isochronous stream has been stopped and released. */
        fun onUsbDacDetached()
    }

    private val listeners = mutableListOf<Listener>()

    fun addListener(listener: Listener) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: Listener) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    /**
     * Called by [UsbDacDriver] after the isochronous stream starts. Marks the
     * DAC as active and notifies all registered listeners. Any listener that
     * holds the current ExoPlayer format should use this callback to re-negotiate
     * the USB format so it matches what is actually flowing through the pipeline.
     */
    internal fun notifyAttached(sampleRate: Int, channelCount: Int) {
        activeSampleRate = sampleRate
        activeChannelCount = channelCount
        isActive = true
        Log.i(TAG, "USB DAC attached — stream active at $sampleRate Hz / $channelCount ch")
        val snapshot = synchronized(listeners) { listeners.toList() }
        snapshot.forEach { it.onUsbDacAttached(sampleRate, channelCount) }
    }

    /**
     * Called by [UsbDacDriver] after the isochronous stream stops. Marks the
     * DAC as inactive and notifies all registered listeners so they can restore
     * normal audio routing.
     */
    internal fun notifyDetached() {
        isActive = false
        activeSampleRate = 0
        activeChannelCount = 0
        Log.i(TAG, "USB DAC detached — reverting to normal audio output")
        val snapshot = synchronized(listeners) { listeners.toList() }
        snapshot.forEach { it.onUsbDacDetached() }
    }
}

