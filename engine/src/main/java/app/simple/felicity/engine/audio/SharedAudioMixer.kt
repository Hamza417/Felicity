package app.simple.felicity.engine.audio

import android.util.Log
import androidx.media3.common.C
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A high-priority background thread that reads from multiple lock-free ring buffers (from VirtualAudioSinks),
 * applies crossfade gain formulas to the raw PCM arrays, sums the buffers into a single PCM array,
 * and pushes the final array into the global [FelicityAudioSink].
 *
 * This enables seamless crossfading between multiple ExoPlayer instances while maintaining
 * exclusive access to the underlying hardware stream (e.g., USB DAC, AAudio).
 */
class SharedAudioMixer(
        val hardwareSink: FelicityAudioSink
) {
    private val TAG = "SharedAudioMixer"
    private val isRunning = AtomicBoolean(false)
    private var mixerThread: Thread? = null

    // Configuration
    private var sampleRate: Int = 48000
    private var channelCount: Int = 2
    private var encoding: Int = C.ENCODING_PCM_16BIT

    // Define lock-free ring buffers for Player A and Player B here.
    // For now, we stub this out pending the RingBuffer implementation.

    fun start() {
        if (isRunning.getAndSet(true)) return

        mixerThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            Log.i(TAG, "SharedAudioMixer thread started")

            val mixBuffer = ByteBuffer.allocateDirect(4096) // Stub size

            while (isRunning.get()) {
                // 1. Read from Player A buffer
                // 2. Read from Player B buffer
                // 3. Apply crossfade gains
                // 4. Sum buffers into mixBuffer

                // Stub: push silence or pass-through
                mixBuffer.clear()
                // Fill with silence if no data
                mixBuffer.position(mixBuffer.capacity())
                mixBuffer.flip()

                if (mixBuffer.hasRemaining()) {
                    // Pass to hardware sink
                    // hardwareSink.handleBuffer(mixBuffer, presentationTime, count)
                }

                // Sleep briefly to avoid busy-looping if buffers are empty
                Thread.sleep(1)
            }
            Log.i(TAG, "SharedAudioMixer thread stopped")
        }.apply {
            name = "Felicity-AudioMixer"
            start()
        }
    }

    fun stop() {
        isRunning.set(false)
        mixerThread?.join(1000)
        mixerThread = null
    }

    fun configure(sampleRate: Int, channelCount: Int, encoding: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        this.encoding = encoding
        Log.i(TAG, "Mixer configured: ${sampleRate}Hz, ${channelCount}ch, encoding: $encoding")
    }
}
