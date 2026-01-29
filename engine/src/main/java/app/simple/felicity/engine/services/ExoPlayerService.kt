package app.simple.felicity.engine.services

import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
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

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableOffload: Boolean): AudioSink {
                val audioSink = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(true) // Force 32-bit Internal Processing
                    .build()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    audioSink.setOffloadMode(DefaultAudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_NOT_REQUIRED)
                }

                return audioSink
            }
        }

        renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true // Handles Focus & Noisy (Unplugging headphones)
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL) // KEEPS CPU AWAKE FOR HIGH-RES AUDIO
            .build()

        player.addListener(playerListener)

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

            // Refined Logging to handle the "-1" state gracefully
            if (format != null && format.pcmEncoding != C.ENCODING_INVALID) {
                val encodingName = when (format.pcmEncoding) {
                    C.ENCODING_PCM_16BIT -> "16-bit"
                    C.ENCODING_PCM_FLOAT -> "32-bit Float"
                    C.ENCODING_PCM_24BIT -> "24-bit" // API 31+ specific constant, usually maps to PACKED in Exo
                    C.ENCODING_PCM_32BIT -> "32-bit"
                    else -> "Other (${format.pcmEncoding})"
                }
                Log.i(TAG, "Audio Engine: ${format.sampleRate}Hz | Output: $encodingName")
            }

            if (isPlaying) {
                MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_PLAYING)
            } else if (player.playbackState == Player.STATE_READY) {
                MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_PAUSED)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_BUFFERING)
                Player.STATE_READY -> {
                    if (player.playWhenReady) MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_PLAYING)
                    else MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_PAUSED)
                }
                Player.STATE_ENDED -> MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_ENDED)
                Player.STATE_IDLE -> MediaManager.notifyPlaybackState(MediaConstants.PLAYBACK_STOPPED)
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