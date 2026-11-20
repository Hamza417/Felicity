package app.simple.felicity.engine.audio

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import app.simple.felicity.engine.audio.SharedAudioMixer.Companion.BLOCK_FRAMES
import app.simple.felicity.preferences.AudioPreferences
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pulls decoded audio from the [VirtualAudioSink] of each ExoPlayer, brings every source to a
 * common format (sample rate, channel layout), applies the crossfade gains, sums the sources and
 * pushes the result to the one real [FelicityAudioSink].
 *
 * Threading: the mixer thread is the **only** thread that ever touches [hardwareSink]. The
 * virtual sinks only change thread-safe state and post whatever the hardware needs to hear
 * (play, pause, parameters) to this thread. Calling a `DefaultAudioSink` from two threads at once
 * is what used to null out its `AudioOutput` in the middle of a write.
 *
 * Output format: when the hardware is idle (first track, after a seek, after a stop) it adopts
 * the sample rate and channel count of the track that is about to play. While audio is flowing it
 * is never reconfigured, because reconfiguring a native stream cuts what is still buffered; a
 * following track with a different format (e.g. 44.1 kHz mp3 into 96 kHz flac) is converted
 * on the fly instead. The sample encoding is fixed by the hi-res preference: float when enabled,
 * 16-bit otherwise.
 */
@UnstableApi
class SharedAudioMixer(
        val hardwareSink: FelicityAudioSink,
        val floatOutput: Boolean = AudioPreferences.isHiresOutputEnabled()
) {
    private val TAG = "SharedAudioMixer"

    var sinkA: VirtualAudioSink? = null
    var sinkB: VirtualAudioSink? = null

    /** Called on the mixer thread with the faded-out sink once a crossfade has ended. */
    @Volatile
    var onCrossfadeComplete: ((VirtualAudioSink) -> Unit)? = null

    private val running = AtomicBoolean(false)
    private var thread: HandlerThread? = null

    @Volatile
    private var handler: Handler? = null

    // ---------------------------------------------------------------------------------------------
    // State below is owned by the mixer thread
    // ---------------------------------------------------------------------------------------------

    private val sources = ArrayList<Source>(2)
    private var fade: Fade? = null

    private var hwConfigured = false
    private var cfgRate = 0
    private var cfgChannels = 0
    private var outRate = 0
    private var outChannels = 0

    private var mixBuf = FloatArray(0)
    private var outBuf: ByteBuffer = ByteBuffer.allocateDirect(0)
    private var pending = false
    private var pendingPtsUs = 0L
    private var timelineFrames = 0L
    private var failures = 0

    private class Fade(val out: Source, val inn: Source, val durationMs: Long) {
        var started = false
        var totalFrames = 1L
        var doneFrames = 0L
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!running.get()) return
            val delay = try {
                cycle()
            } catch (t: Throwable) {
                Log.e(TAG, "Mixer cycle failed", t)
                ERROR_RETRY_MS
            }
            schedule(delay)
        }
    }

    fun start() {
        if (running.getAndSet(true)) return

        sinkA?.let { sources.add(Source(it)) }
        sinkB?.let { sources.add(Source(it)) }

        val t = HandlerThread("Felicity-AudioMixer", Process.THREAD_PRIORITY_URGENT_AUDIO)
        t.start()
        thread = t
        handler = Handler(t.looper)
        schedule(0)
        Log.i(TAG, "SharedAudioMixer thread started (float output: $floatOutput)")
    }

    /** Stops the thread and releases the hardware sink on it. */
    fun stop() {
        if (!running.getAndSet(false)) return
        val h = handler
        val t = thread
        handler = null
        thread = null
        h?.removeCallbacksAndMessages(null)
        h?.post {
            try {
                hardwareSink.release()
            } catch (e: Exception) {
                Log.e(TAG, "Hardware sink release failed", e)
            }
        }
        t?.quitSafely()
        try {
            t?.join(2000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        Log.i(TAG, "SharedAudioMixer thread stopped")
    }

    // ---------------------------------------------------------------------------------------------
    // Public control, callable from any thread
    // ---------------------------------------------------------------------------------------------

    /**
     * Starts fading [outgoing] out and [incoming] in. The ramp itself begins on the first frame
     * [incoming] delivers, so a slow-to-start decoder never makes the outgoing track dip.
     * [durationMs] must not exceed what is left of [outgoing].
     */
    fun beginCrossfade(outgoing: VirtualAudioSink, incoming: VirtualAudioSink, durationMs: Long) {
        post {
            completeFade(notify = false)
            val out = sourceOf(outgoing)
            val inn = sourceOf(incoming)
            if (out != null && inn != null) {
                fade = Fade(out, inn, durationMs.coerceAtLeast(1))
            }
        }
    }

    /** Ends a running crossfade right away; the incoming source continues alone at full level. */
    fun finishCrossfade() {
        post { completeFade(notify = true) }
    }

    fun wake() {
        schedule(0)
    }

    // ---------------------------------------------------------------------------------------------
    // Hooks used by the virtual sinks
    // ---------------------------------------------------------------------------------------------

    // Unconditional on purpose: FelicityAudioSink.requestImmediatePause() mutes the native stream
    // from the main thread and only play() lifts that mute.
    fun onSinkPlay() {
        post {
            guarded("play") { hardwareSink.play() }
            schedule(0)
        }
    }

    fun onSinkPause() {
        post { pauseHardwareIfIdle() }
    }

    fun onPlaybackParameters(parameters: PlaybackParameters) {
        post { guarded("setPlaybackParameters") { hardwareSink.playbackParameters = parameters } }
    }

    fun onSkipSilence(enabled: Boolean) {
        post { guarded("setSkipSilenceEnabled") { hardwareSink.skipSilenceEnabled = enabled } }
    }

    fun onAudioAttributes(attributes: AudioAttributes) {
        post { guarded("setAudioAttributes") { hardwareSink.audioAttributes = attributes } }
    }

    // ---------------------------------------------------------------------------------------------
    // Mixer thread
    // ---------------------------------------------------------------------------------------------

    private fun post(block: () -> Unit) {
        handler?.post {
            if (running.get()) block()
        }
    }

    private fun schedule(delayMs: Long) {
        val h = handler ?: return
        // Remove-then-post keeps a single pending loop even when wake() races the loop itself.
        h.removeCallbacks(loop)
        if (delayMs <= 0) h.post(loop) else h.postDelayed(loop, delayMs)
    }

    private inline fun guarded(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Hardware sink $what failed", e)
        }
    }

    private fun sourceOf(sink: VirtualAudioSink): Source? = sources.firstOrNull { it.sink === sink }

    private fun pauseHardwareIfIdle() {
        if (sources.none { it.sink.isActive }) {
            guarded("pause") { hardwareSink.pause() }
        }
    }

    /** @return milliseconds until the next cycle. */
    private fun cycle(): Long {
        for (s in sources) syncFlush(s, s.sink.flushGeneration)

        if (pending) {
            return when {
                pushPending() -> 0L
                failures > 0 -> ERROR_RETRY_MS
                sources.none { it.sink.isActive } -> IDLE_MS
                else -> BUSY_RETRY_MS
            }
        }

        if (sources.none { it.sink.isActive }) return IDLE_MS

        if (!hwConfigured) {
            val primary = fade?.out?.takeIf { it.sink.isActive && it.sink.hasQueuedData() }
                ?: sources.firstOrNull { it.sink.isActive && it.sink.hasQueuedData() }
                ?: return STARVED_MS
            val format = primary.sink.peekFormat()
            if (format < 0 || !configureHardware((format shr 8).toInt(), (format and 0xFF).toInt())) {
                return STARVED_MS
            }
        }

        for (s in sources) {
            if (s.sink.isActive) fill(s)
        }

        val f = fade
        if (f != null) {
            if (f.out.readyFrames == 0 && (f.out.sink.isDrained() || !f.out.sink.isActive)) {
                completeFade(notify = true)
            } else if (!f.started && f.inn.sink.isActive && f.inn.readyFrames > 0) {
                f.started = true
                f.totalFrames = max(1L, f.durationMs * outRate / 1000L)
                f.doneFrames = 0L
            }
        }

        return if (mixBlock()) 0L else STARVED_MS
    }

    /**
     * Reacts to a flush of [s]. The hardware may only be flushed when the other source is not
     * audible, otherwise the pre-roll of the next track would cut the current one off.
     */
    private fun syncFlush(s: Source, generation: Int) {
        if (generation == s.seenGeneration) return
        s.seenGeneration = generation
        s.reset()
        val other = sources.firstOrNull { it !== s }
        if (other == null || !other.sink.isActive) {
            hardwareFlush()
        }
    }

    private fun hardwareFlush() {
        pending = false
        failures = 0
        timelineFrames = 0L
        hwConfigured = false
        for (s in sources) s.reset()
        guarded("flush") { hardwareSink.flush() }
    }

    private fun configureHardware(rate: Int, channels: Int): Boolean {
        if (rate <= 0 || channels <= 0) return false
        if (rate != cfgRate || channels != cfgChannels) {
            val format = Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setPcmEncoding(if (floatOutput) C.ENCODING_PCM_FLOAT else C.ENCODING_PCM_16BIT)
                .setSampleRate(rate)
                .setChannelCount(channels)
                .build()
            try {
                @Suppress("DEPRECATION")
                hardwareSink.configure(format, 0, null)
            } catch (e: Exception) {
                Log.e(TAG, "Hardware sink rejected ${rate}Hz/${channels}ch", e)
                cfgRate = 0
                cfgChannels = 0
                return false
            }
            cfgRate = rate
            cfgChannels = channels
            Log.i(TAG, "Hardware configured: ${rate}Hz, ${channels}ch, ${if (floatOutput) "float" else "16-bit"}")
        }

        outRate = rate
        outChannels = channels
        if (mixBuf.size < BLOCK_FRAMES * channels) mixBuf = FloatArray(BLOCK_FRAMES * channels)
        val bytes = BLOCK_FRAMES * channels * if (floatOutput) 4 else 2
        if (outBuf.capacity() < bytes) outBuf = ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN)
        hwConfigured = true
        return true
    }

    private fun fill(s: Source) {
        var reads = 0
        while (s.readyFrames < BLOCK_FRAMES && reads++ < MAX_READS_PER_CYCLE) {
            val frames = s.sink.read(s.block, READ_FRAMES)
            if (frames <= 0) return
            if (s.block.generation != s.seenGeneration) {
                // A flush landed between the cycle start and this read.
                syncFlush(s, s.block.generation)
                if (!hwConfigured && !configureHardware(s.block.sampleRate, s.block.channels)) return
            }
            s.append(outRate, outChannels, s.sink.softwareVolume)
        }
    }

    /** Mixes one block of at most [BLOCK_FRAMES] frames into [outBuf]. */
    private fun mixBlock(): Boolean {
        val channels = outChannels
        val f = fade
        val ramp = f != null && f.started

        // Until the incoming source has produced its first frame only the outgoing one is heard.
        fun audible(s: Source) = s.sink.isActive && (f == null || ramp || s === f.out)

        var n = 0
        for (s in sources) {
            if (audible(s)) n = max(n, s.readyFrames)
        }
        n = min(n, BLOCK_FRAMES)
        if (n == 0) return false

        java.util.Arrays.fill(mixBuf, 0, n * channels, 0f)

        for (s in sources) {
            if (!audible(s)) continue
            val take = min(n, s.readyFrames)
            if (take == 0) continue
            if (f == null || !ramp) {
                for (i in 0 until take * channels) mixBuf[i] += s.ready[i]
            } else {
                val isOut = s === f.out
                for (j in 0 until take) {
                    val t = min(1.0, (f.doneFrames + j).toDouble() / f.totalFrames)
                    val angle = t * PI / 2
                    val gain = (if (isOut) cos(angle) else sin(angle)).toFloat()
                    val base = j * channels
                    for (c in 0 until channels) mixBuf[base + c] += s.ready[base + c] * gain
                }
            }
            s.consume(take, channels)
        }

        writeOutput(n, channels)

        if (f != null && ramp) {
            f.doneFrames += n
            if (f.doneFrames >= f.totalFrames) completeFade(notify = true)
        }
        return true
    }

    private fun writeOutput(frames: Int, channels: Int) {
        val samples = frames * channels
        outBuf.clear()
        if (floatOutput) {
            for (i in 0 until samples) outBuf.putFloat(i * 4, mixBuf[i])
            outBuf.limit(samples * 4)
        } else {
            for (i in 0 until samples) {
                val v = (mixBuf[i] * 32768f).roundToInt().coerceIn(-32768, 32767)
                outBuf.putShort(i * 2, v.toShort())
            }
            outBuf.limit(samples * 2)
        }
        outBuf.position(0)
        pendingPtsUs = timelineFrames * 1_000_000L / outRate
        timelineFrames += frames
        pending = true
        pushPending()
    }

    /** @return true when the hardware took the whole pending buffer. */
    private fun pushPending(): Boolean {
        return try {
            val accepted = hardwareSink.pushMixedBuffer(outBuf, pendingPtsUs)
            if (accepted) {
                pending = false
                failures = 0
            }
            accepted
        } catch (e: Exception) {
            failures++
            Log.e(TAG, "Hardware write failed ($failures), resetting output", e)
            // Whatever state the hardware is in, flush and reconfigure it on the next cycle.
            hardwareFlush()
            failures = failures.coerceAtLeast(1)
            false
        }
    }

    private fun completeFade(notify: Boolean) {
        val f = fade ?: return
        fade = null
        f.out.sink.mute()
        f.out.reset()
        pauseHardwareIfIdle()
        if (notify) onCrossfadeComplete?.invoke(f.out.sink)
    }

    // ---------------------------------------------------------------------------------------------
    // Per-source conversion
    // ---------------------------------------------------------------------------------------------

    /**
     * Holds a source's audio after conversion to the output format. Conversion is a channel
     * matrix followed by a streaming Catmull-Rom resampler whose history survives between reads.
     */
    private class Source(val sink: VirtualAudioSink) {
        val block = VirtualAudioSink.SourceBlock()
        var seenGeneration = sink.flushGeneration

        var ready = FloatArray(0)
        var readyFrames = 0

        private var srcRate = 0
        private var srcChannels = 0
        private var dstRate = 0
        private var dstChannels = 0
        private var matrix: FloatArray? = null
        private var step = 1.0
        private var frac = 0.0
        private var primed = false
        private var history = FloatArray(0)
        private var mapped = FloatArray(0)

        fun reset() {
            readyFrames = 0
            primed = false
            frac = 0.0
        }

        fun consume(frames: Int, channels: Int) {
            val remaining = readyFrames - frames
            if (remaining > 0) {
                System.arraycopy(ready, frames * channels, ready, 0, remaining * channels)
            }
            readyFrames = max(0, remaining)
        }

        private fun reconfigure(outRate: Int, outChannels: Int) {
            srcRate = block.sampleRate
            srcChannels = block.channels
            dstRate = outRate
            dstChannels = outChannels
            matrix = buildMatrix(srcChannels, dstChannels)
            step = srcRate.toDouble() / dstRate
            history = FloatArray(4 * dstChannels)
            primed = false
            frac = 0.0
        }

        fun append(outRate: Int, outChannels: Int, volume: Float) {
            if (block.sampleRate != srcRate || block.channels != srcChannels
                    || outRate != dstRate || outChannels != dstChannels) {
                reconfigure(outRate, outChannels)
            }

            val frames = block.frames
            val src: FloatArray
            val m = matrix
            if (m == null) {
                src = block.data
            } else {
                if (mapped.size < frames * outChannels) mapped = FloatArray(frames * outChannels)
                for (f in 0 until frames) {
                    val inBase = f * srcChannels
                    val outBase = f * outChannels
                    for (o in 0 until outChannels) {
                        var acc = 0f
                        val row = o * srcChannels
                        for (i in 0 until srcChannels) acc += m[row + i] * block.data[inBase + i]
                        mapped[outBase + o] = acc
                    }
                }
                src = mapped
            }

            if (srcRate == dstRate) {
                ensureCapacity((readyFrames + frames) * outChannels)
                val base = readyFrames * outChannels
                for (i in 0 until frames * outChannels) ready[base + i] = src[i] * volume
                readyFrames += frames
                return
            }

            ensureCapacity((readyFrames + (frames / step).toInt() + 8) * outChannels)

            for (f in 0 until frames) {
                val base = f * outChannels
                if (!primed) {
                    for (k in 0 until 4) for (c in 0 until outChannels) history[k * outChannels + c] = src[base + c]
                    primed = true
                    frac = 0.0
                } else {
                    for (c in 0 until outChannels) {
                        history[c] = history[outChannels + c]
                        history[outChannels + c] = history[2 * outChannels + c]
                        history[2 * outChannels + c] = history[3 * outChannels + c]
                        history[3 * outChannels + c] = src[base + c]
                    }
                }
                while (frac < 1.0) {
                    val t = frac.toFloat()
                    val out = readyFrames * outChannels
                    for (c in 0 until outChannels) {
                        val p0 = history[c]
                        val p1 = history[outChannels + c]
                        val p2 = history[2 * outChannels + c]
                        val p3 = history[3 * outChannels + c]
                        val v = p1 + 0.5f * t * (p2 - p0 + t * (2f * p0 - 5f * p1 + 4f * p2 - p3 +
                                t * (3f * (p1 - p2) + p3 - p0)))
                        ready[out + c] = v * volume
                    }
                    readyFrames++
                    frac += step
                }
                frac -= 1.0
            }
        }

        private fun ensureCapacity(samples: Int) {
            if (ready.size < samples) ready = ready.copyOf(max(samples, ready.size * 2))
        }

        /** Row-major `[dst][src]` matrix, or null when the layouts already match. */
        private fun buildMatrix(src: Int, dst: Int): FloatArray? {
            if (src == dst) return null
            val m = FloatArray(dst * src)
            when {
                dst == 1 -> for (i in 0 until src) m[i] = 1f / src
                src == 1 -> for (o in 0 until min(dst, 2)) m[o] = 1f
                dst == 2 -> downmixToStereo(src, m)
                else -> for (o in 0 until min(src, dst)) m[o * src + o] = 1f
            }
            return m
        }

        /** Assumes the usual WAV/ITU channel order: L R C LFE Ls Rs (Lb Rb). The LFE is dropped. */
        private fun downmixToStereo(src: Int, m: FloatArray) {
            val c = 0.7071f
            fun set(o: Int, i: Int, w: Float) {
                m[o * src + i] = w
            }
            set(0, 0, 1f)
            set(1, 1, 1f)
            when (src) {
                3 -> {
                    set(0, 2, c); set(1, 2, c)
                }
                4 -> {
                    set(0, 2, c); set(1, 3, c)
                }
                5 -> {
                    set(0, 2, c); set(1, 2, c); set(0, 3, c); set(1, 4, c)
                }
                6 -> {
                    set(0, 2, c); set(1, 2, c); set(0, 4, c); set(1, 5, c)
                }
                8 -> {
                    set(0, 2, c); set(1, 2, c); set(0, 4, c); set(1, 5, c); set(0, 6, c); set(1, 7, c)
                }
                else -> for (i in 2 until src) set(i % 2, i, c)
            }
            for (o in 0 until 2) {
                var sum = 0f
                for (i in 0 until src) sum += m[o * src + i]
                if (sum > 1f) for (i in 0 until src) m[o * src + i] /= sum
            }
        }
    }

    companion object {
        private const val BLOCK_FRAMES = 1024
        private const val READ_FRAMES = 1024
        private const val MAX_READS_PER_CYCLE = 16
        private const val IDLE_MS = 20L
        private const val STARVED_MS = 4L
        private const val BUSY_RETRY_MS = 3L
        private const val ERROR_RETRY_MS = 100L
    }
}
