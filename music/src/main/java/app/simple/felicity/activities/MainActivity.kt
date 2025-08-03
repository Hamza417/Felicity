package app.simple.felicity.activities

import android.os.Bundle
import android.view.KeyEvent
import app.simple.felicity.R
import app.simple.felicity.dialogs.app.VolumeKnob.Companion.showVolumeKnob
import app.simple.felicity.extensions.activities.BaseActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Keep screen on
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
}
