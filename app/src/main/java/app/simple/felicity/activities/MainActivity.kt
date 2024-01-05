package app.simple.felicity.activities

import android.os.Bundle
import app.simple.felicity.R
import app.simple.felicity.extensions.activities.BaseActivity
import app.simple.felicity.ui.launcher.SplashScreen
import app.simple.felicity.utils.ConditionUtils.isNull

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Keep screen on
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (savedInstanceState.isNull()) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.app_container, SplashScreen.newInstance())
                .commit()
        }
    }
}