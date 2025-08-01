package app.simple.felicity.engine.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.simple.felicity.engine.managers.AudioStateManager
import app.simple.felicity.engine.managers.PlaybackState
import app.simple.felicity.preferences.SharedPreferences.initRegisterSharedPreferenceChangeListener
import app.simple.felicity.repository.interfaces.AudioStateCallbacks
import app.simple.felicity.repository.models.normal.Audio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ExoPlayerService : MediaSessionService(),
                         AudioManager.OnAudioFocusChangeListener,
                         SharedPreferences.OnSharedPreferenceChangeListener,
                         AudioStateCallbacks {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (AudioStateManager.isPlaying()) {
                    Log.d("BaseAudioService", "Audio becoming noisy, pausing playback")
                    mediaSession?.player?.pause()
                    AudioStateManager.updatePlaybackState(PlaybackState.PAUSED)
                } else {
                    Log.d("BaseAudioService", "Audio is not playing, no action taken")
                }
            }
        }
    }

    private val audioBecomingNoisyFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    private var focusRequest: AudioFocusRequest? = null
    private var mediaSession: MediaSession? = null

    private var wasPlaying = false

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: ExoPlayerService created")
        initRegisterSharedPreferenceChangeListener(applicationContext)

        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()

        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(this)
            .build()

        registerReceiver(becomingNoisyReceiver, audioBecomingNoisyFilter)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        if (mediaSession == null) {
            Log.d(TAG, "onGetSession: Creating new MediaSession")
            mediaSession = MediaSession.Builder(this, ExoPlayer.Builder(this).build())
                .setId("ExoPlayerServiceSession")
                .build()
        }

        Log.d(TAG, "onGetSession: Returning MediaSession")

        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (AudioStateManager.isPlaying()) {
                    if (wasPlaying) {
                        mediaSession?.player?.play()
                    }
                }
            }
            /**
             * Lost focus for an unbounded amount of time: stop playback and release media player
             */
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                wasPlaying = AudioStateManager.isPlaying()

                /**
                 * Lost focus for a short time, but we have to stop
                 * playback. We don't release the media player because playback
                 * is likely to resume
                 */
                if (AudioStateManager.isPlaying()) {
                    mediaSession?.player?.pause()
                } else {
                    mediaSession?.player?.stop()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                if (AudioStateManager.isPlaying()) {
                    wasPlaying = true
                    mediaSession?.player?.deviceVolume?.let { volume ->
                        if (volume > 0) {
                            mediaSession?.player?.setVolume(volume * 0.5f) // Reduce volume by 50%
                        }
                    }
                } else {
                    wasPlaying = false
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {

    }

    override fun onAudioPause(audio: ArrayList<Audio>?) {
        super.onAudioPause(audio)

    }

    override fun onAudioPlay(audio: ArrayList<Audio>?, position: Int) {
        super.onAudioPlay(audio, position)

    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    inner class ExoPlayerBinder : Binder() {
        fun getService(): ExoPlayerService = this@ExoPlayerService
    }

    companion object {
        private const val TAG = "ExoPlayerService"
    }
}