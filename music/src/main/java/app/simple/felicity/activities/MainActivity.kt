package app.simple.felicity.activities

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.simple.felicity.compose.theme.FelicityTheme
import app.simple.felicity.extensions.activities.BaseActivity
import app.simple.felicity.ui.home.SimpleHome
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            FelicityTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SimpleHome()
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                // showVolumeKnob()
                true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                // showVolumeKnob()
                true
            }

            else -> {
                super.onKeyDown(keyCode, event)
            }
        }
    }
}
