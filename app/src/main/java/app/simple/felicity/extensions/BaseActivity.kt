package app.simple.felicity.extensions

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import app.simple.felicity.R

open class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStrictModePolicy()
        makeAppFullScreen()

    }

    private fun makeAppFullScreen() {
        window.statusBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }
    }

    /**
     * Making the Navigation system bar not overlapping with the activity
     */
    fun fixNavigationBarOverlap() {
        /**
         * Root ViewGroup of this activity
         */
        val root = findViewById<CoordinatorLayout>(R.id.app_container)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // val imeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime())
            // val imeHeight = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom

            /**
             * Apply the insets as a margin to the view. Here the system is setting
             * only the bottom, left, and right dimensions, but apply whichever insets are
             * appropriate to your layout. You can also update the view padding
             * if that's more appropriate.
             */
            view.layoutParams = (view.layoutParams as FrameLayout.LayoutParams).apply {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            /**
             * Return CONSUMED if you don't want want the window insets to keep being
             * passed down to descendant views.
             */
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        fixNavigationBarOverlap()
    }

    private fun setStrictModePolicy() {
        StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .build())
    }
}