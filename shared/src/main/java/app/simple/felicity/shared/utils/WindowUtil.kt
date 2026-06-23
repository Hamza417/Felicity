package app.simple.felicity.shared.utils

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

    /**
     * Applies window insets as padding to the view, respecting the initial XML padding.
     *
     * @param applyStatusBar If true, adds the status bar inset to the top padding.
     * @param applyNavigationBar If true, adds the navigation bar insets to the left, right, and bottom padding.
     */
    fun View.applyBarPadding(
            applyStatusBar: Boolean = true,
            applyNavigationBar: Boolean = true
    ) {
        // Snapshot the initial padding exactly once before any listener is attached.
        // This acts as our immutable baseline.
        val baseLeft = paddingLeft
        val baseTop = paddingTop
        val baseRight = paddingRight
        val baseBottom = paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
            // Fetch status and navigation bars independently.
            // This prevents them from blending together in unexpected ways.
            val statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())

            // Resolve the specific insets based on your flags
            val topInset = if (applyStatusBar) statusBars.top else 0
            val bottomInset = if (applyNavigationBar) navBars.bottom else 0
            val leftInset = if (applyNavigationBar) navBars.left else 0
            val rightInset = if (applyNavigationBar) navBars.right else 0

            // Apply the combined padding safely using the baseline
            view.setPadding(
                    baseLeft + leftInset,
                    baseTop + topInset,
                    baseRight + rightInset,
                    baseBottom + bottomInset
            )

            // Return the insets untouched so child views can also consume them.
            // If you return WindowInsetsCompat.CONSUMED, children won't get inset callbacks.
            windowInsets
        }
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