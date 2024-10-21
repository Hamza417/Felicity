package app.simple.felicity.services

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.media3.session.MediaSession
import app.simple.felicity.extensions.services.BaseAudioService

class FelicityPlayerService : BaseAudioService() {

    val binder = FelicityPlayerServiceBinder()

    inner class FelicityPlayerServiceBinder : Binder() {
        fun getService(): FelicityPlayerService {
            return this@FelicityPlayerService
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onPlay() {

    }

    override fun onPause() {

    }

    override fun onStop() {

    }

    override fun onSeekTo(position: Int) {

    }

    override fun onSkipToNext() {

    }

    override fun onSkipToPrevious() {

    }

    override fun onFastForward() {

    }

    override fun onRewind() {

    }

    override fun onPrepare() {

    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return null
    }

    companion object {
        fun getIntent(context: Context): Intent {
            return Intent(context, FelicityPlayerService::class.java)
        }
    }
}
