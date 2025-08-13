package app.simple.felicity.core.utils

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object WindowUtil {

    fun getStatusBarHeightWhenAvailable(view: View, callback: (Int) -> Unit) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val height = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            callback(height)
            insets
        }

        // view.requestApplyInsets()
    }

    fun getNavigationBarHeightWhenAvailable(view: View, callback: (Int) -> Unit) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val height = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            callback(height)
            insets
        }

        // view.requestApplyInsets()
    }

    fun applyInsetPadding(view: View, statusBar: Boolean, navBar: Boolean) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val left = view.paddingLeft + if (statusBar) systemBars.left else 0
            val top = view.paddingTop + if (statusBar) systemBars.top else 0
            val right = view.paddingRight + if (navBar) systemBars.right else 0
            val bottom = view.paddingBottom + if (navBar) systemBars.bottom else 0

            v.setPadding(left, top, right, bottom)
            insets
        }
    }
}