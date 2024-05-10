package app.simple.felicity.extensions.services

import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.IBinder
import androidx.media3.session.MediaSessionService

abstract class FelicityAudioService : MediaSessionService(),
                                      AudioManager.OnAudioFocusChangeListener,
                                      MediaPlayer.OnCompletionListener,
                                      MediaPlayer.OnPreparedListener,
                                      MediaPlayer.OnErrorListener,
                                      MediaPlayer.OnBufferingUpdateListener,
                                      MediaPlayer.OnSeekCompleteListener,
                                      SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onBind(intent: Intent?): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onAudioFocusChange(focusChange: Int) {

    }

    override fun onCompletion(mp: MediaPlayer?) {

    }

    override fun onPrepared(mp: MediaPlayer?) {

    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        return false
    }

    override fun onBufferingUpdate(mp: MediaPlayer?, percent: Int) {

    }

    override fun onSeekComplete(mp: MediaPlayer?) {

    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {

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
}
