package app.simple.felicity.activities

import android.os.Bundle
import app.simple.felicity.R
import app.simple.felicity.extensions.activities.BaseActivity
import app.simple.felicity.ui.app.Songs
import app.simple.felicity.utils.ConditionUtils.isNull

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState.isNull()) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.app_container, Songs.newInstance())
                .commit()
        }
    }
}