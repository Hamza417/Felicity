package app.simple.felicity.engine.abstraction.services

import android.app.NotificationManager
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import androidx.core.app.NotificationCompat
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
import kotlinx.coroutines.launch

abstract class BaseAudioService : MediaSessionService(),
                                  AudioManager.OnAudioFocusChangeListener,
                                  SharedPreferences.OnSharedPreferenceChangeListener,
                                  AudioStateCallbacks {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val becomingNoisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (AudioStateManager.isPlaying()) {
                    onPause()
                }
            }
        }
    }
    private val audioBecomingNoisyFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    private var focusRequest: AudioFocusRequest? = null
    private var notificationManager: NotificationManager? = null
    private var builder: NotificationCompat.Builder? = null
    private var mediaSession: MediaSession? = null

    private var wasPlaying = false

    override fun onCreate() {
        super.onCreate()
        initRegisterSharedPreferenceChangeListener(applicationContext)


        serviceScope.launch {
            AudioStateManager.audioState.collect {
                when (it.playbackState) {
                    PlaybackState.IDLE -> {
                        Log.d("BaseAudioService", "Playback is idle")
                    }
                    PlaybackState.PLAYING -> {
                        if (AudioStateManager.isPlaying()) {
                            if (focusRequest != null) {
                                val audioFocusResult = (getSystemService(AUDIO_SERVICE) as AudioManager).requestAudioFocus(focusRequest!!)
                                if (audioFocusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                                    onPlay()
                                } else {
                                    Log.e("BaseAudioService", "Failed to gain audio focus")
                                }
                            } else {
                                Log.e("BaseAudioService", "Focus request is null")
                            }
                        }
                    }
                    PlaybackState.PAUSED -> {
                        if (AudioStateManager.isPlaying()) {
                            onPause()
                        } else {
                            Log.d("BaseAudioService", "Playback is already paused")
                        }
                    }
                    PlaybackState.STOPPED -> {
                        if (AudioStateManager.isPlaying()) {
                            onStop()
                        } else {
                            Log.d("BaseAudioService", "Playback is already stopped")
                        }
                    }
                    PlaybackState.BUFFERING -> {
                        Log.d("BaseAudioService", "Playback is buffering")
                    }
                }
            }
        }

        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(this)
            .build()

        registerReceiver(becomingNoisyReceiver, audioBecomingNoisyFilter)
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
                        onPlay()
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
                    onPause()
                } else {
                    onStop()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                if (AudioStateManager.isPlaying()) {
                    wasPlaying = true
                    onVolume(0.5f) // Reduce volume to 50% when ducking
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
        super.onDestroy()
    }

    abstract fun onPlay()
    abstract fun onPause()
    abstract fun onStop()
    abstract fun onSeekTo(position: Int)
    abstract fun onSkipToNext()
    abstract fun onSkipToPrevious()
    abstract fun onFastForward()
    abstract fun onRewind()
    abstract fun onPrepare()

    abstract fun getDuration(): Int

    abstract fun getCurrentPosition(): Int
    abstract fun onVolume(volume: Float)

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "audio_service_channel"
        const val NOTIFICATION_ID = 1
    }

    abstract fun onSetAudio(audio: Audio)
}
