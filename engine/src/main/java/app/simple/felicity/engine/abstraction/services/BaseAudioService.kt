package app.simple.felicity.engine.abstraction.services

import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.IBinder
import androidx.media3.session.MediaSessionService
import app.simple.felicity.engine.manager.AudioStateManager
import app.simple.felicity.repository.interfaces.AudioStateCallbacks
import app.simple.felicity.repository.models.normal.Audio

abstract class BaseAudioService : MediaSessionService(),
                                  AudioManager.OnAudioFocusChangeListener,
                                  SharedPreferences.OnSharedPreferenceChangeListener,
                                  AudioStateCallbacks {

    override fun onBind(intent: Intent?): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()
        AudioStateManager.getInstance().registerAudioStateCallbacks(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onAudioFocusChange(focusChange: Int) {

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
        AudioStateManager.getInstance().unregisterAudioStateCallbacks(this)
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

    fun getDuration(): Int {
        return 0
    }

    fun getProgress(): Int {
        return 0
    }
}
