package app.simple.felicity.activities

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import app.simple.felicity.R
import app.simple.felicity.dialogs.app.VolumeKnob.Companion.showVolumeKnob
import app.simple.felicity.extensions.activities.BaseActivity
import app.simple.felicity.repository.services.AudioSynchronizerService
import app.simple.felicity.shared.utils.ConditionUtils.isNull
import app.simple.felicity.ui.main.home.InureHome

class MainActivity : BaseActivity() {

    private var serviceConnection: ServiceConnection? = null
    private var audioSynchronizerService: AudioSynchronizerService? = null

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

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                audioSynchronizerService = (service as AudioSynchronizerService.SynchronizerBinder).getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // Do nothing
            }
        }
    }

    override fun onStart() {
        super.onStart()
        startService()
    }

    override fun onStop() {
        super.onStop()
        serviceConnection?.let { unbindService(it) }
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

    private fun startService() {
        val intent = AudioSynchronizerService.getSyncServiceIntent(baseContext)
        startService(intent)
        serviceConnection?.let {
            bindService(intent, it, Context.BIND_AUTO_CREATE)
        }
    }
}
