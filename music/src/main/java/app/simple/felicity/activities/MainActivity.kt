package app.simple.felicity.activities

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import app.simple.felicity.R
import app.simple.felicity.dialogs.app.VolumeKnob.Companion.showVolumeKnob
import app.simple.felicity.engine.services.MediaPlayerService
import app.simple.felicity.extensions.activities.BaseActivity
import app.simple.felicity.repository.services.AudioSynchronizerService
import app.simple.felicity.shared.utils.ConditionUtils.isNull
import app.simple.felicity.ui.main.home.InureHome

class MainActivity : BaseActivity() {

    private var syncServiceConnection: ServiceConnection? = null
    private var playerServiceConnection: ServiceConnection? = null

    private var audioSynchronizerService: AudioSynchronizerService? = null
    private var mediaPlayerService: MediaPlayerService? = null

    private var isSynchronizerServiceBound = false
    private var isAudioServiceBound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Keep screen on
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (savedInstanceState.isNull()) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.app_container, InureHome.newInstance())
                .commit()
        }

        syncServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                audioSynchronizerService = (service as AudioSynchronizerService.SynchronizerBinder).getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // Do nothing
            }
        }

        playerServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                mediaPlayerService = (service as MediaPlayerService.PlayerBinder).getService()
                isAudioServiceBound = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isAudioServiceBound = false
            }
        }
    }

    override fun onStart() {
        super.onStart()
        startServices()
    }

    override fun onStop() {
        super.onStop()
        syncServiceConnection?.let { unbindService(it) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                showVolumeKnob()
                true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                showVolumeKnob()
                true
            }

            else -> {
                super.onKeyDown(keyCode, event)
            }
        }
    }

    private fun startServices() {
        val intent = AudioSynchronizerService.getSyncServiceIntent(baseContext)
        startService(intent)
        syncServiceConnection?.let {
            bindService(intent, it, Context.BIND_AUTO_CREATE)
        }

        val playerIntent = MediaPlayerService.getMediaPlayerServiceIntent(baseContext)
        startService(playerIntent)
        playerServiceConnection?.let {
            bindService(playerIntent, it, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isSynchronizerServiceBound) {
            syncServiceConnection?.let { unbindService(it) }
        }

        if (isAudioServiceBound) {
            playerServiceConnection?.let { unbindService(it) }
        }

        audioSynchronizerService = null
        mediaPlayerService = null
        isSynchronizerServiceBound = false
        isAudioServiceBound = false
    }
}
