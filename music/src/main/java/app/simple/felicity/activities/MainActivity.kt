package app.simple.felicity.activities

import android.os.Bundle
import android.view.KeyEvent
import app.simple.felicity.R
import app.simple.felicity.dialogs.app.VolumeKnob.Companion.showVolumeKnob
import app.simple.felicity.extensions.activities.BaseActivity
import app.simple.felicity.preferences.HomePreferences
import app.simple.felicity.shared.utils.ConditionUtils.isNull
import app.simple.felicity.ui.main.home.ArtFlowHome
import app.simple.felicity.ui.main.home.CarouselHome
import app.simple.felicity.ui.main.home.SimpleHome
import app.simple.felicity.ui.main.home.SpannedHome
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Keep screen on
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (savedInstanceState.isNull()) {
            setHomePanel()
        }
    }

    private fun setHomePanel() {
        when (HomePreferences.getHomeInterface()) {
            HomePreferences.HOME_INTERFACE_CAROUSEL -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.app_container, CarouselHome.newInstance(), CarouselHome.TAG)
                    .commit()
            }

            HomePreferences.HOME_INTERFACE_SPANNED -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.app_container, SpannedHome.newInstance(), SpannedHome.TAG)
                    .commit()
            }
            HomePreferences.HOME_INTERFACE_ARTFLOW -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.app_container, ArtFlowHome.newInstance(), ArtFlowHome.TAG)
                    .commit()
            }
            HomePreferences.HOME_INTERFACE_SIMPLE -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.app_container, SimpleHome.newInstance(), SimpleHome.TAG)
                    .commit()
            }
        }
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
