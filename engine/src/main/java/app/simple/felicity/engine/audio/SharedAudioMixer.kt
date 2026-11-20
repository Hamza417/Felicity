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
import app.simple.felicity.engine.audio.SharedAudioMixer.Companion.CLIP_KNEE
import app.simple.felicity.preferences.AudioPreferences
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

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

    /**
     * Called on the mixer thread with the sink and the token of its gain envelope whenever an
     * envelope has finished (or its source drained while it was fading out).
     */
    @Volatile
    var onCrossfadeComplete: ((VirtualAudioSink, Int) -> Unit)? = null

    private val running = AtomicBoolean(false)
    private var thread: HandlerThread? = null

    @Volatile
    private var handler: Handler? = null

    // ---------------------------------------------------------------------------------------------
    // State below is owned by the mixer thread
    // ---------------------------------------------------------------------------------------------

    private val sources = ArrayList<Source>(2)

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

    private enum class FadeCurve { EQUAL_POWER_IN, EQUAL_POWER_OUT, LINEAR }

    /**
     * The gain ramp of a single source. Every source owns one, so ramps never depend on each
     * other and one of them can be restarted while the other keeps running.
     */
    private class GainEnvelope {
        var active = false
        var waitForFirstFrame = false
        var startGain = 1f
        var targetGain = 1f
        var currentGain = 1f
        var doneFrames = 0L
        var totalFrames = 1L
        var curve = FadeCurve.LINEAR
        var muteOnComplete = false
        var token = 0

        /** Ramp length, converted to [totalFrames] once the ramp is allowed to start. */
        var durationMs = 0L

        /** Source whose first frame releases the wait; the envelope's own source when null. */
        var gate: Source? = null

        /** Whether finishing reports to [SharedAudioMixer.onCrossfadeComplete]. */
        var notify = true

        fun configure(
                start: Float,
                target: Float,
                curve: FadeCurve,
                durationMs: Long,
                waitForFirstFrame: Boolean,
                muteOnComplete: Boolean,
                gate: Source?,
                notify: Boolean
        ) {
            this.startGain = start
            this.targetGain = target
            this.currentGain = start
            this.curve = curve
            this.durationMs = durationMs
            this.waitForFirstFrame = waitForFirstFrame
            this.muteOnComplete = muteOnComplete
            this.gate = gate
            this.notify = notify
            this.doneFrames = 0L
            this.totalFrames = 1L
            this.active = true
        }

        /** Gain after [done] of [totalFrames] frames of the ramp. */
        fun gainAt(done: Long): Float {
            if (done >= totalFrames) return targetGain
            val t = done.toDouble() / totalFrames
            val delta = targetGain - startGain
            return when (curve) {
                FadeCurve.EQUAL_POWER_IN -> startGain + delta * sin(t * PI / 2).toFloat()
                FadeCurve.EQUAL_POWER_OUT -> startGain + delta * (1.0 - cos(t * PI / 2)).toFloat()
                FadeCurve.LINEAR -> startGain + delta * t.toFloat()
            }
        }
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
     * Starts fading [outgoing] out and [incoming] in. Both ramps begin on the first frame
     * [incoming] delivers, so a slow-to-start decoder never makes the outgoing track dip.
     * [durationMs] must not exceed what is left of [outgoing]. The [token] is handed back through
     * [onCrossfadeComplete] so stale completions can be told apart.
     */
    fun beginCrossfade(outgoing: VirtualAudioSink, incoming: VirtualAudioSink, durationMs: Long, token: Int) {
        post {
            val out = sourceOf(outgoing)
            val inn = sourceOf(incoming)
            if (out == null || inn == null) return@post
            val duration = durationMs.coerceAtLeast(1)

            out.envelope.configure(
                    start = out.effectiveGain(), target = 0f, curve = FadeCurve.EQUAL_POWER_OUT,
                    durationMs = duration, waitForFirstFrame = true, muteOnComplete = true,
                    gate = inn, notify = true)
            out.envelope.token = token

            inn.envelope.configure(
                    start = 0f, target = 1f, curve = FadeCurve.EQUAL_POWER_IN,
                    durationMs = duration, waitForFirstFrame = true, muteOnComplete = false,
                    gate = null, notify = true)
            inn.envelope.token = token
            schedule(0)
        }
    }

    /**
     * Restarts only the fade-in of [incoming], for when it was switched to another track while
     * the crossfade was running. The fade-out of the other source is left alone.
     */
    fun retargetIncoming(incoming: VirtualAudioSink, durationMs: Long) {
        post {
            val inn = sourceOf(incoming) ?: return@post
            val token = inn.envelope.token
            inn.envelope.configure(
                    start = 0f, target = 1f, curve = FadeCurve.EQUAL_POWER_IN,
                    durationMs = durationMs.coerceAtLeast(1), waitForFirstFrame = true,
                    muteOnComplete = false, gate = null, notify = true)
            inn.envelope.token = token
            schedule(0)
        }
    }

    /**
     * Ramps [outgoing] from wherever it is down to silence in [microFadeMs] and mutes it, so an
     * aborted crossfade does not end in a click. [onCrossfadeComplete] is only called for it
     * when [notify] is true.
     */
    fun abortOutgoing(outgoing: VirtualAudioSink, microFadeMs: Long = 50L, notify: Boolean = false) {
        post {
            val out = sourceOf(outgoing) ?: return@post
            val token = out.envelope.token
            out.envelope.configure(
                    start = out.effectiveGain(), target = 0f, curve = FadeCurve.LINEAR,
                    durationMs = microFadeMs.coerceAtLeast(1), waitForFirstFrame = false,
                    muteOnComplete = true, gate = null, notify = notify)
            out.envelope.token = token
            out.envelope.totalFrames = framesFor(out.envelope.durationMs)
            schedule(0)
        }
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
        for (i in sources.indices) {
            val s = sources[i]
            syncFlush(s, s.sink.flushGeneration)
        }

        settleEnvelopes()

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
            val primary = sources.firstOrNull {
                it.envelope.active && it.envelope.muteOnComplete && it.sink.isActive && it.sink.hasQueuedData()
            } ?: sources.firstOrNull { it.sink.isActive && it.sink.hasQueuedData() }
            ?: return STARVED_MS
            val format = primary.sink.peekFormat()
            if (format < 0 || !configureHardware((format shr 8).toInt(), (format and 0xFF).toInt())) {
                return STARVED_MS
            }
        }

        for (i in sources.indices) {
            val s = sources[i]
            if (s.sink.isActive) fill(s)
        }

        armEnvelopes()

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

        // A fade-in that was already running belongs to the track that was just flushed away;
        // whatever plays next fades in from silence again.
        val env = s.envelope
        if (env.active && env.curve == FadeCurve.EQUAL_POWER_IN && !env.waitForFirstFrame) {
            env.waitForFirstFrame = true
            env.doneFrames = 0L
            env.currentGain = env.startGain
        }

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
            s.append(outRate, outChannels, s.sink.softwareVolume * s.sink.replayGainLinear)
        }
    }

    /** Mixes one block of at most [BLOCK_FRAMES] frames into [outBuf]. */
    private fun mixBlock(): Boolean {
        val channels = outChannels

        var n = 0
        for (i in sources.indices) {
            val s = sources[i]
            if (s.sink.isActive) n = max(n, s.readyFrames)
        }
        n = min(n, BLOCK_FRAMES)
        if (n == 0) return false

        java.util.Arrays.fill(mixBuf, 0, n * channels, 0f)

        for (i in sources.indices) {
            val s = sources[i]
            if (!s.sink.isActive) continue
            val take = min(n, s.readyFrames)
            if (take == 0) continue
            val env = s.envelope
            if (!env.active) {
                for (k in 0 until take * channels) mixBuf[k] += s.ready[k]
            } else if (env.waitForFirstFrame) {
                val gain = env.currentGain
                for (k in 0 until take * channels) mixBuf[k] += s.ready[k] * gain
            } else {
                // Each source advances its own ramp by the frames it actually contributes.
                var done = env.doneFrames
                var gain = env.currentGain
                for (j in 0 until take) {
                    gain = env.gainAt(done)
                    if (done < env.totalFrames) done++
                    val base = j * channels
                    for (c in 0 until channels) mixBuf[base + c] += s.ready[base + c] * gain
                }
                env.doneFrames = done
                env.currentGain = gain
            }
            s.consume(take, channels)
        }

        writeOutput(n, channels)

        for (i in sources.indices) {
            val s = sources[i]
            val env = s.envelope
            if (env.active && !env.waitForFirstFrame && env.doneFrames >= env.totalFrames) finishEnvelope(s)
        }
        return true
    }

    /**
     * Transparent up to [CLIP_KNEE], then a tanh roll-off that approaches full scale. It has no
     * gain change below the knee, so a lone source plays identically before and after a crossfade
     * while a loud overlap of two sources is still kept in range.
     */
    private fun softClip(x: Float): Float {
        val a = abs(x)
        if (a <= CLIP_KNEE) return x
        val shaped = CLIP_KNEE + CLIP_HEADROOM * tanh((a - CLIP_KNEE) / CLIP_HEADROOM)
        return if (x < 0f) -shaped else shaped
    }

    private fun writeOutput(frames: Int, channels: Int) {
        val samples = frames * channels
        outBuf.clear()
        if (floatOutput) {
            for (i in 0 until samples) outBuf.putFloat(i * 4, mixBuf[i])
            outBuf.limit(samples * 4)
        } else {
            for (i in 0 until samples) {
                val v = (softClip(mixBuf[i]) * 32768f).roundToInt().coerceIn(-32768, 32767)
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

    /** Finishes the envelope of every source that has nothing left to fade. */
    private fun settleEnvelopes() {
        for (i in sources.indices) {
            val s = sources[i]
            val env = s.envelope
            if (env.active && env.muteOnComplete && s.readyFrames == 0 &&
                    (s.sink.isDrained() || !s.sink.isActive)) {
                finishEnvelope(s)
            }
        }
    }

    /** Lets waiting envelopes start once the audio they wait for has arrived. */
    private fun armEnvelopes() {
        for (i in sources.indices) {
            val s = sources[i]
            val env = s.envelope
            if (!env.active || !env.waitForFirstFrame) continue
            val gate = env.gate ?: s
            if (gate.sink.isActive && gate.readyFrames > 0) {
                env.waitForFirstFrame = false
                env.totalFrames = framesFor(env.durationMs)
                env.doneFrames = 0L
            } else {
                env.currentGain = env.startGain
            }
        }
    }

    private fun framesFor(durationMs: Long): Long = max(1L, durationMs * outRate / 1000L)

    private fun finishEnvelope(s: Source) {
        val env = s.envelope
        env.currentGain = env.targetGain
        env.active = false
        env.waitForFirstFrame = false
        if (env.muteOnComplete) {
            s.sink.mute()
            s.reset()
        }
        pauseHardwareIfIdle()
        if (env.notify) onCrossfadeComplete?.invoke(s.sink, env.token)
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
        val envelope = GainEnvelope()
        var seenGeneration = sink.flushGeneration

        var ready = FloatArray(0)
        var readyFrames = 0

        /** Gain this source plays at right now; sources without a running envelope are at unity. */
        fun effectiveGain(): Float = if (envelope.active) envelope.currentGain else 1f

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
        private const val CLIP_KNEE = 0.85f
        private const val CLIP_HEADROOM = 1f - CLIP_KNEE
    }
}
