package app.simple.felicity.services

import android.media.MediaPlayer
import androidx.media3.session.MediaSession
import app.simple.felicity.abstraction.services.BaseAudioService

class MediaPlayerService : BaseAudioService(),
                           MediaPlayer.OnPreparedListener,
                           MediaPlayer.OnCompletionListener,
                           MediaPlayer.OnSeekCompleteListener,
                           MediaPlayer.OnErrorListener {

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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        TODO("Not yet implemented")
    }

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

}