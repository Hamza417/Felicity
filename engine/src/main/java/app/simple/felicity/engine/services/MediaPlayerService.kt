package app.simple.felicity.engine.services

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.media3.session.MediaSession
import app.simple.felicity.engine.abstraction.services.BaseAudioService
import app.simple.felicity.repository.models.normal.Audio

class MediaPlayerService : BaseAudioService(), MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnErrorListener, MediaPlayer.OnBufferingUpdateListener {

    private var mediaPlayer = MediaPlayer()

    override fun onSetAudio(audio: Audio) {
        mediaPlayer.reset()
        mediaPlayer.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
        mediaPlayer.setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        mediaPlayer.setOnCompletionListener(this)
        mediaPlayer.setOnPreparedListener(this)
        mediaPlayer.setOnSeekCompleteListener(this)
        mediaPlayer.setOnBufferingUpdateListener(this)
        mediaPlayer.setDataSource(audio.path)
        mediaPlayer.prepareAsync()
    }

    override fun onPlay() {
        TODO("Not yet implemented")
    }

    override fun onPause() {
        TODO("Not yet implemented")
    }

    override fun onStop() {
        TODO("Not yet implemented")
    }

    override fun onSeekTo(position: Int) {
        TODO("Not yet implemented")
    }

    override fun onSkipToNext() {
        TODO("Not yet implemented")
    }

    override fun onSkipToPrevious() {
        TODO("Not yet implemented")
    }

    override fun onFastForward() {
        TODO("Not yet implemented")
    }

    override fun onRewind() {
        TODO("Not yet implemented")
    }

    override fun onPrepare() {
        TODO("Not yet implemented")
    }

    override fun getDuration(): Int {
        TODO("Not yet implemented")
    }

    override fun getCurrentPosition(): Int {
        TODO("Not yet implemented")
    }

    override fun onVolume(volume: Float) {
        mediaPlayer.setVolume(volume, volume)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        TODO("Not yet implemented")
    }

    // -------------------------------------------------------------------------------------------------------------------- //

    override fun onPrepared(mp: MediaPlayer?) {
        TODO("Not yet implemented")
    }

    override fun onCompletion(mp: MediaPlayer?) {
        TODO("Not yet implemented")
    }

    override fun onSeekComplete(mp: MediaPlayer?) {
        TODO("Not yet implemented")
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun onBufferingUpdate(mp: MediaPlayer?, percent: Int) {

    }

    // -------------------------------------------------------------------------------------------------------------------- //

    override fun onBind(intent: Intent?): IBinder? {
        super.onBind(intent)
        return PlayerBinder()
    }

    inner class PlayerBinder : Binder() {
        fun getService(): MediaPlayerService = this@MediaPlayerService
    }

    // -------------------------------------------------------------------------------------------------------------------- //

    companion object {
        private const val TAG = "MediaPlayerService"

        fun getMediaPlayerServiceIntent(context: Context): Intent {
            return Intent(context, MediaPlayerService::class.java)
        }
    }
}