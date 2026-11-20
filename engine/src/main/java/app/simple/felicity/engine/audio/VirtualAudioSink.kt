package app.simple.felicity.engine.audio

import android.media.AudioDeviceInfo
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The [AudioSink] seen by a single ExoPlayer instance.
 *
 * It never touches hardware. Every decoded PCM buffer is converted to interleaved float and parked
 * in a queue together with its own sample rate and channel count, so a track with a different
 * format (mp3 -> flac, 44.1 kHz -> 96 kHz, MediaCodec 16-bit -> FFmpeg float...) can follow another
 * one without any reconfiguration. The [SharedAudioMixer] drains that queue on its own thread,
 * converts it to the hardware format and is the only owner of the real sink.
 *
 * Everything that is shared between the ExoPlayer playback thread and the mixer thread is guarded
 * by [lock].
 */
@UnstableApi
class VirtualAudioSink(
        private val mixer: SharedAudioMixer
) : AudioSink {

    /** One decoded buffer, already converted to interleaved float. */
    private class Chunk(
            val data: FloatArray,
            val frames: Int,
            val sampleRate: Int,
            val channels: Int
    ) {
        var offsetFrames = 0
    }

    /** Reusable holder the mixer reads into so no allocation happens per read. */
    class SourceBlock {
        var data = FloatArray(0)
        var frames = 0
        var sampleRate = 0
        var channels = 0
        var generation = 0
    }

    private val lock = Any()
    private val queue = ArrayDeque<Chunk>()
    private var queuedUs = 0.0

    private var listener: AudioSink.Listener? = null

    // Format of the buffers coming from the renderer right now.
    private var cfgSampleRate = 0
    private var cfgChannels = 0
    private var cfgEncoding = C.ENCODING_INVALID

    // Position bookkeeping, identical in spirit to DefaultAudioSink.
    private var started = false
    private var startMediaTimeUs = 0L
    private var startNeedsSync = false
    private var submittedUs = 0.0
    private var consumedUs = 0.0

    @Volatile
    var isPlaying = false
        private set

    @Volatile
    var endOfStream = false
        private set

    /** Set by the mixer once this sink was faded out. Audio is dropped until the next [flush]. */
    @Volatile
    var isMuted = false
        private set

    private var processors = emptyArray<AudioProcessor>()
    private var activeProcessors = emptyArray<AudioProcessor>()

    fun setAudioProcessors(processors: Array<AudioProcessor>) {
        this.processors = processors
    }

    /** Incremented on every flush so the mixer can discard anything it converted before it. */
    @Volatile
    var flushGeneration = 0
        private set

    @Volatile
    var softwareVolume = 1.0f
        private set

    /**
     * Linear ReplayGain of the track currently feeding this sink. Kept apart from
     * [softwareVolume] so ExoPlayer's audio-focus ducking via [setVolume] never overwrites it,
     * and deliberately not reset by [flush] so a seek within the same track keeps its gain.
     * Applied per source by the mixer, which lets an outgoing track fade with its own gain.
     */
    @Volatile
    var replayGainLinear = 1.0f

    val isActive: Boolean
        get() = isPlaying && !isMuted

    // ---------------------------------------------------------------------------------------------
    // Mixer side
    // ---------------------------------------------------------------------------------------------

    fun hasQueuedData(): Boolean = synchronized(lock) { queue.isNotEmpty() }

    /** True once the renderer signalled the end of the stream and everything was consumed. */
    fun isDrained(): Boolean = synchronized(lock) { endOfStream && queue.isEmpty() }

    /** Returns `(sampleRate shl 8) or channels` of the next chunk or -1 when nothing is queued. */
    fun peekFormat(): Long = synchronized(lock) {
        val chunk = queue.firstOrNull() ?: return -1L
        (chunk.sampleRate.toLong() shl 8) or chunk.channels.toLong()
    }

    /**
     * Copies up to [maxFrames] frames from the head chunk into [block]. A read never crosses a
     * chunk boundary, so [SourceBlock.sampleRate] and [SourceBlock.channels] always describe the
     * whole block.
     */
    fun read(block: SourceBlock, maxFrames: Int): Int = synchronized(lock) {
        block.generation = flushGeneration
        block.frames = 0
        if (!isActive) return 0
        val chunk = queue.firstOrNull() ?: return 0

        val frames = minOf(maxFrames, chunk.frames - chunk.offsetFrames)
        val samples = frames * chunk.channels
        if (block.data.size < samples) {
            block.data = FloatArray(samples)
        }
        System.arraycopy(chunk.data, chunk.offsetFrames * chunk.channels, block.data, 0, samples)
        block.frames = frames
        block.sampleRate = chunk.sampleRate
        block.channels = chunk.channels

        chunk.offsetFrames += frames
        if (chunk.offsetFrames >= chunk.frames) {
            queue.removeFirst()
        }

        val durationUs = frames * 1_000_000.0 / chunk.sampleRate
        consumedUs += durationUs
        queuedUs = (queuedUs - durationUs).coerceAtLeast(0.0)
        frames
    }

    /** Drops everything and ignores further audio until the next [flush]. */
    fun mute() {
        synchronized(lock) {
            isMuted = true
            queue.clear()
            queuedUs = 0.0
        }
    }

    // ---------------------------------------------------------------------------------------------
    // ExoPlayer side
    // ---------------------------------------------------------------------------------------------

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
    }

    override fun setPlayerId(playerId: PlayerId?) {}

    override fun setClock(clock: Clock) {}

    override fun supportsFormat(format: Format): Boolean {
        return getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED
    }

    /**
     * Only linear little-endian PCM is accepted. Compressed formats must be refused, otherwise
     * ExoPlayer picks the passthrough decoder and feeds encoded bytes to this sink as if they
     * were PCM.
     */
    override fun getFormatSupport(format: Format): Int {
        if (format.sampleMimeType != MimeTypes.AUDIO_RAW) return AudioSink.SINK_FORMAT_UNSUPPORTED
        if (format.channelCount !in 1..MAX_CHANNELS) return AudioSink.SINK_FORMAT_UNSUPPORTED
        return when (format.pcmEncoding) {
            C.ENCODING_PCM_16BIT -> AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
            // Decoders only produce float when we say so, which keeps 16-bit mode on 16-bit.
            C.ENCODING_PCM_FLOAT ->
                if (mixer.floatOutput) AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
                else AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING
            C.ENCODING_PCM_8BIT,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT -> AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING
            else -> AudioSink.SINK_FORMAT_UNSUPPORTED
        }
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long = synchronized(lock) {
        if (!started) return AudioSink.CURRENT_POSITION_NOT_SET
        startMediaTimeUs + consumedUs.toLong()
    }

    override fun getAudioTrackBufferSizeUs(): Long = MAX_QUEUED_US

    @Suppress("OVERRIDE_DEPRECATION")
    override fun configure(
            inputFormat: Format,
            specifiedBufferSize: Int,
            outputChannels: IntArray?
    ) {
        if (getFormatSupport(inputFormat) == AudioSink.SINK_FORMAT_UNSUPPORTED
                || inputFormat.sampleRate <= 0) {
            throw AudioSink.ConfigurationException("Unsupported sink format", inputFormat)
        }
        synchronized(lock) {
            var format = AudioProcessor.AudioFormat(
                    inputFormat.sampleRate,
                    inputFormat.channelCount,
                    inputFormat.pcmEncoding
            )

            val activeList = ArrayList<AudioProcessor>()

            for (p in processors) {
                try {
                    val nextFormat = p.configure(format)
                    if (p.isActive) {
                        activeList.add(p)
                        format = nextFormat
                    }
                } catch (e: AudioProcessor.UnhandledAudioFormatException) {
                    throw AudioSink.ConfigurationException(e, inputFormat)
                }
            }

            activeProcessors = activeList.toTypedArray()

            cfgSampleRate = format.sampleRate
            cfgChannels = format.channelCount
            cfgEncoding = format.encoding

            for (p in activeProcessors) p.flush()
        }
    }

    override fun play() {
        isPlaying = true
        mixer.onSinkPlay()
    }

    override fun pause() {
        isPlaying = false
        mixer.onSinkPause()
    }

    /** Only remembers that the next buffer may not follow the previous one. Nothing is dropped. */
    override fun handleDiscontinuity() {
        synchronized(lock) { startNeedsSync = true }
    }

    override fun handleBuffer(
            buffer: ByteBuffer,
            presentationTimeUs: Long,
            encodedAccessUnitCount: Int
    ): Boolean {
        if (!buffer.hasRemaining()) return true

        if (isMuted) {
            buffer.position(buffer.limit())
            return true
        }

        var discontinuityReported = false
        synchronized(lock) {
            if (cfgEncoding == C.ENCODING_INVALID) {
                throw IllegalStateException("handleBuffer called before configure")
            }
            // Back pressure on duration, not on chunk count, so it is independent of the
            // buffer size the decoder happens to use.
            if (queuedUs >= MAX_QUEUED_US) return false

            var currentBuffer = buffer
            for (p in activeProcessors) {
                p.queueInput(currentBuffer)
                currentBuffer = p.output
            }

            val chunk = convert(currentBuffer)
            if (!started) {
                started = true
                startMediaTimeUs = presentationTimeUs
                startNeedsSync = false
            } else {
                val expectedUs = startMediaTimeUs + submittedUs.toLong()
                if (!startNeedsSync && Math.abs(expectedUs - presentationTimeUs) > MAX_DRIFT_US) {
                    startNeedsSync = true
                }
                if (startNeedsSync) {
                    startMediaTimeUs += presentationTimeUs - expectedUs
                    startNeedsSync = false
                    discontinuityReported = true
                }
            }

            if (chunk != null) {
                val durationUs = chunk.frames * 1_000_000.0 / chunk.sampleRate
                submittedUs += durationUs
                queuedUs += durationUs
                queue.addLast(chunk)
            }
        }
        if (discontinuityReported) {
            listener?.onPositionDiscontinuity()
        }
        return true
    }

    /** Must be called with [lock] held. Consumes the whole of [buffer]. */
    private fun convert(buffer: ByteBuffer): Chunk? {
        val encoding = cfgEncoding
        val channels = cfgChannels
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_8BIT -> 1
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            else -> 4
        }
        val frames = buffer.remaining() / (bytesPerSample * channels)
        if (frames <= 0) {
            buffer.position(buffer.limit())
            return null
        }

        val src = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val start = src.position()
        val samples = frames * channels
        val out = FloatArray(samples)

        when (encoding) {
            C.ENCODING_PCM_16BIT -> for (i in 0 until samples) {
                out[i] = src.getShort(start + i * 2) * (1f / 32768f)
            }
            C.ENCODING_PCM_FLOAT -> for (i in 0 until samples) {
                out[i] = src.getFloat(start + i * 4)
            }
            C.ENCODING_PCM_24BIT -> for (i in 0 until samples) {
                val p = start + i * 3
                val v = (src.get(p + 2).toInt() shl 16) or
                        ((src.get(p + 1).toInt() and 0xFF) shl 8) or
                        (src.get(p).toInt() and 0xFF)
                out[i] = v * (1f / 8388608f)
            }
            C.ENCODING_PCM_32BIT -> for (i in 0 until samples) {
                out[i] = src.getInt(start + i * 4) * (1f / 2147483648f)
            }
            C.ENCODING_PCM_8BIT -> for (i in 0 until samples) {
                out[i] = ((src.get(start + i).toInt() and 0xFF) - 128) * (1f / 128f)
            }
        }

        buffer.position(buffer.limit())
        return Chunk(out, frames, cfgSampleRate, channels)
    }

    override fun playToEndOfStream() {
        endOfStream = true
        synchronized(lock) {
            for (p in activeProcessors) {
                p.queueEndOfStream()
            }
            while (true) {
                var hasOutput = false
                for (p in activeProcessors) {
                    val out = p.output
                    if (out.hasRemaining()) {
                        hasOutput = true
                        val chunk = convert(out)
                        if (chunk != null) {
                            val durationUs = chunk.frames * 1_000_000.0 / chunk.sampleRate
                            submittedUs += durationUs
                            queuedUs += durationUs
                            queue.addLast(chunk)
                        }
                    }
                }
                if (!hasOutput) break
            }
        }
    }

    override fun isEnded(): Boolean = isDrained() && activeProcessors.all { it.isEnded }

    override fun hasPendingData(): Boolean = hasQueuedData()

    private var playbackParams = PlaybackParameters.DEFAULT

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        playbackParams = playbackParameters
        mixer.onPlaybackParameters(playbackParameters)
    }

    override fun getPlaybackParameters(): PlaybackParameters = playbackParams

    private var skipSilence = false

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        skipSilence = skipSilenceEnabled
        mixer.onSkipSilence(skipSilenceEnabled)
    }

    override fun getSkipSilenceEnabled(): Boolean = skipSilence

    private var attributes = AudioAttributes.DEFAULT

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        attributes = audioAttributes
        mixer.onAudioAttributes(audioAttributes)
    }

    override fun getAudioAttributes(): AudioAttributes = attributes

    // The hardware sink owns its own session, ExoPlayer's per-player ids do not apply to it.
    override fun setAudioSessionId(audioSessionId: Int) {}

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {}

    override fun enableTunnelingV21() {}

    override fun disableTunneling() {}

    override fun setVolume(volume: Float) {
        softwareVolume = volume
    }

    /**
     * Drops all queued audio of this player. The hardware is deliberately not touched from here;
     * the mixer notices the new [flushGeneration] and decides on its own thread whether the
     * hardware may be flushed (it must not while the other player is still audible).
     */
    override fun flush() {
        synchronized(lock) {
            queue.clear()
            queuedUs = 0.0
            started = false
            startNeedsSync = false
            submittedUs = 0.0
            consumedUs = 0.0
            endOfStream = false
            isMuted = false
            flushGeneration++
            for (p in activeProcessors) p.flush()
        }
        mixer.wake()
    }

    override fun reset() {
        flush()
        synchronized(lock) {
            cfgSampleRate = 0
            cfgChannels = 0
            cfgEncoding = C.ENCODING_INVALID
            for (p in processors) p.reset()
            activeProcessors = emptyArray()
        }
    }

    override fun release() {
        flush()
    }

    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) {}

    companion object {
        /** Maximum amount of decoded audio parked in the queue. */
        private const val MAX_QUEUED_US = 1_000_000L
        private const val MAX_DRIFT_US = 200_000L
        private const val MAX_CHANNELS = 8
    }
}
