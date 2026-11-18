package app.simple.felicity.engine.managers

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.AnalyticsListener
import app.simple.felicity.engine.audio.SharedAudioMixer
import app.simple.felicity.engine.audio.VirtualAudioSink

/**
 * Manages two [ExoPlayer] instances (Player A and Player B) to support crossfading.
 * - Monitors Player A's position. When `currentPosition >= duration - crossfade_duration`,
 *   it commands Player B to `play()`.
 * - Routes [Player.Listener] events appropriately so the UI sees a single seamless stream.
 */
@UnstableApi
class CrossfadePlayerManager(
        private val context: Context,
        private val sharedMixer: SharedAudioMixer,
        private val extensionRendererMode: Int,
        private val loadControl: LoadControl
) {

    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null

    // Which player is currently the main output (the one the UI tracks)
    private var activePlayerIsA = true

    // Callbacks for the service
    var eventListener: Player.Listener? = null
    var analyticsListener: AnalyticsListener? = null
    var onActivePlayerChanged: ((ExoPlayer) -> Unit)? = null

    init {
        // Build players with VirtualAudioSink
        playerA = buildPlayer()
        // playerB = buildPlayer()
    }

    private fun buildPlayer(): ExoPlayer {
        val virtualSink = VirtualAudioSink(sharedMixer)
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableOffload: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return virtualSink
            }
        }
        renderersFactory.setExtensionRendererMode(extensionRendererMode)

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_NEVER)
                        .build(),
                    true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setLoadControl(loadControl)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (activePlayer == player) eventListener?.onPlaybackStateChanged(playbackState)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (activePlayer == player) eventListener?.onIsPlayingChanged(isPlaying)
            }

            override fun onPlayerError(error: PlaybackException) {
                if (activePlayer == player) eventListener?.onPlayerError(error)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (activePlayer == player) eventListener?.onPlayWhenReadyChanged(playWhenReady, reason)
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                if (activePlayer == player) eventListener?.onMediaItemTransition(mediaItem, reason)
            }

            override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
            ) {
                if (activePlayer == player) eventListener?.onPositionDiscontinuity(oldPosition, newPosition, reason)
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (activePlayer == player) eventListener?.onAudioSessionIdChanged(audioSessionId)
            }
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioDecoderInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    decoderName: String,
                    initializedTimestampMs: Long,
                    initializationDurationMs: Long
            ) {
                if (activePlayer == player) analyticsListener?.onAudioDecoderInitialized(eventTime, decoderName, initializedTimestampMs, initializationDurationMs)
            }

            override fun onAudioInputFormatChanged(
                    eventTime: AnalyticsListener.EventTime,
                    format: androidx.media3.common.Format,
                    decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
            ) {
                if (activePlayer == player) analyticsListener?.onAudioInputFormatChanged(eventTime, format, decoderReuseEvaluation)
            }
        })
        return player
    }

    val activePlayer: ExoPlayer?
        get() = if (activePlayerIsA) playerA else playerB

    fun play() {
        activePlayer?.play()
    }

    fun pause() {
        activePlayer?.pause()
    }

    fun release() {
        playerA?.release()
        playerB?.release()
    }

    // Add UI proxy methods (setMediaItems, seekTo, getDuration, etc.)
}