package app.simple.felicity.extensions.services

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.IBinder

class FelicityAudioService : Service(),
                             AudioManager.OnAudioFocusChangeListener,
                             MediaPlayer.OnCompletionListener,
                             MediaPlayer.OnPreparedListener,
                             MediaPlayer.OnErrorListener,
                             MediaPlayer.OnBufferingUpdateListener,
                             MediaPlayer.OnSeekCompleteListener,
                             SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
}
