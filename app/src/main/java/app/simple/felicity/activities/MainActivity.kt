package app.simple.felicity.activities

import android.os.Bundle
import app.simple.felicity.R
import app.simple.felicity.extensions.activities.BaseActivity

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}