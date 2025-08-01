package app.simple.felicity.engine.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.IBinder
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import app.simple.felicity.preferences.SharedPreferences.initRegisterSharedPreferenceChangeListener
import app.simple.felicity.repository.repositories.MediaStoreRepository

class ExoPlayerService : MediaLibraryService(),
                         AudioManager.OnAudioFocusChangeListener,
                         SharedPreferences.OnSharedPreferenceChangeListener {

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.d(TAG, "onReceive: Audio becoming noisy, pausing playback")
                val player = mediaSession?.player
                if (player?.isPlaying == true) {
                    player.pause()
                    wasPlaying = true
                }
            }
        }
    }

    private val audioBecomingNoisyFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

    private var focusRequest: AudioFocusRequest? = null
    private var mediaSession: MediaLibrarySession? = null
    private var mediaStoreRepository: MediaStoreRepository? = null

    private var wasPlaying = false

    var callback: MediaLibrarySession.Callback = object : MediaLibrarySession.Callback {

    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: ExoPlayerService created")
        initRegisterSharedPreferenceChangeListener(applicationContext)
        mediaStoreRepository = MediaStoreRepository(this)
        val player = ExoPlayer.Builder(this).build()

        mediaSession = MediaLibrarySession.Builder(this, player, callback).setId("ExoPlayerServiceSession").build()

        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(this)
            .build()

        registerReceiver(becomingNoisyReceiver, audioBecomingNoisyFilter)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        Log.d(TAG, "onGetSession: Returning media library session")
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onAudioFocusChange(focusChange: Int) {
        val player = mediaSession?.player

        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Resume playback or restore volume
                player?.volume = 1.0f
                if (wasPlaying) {
                    player?.play()
                    wasPlaying = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Pause playback and remember if it was playing
                if (player?.isPlaying == true) {
                    wasPlaying = true
                    player.pause()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                // Lower the volume for ducking
                player?.volume = 0.2f
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {

    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ExoPlayerService"
    }
}