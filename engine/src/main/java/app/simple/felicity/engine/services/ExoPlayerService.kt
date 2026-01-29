package app.simple.felicity.engine.services

import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import app.simple.felicity.manager.SharedPreferences.initRegisterSharedPreferenceChangeListener
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.repositories.MediaStoreRepository

@OptIn(UnstableApi::class)
class ExoPlayerService : MediaLibraryService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var mediaSession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private var mediaStoreRepository: MediaStoreRepository? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: ExoPlayerService created")

        initRegisterSharedPreferenceChangeListener(applicationContext)
        mediaStoreRepository = MediaStoreRepository(this)

        // High-Res Audio Configuration (24/32-bit support)
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioRenderers(
                    context: Context,
                    extensionRendererMode: Int,
                    mediaCodecSelector: MediaCodecSelector,
                    enableDecoderFallback: Boolean,
                    audioSink: AudioSink,
                    eventHandler: Handler,
                    eventListener: AudioRendererEventListener,
                    out: ArrayList<Renderer>
            ) {
                val customSink = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(true) // 32-bit Float support
                    .build()

                out.add(MediaCodecAudioRenderer(context, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, customSink))
                super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, customSink, eventHandler, eventListener, out)
            }
        }
        renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        // Build Player with Internal Audio Management
        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true // THIS HANDLES AUDIO FOCUS & NOISY AUTOMATICALLY
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(playerListener)

        // Initialize MediaSession (This triggers the system playback notification)
        val sessionActivityIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(sessionActivityIntent!!)
            .setId("ExoPlayerServiceSession")
            .build()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val format = player.audioFormat

            val encodingName = when (format?.pcmEncoding) {
                C.ENCODING_PCM_16BIT -> "16-bit" // 2
                C.ENCODING_PCM_FLOAT -> "32-bit Float" // 4
                C.ENCODING_PCM_24BIT -> "24-bit" // 21 (High Res)
                C.ENCODING_PCM_8BIT -> "8-bit" // 3
                C.ENCODING_PCM_32BIT -> "32-bit" // 5
                C.ENCODING_INVALID -> "Compressed/Unknown" // -1 (MP3/AAC pending)
                else -> "Other (${format?.pcmEncoding})"
            }

            Log.i(TAG, "Audio Engine: ${format?.sampleRate}Hz | Type: $encodingName")

            if (isPlaying) {
                MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_PLAYING)
            } else if (player.playbackState == Player.STATE_READY) {
                MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_PAUSED)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_BUFFERING)
                }
                Player.STATE_READY -> {
                    if (player.playWhenReady) MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_PLAYING)
                    else MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_PAUSED)
                }
                Player.STATE_ENDED -> {
                    MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_ENDED)
                }
                Player.STATE_IDLE -> {
                    MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_STOPPED)
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "Player error: ${error.errorCodeName}", error)
            MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_ERROR)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            MediaManager.notifyCurrentPosition(player.currentMediaItemIndex)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {}

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: Cleaning up service")
        mediaSession?.run {
            player.stop()
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

    }

    companion object {
        private const val TAG = "ExoPlayerService"
    }
}