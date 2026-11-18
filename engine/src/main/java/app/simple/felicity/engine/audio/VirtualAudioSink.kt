package app.simple.felicity.engine.audio

import android.media.AudioDeviceInfo
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer

/**
 * Acts as a local sink for a single ExoPlayer instance.
 * Instead of writing to a hardware device, it writes PCM data into a lock-free ring buffer
 * accessible by the [SharedAudioMixer].
 */
@UnstableApi
class VirtualAudioSink(
        private val mixer: SharedAudioMixer
) : AudioSink {

    private var listener: AudioSink.Listener? = null
    private var isPlaying = false

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
        mixer.hardwareSink.setListener(listener)
    }

    override fun setPlayerId(playerId: PlayerId?) {
        // Ignored
    }

    override fun setClock(clock: Clock) {
        // Ignored
    }

    override fun supportsFormat(format: Format): Boolean {
        // The virtual sink can support anything ExoPlayer can decode to PCM.
        return true
    }

    override fun getFormatSupport(format: Format): Int {
        return AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        return mixer.hardwareSink.getCurrentPositionUs(sourceEnded)
    }

    override fun getAudioTrackBufferSizeUs(): Long {
        return 0L
    }

    override fun configure(
            inputFormat: Format,
            specifiedBufferSize: Int,
            outputChannels: IntArray?
    ) {
        mixer.hardwareSink.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun play() {
        isPlaying = true
        mixer.hardwareSink.play()
    }

    override fun handleDiscontinuity() {
        // Reset buffers
    }

    override fun handleBuffer(
            buffer: ByteBuffer,
            presentationTimeUs: Long,
            encodedAccessUnitCount: Int
    ): Boolean {
        return mixer.hardwareSink.pushMixedBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun playToEndOfStream() {
        mixer.hardwareSink.playToEndOfStream()
    }

    override fun isEnded(): Boolean {
        return mixer.hardwareSink.isEnded()
    }

    override fun hasPendingData(): Boolean {
        return mixer.hardwareSink.hasPendingData()
    }

    private var playbackParams = PlaybackParameters.DEFAULT

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        playbackParams = playbackParameters
        mixer.hardwareSink.playbackParameters = playbackParameters
    }

    override fun getPlaybackParameters(): PlaybackParameters {
        return playbackParams
    }

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        // Handled by pre-mix DSP processor
    }

    override fun getSkipSilenceEnabled(): Boolean {
        return false
    }

    private var attributes = AudioAttributes.DEFAULT

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        attributes = audioAttributes
        mixer.hardwareSink.audioAttributes = audioAttributes
    }

    override fun getAudioAttributes(): AudioAttributes {
        return attributes
    }

    override fun setAudioSessionId(audioSessionId: Int) {
        mixer.hardwareSink.setAudioSessionId(audioSessionId)
    }

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
        mixer.hardwareSink.setAuxEffectInfo(auxEffectInfo)
    }

    override fun enableTunnelingV21() {
        // Unsupported
    }

    override fun disableTunneling() {
        // Unsupported
    }

    override fun setVolume(volume: Float) {
        mixer.hardwareSink.setVolume(volume)
    }

    override fun pause() {
        isPlaying = false
        mixer.hardwareSink.pause()
    }

    override fun flush() {
        mixer.hardwareSink.flush()
    }

    override fun reset() {
        mixer.hardwareSink.reset()
    }

    override fun release() {
        mixer.hardwareSink.release()
    }

    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) {
        // Handled by global sink
    }
}
