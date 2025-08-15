package app.simple.felicity.extensions.activities

import android.content.Context
import android.os.Bundle
import android.os.StrictMode
import androidx.activity.ComponentActivity

open class BaseActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context?) {
        app.simple.felicity.preferences.SharedPreferences.init(newBase!!)
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStrictModePolicy()
    }

    private fun setStrictModePolicy() {
        StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .build())
    }

    companion object {
        const val TAG = "BaseActivity"
    }
}
