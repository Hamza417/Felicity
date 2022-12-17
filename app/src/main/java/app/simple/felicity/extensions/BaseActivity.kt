package app.simple.felicity.extensions

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class BaseActivity: AppCompatActivity() {
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        
    }
}