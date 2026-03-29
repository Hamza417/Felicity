package app.simple.felicity.decorations.padding

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.simple.felicity.decoration.R

/**
 * A plain [LinearLayout] that pads itself to avoid the system status bar and/or
 * navigation bar using the modern [androidx.core.view.WindowInsetsCompat] API.
 *
 * Unlike [PaddingAwareLinearLayout], this class does **not** extend any theme-aware
 * super class, so it never draws a theme-driven background color.  Use it wherever
 * the host view provides its own background (e.g., a transparent gradient overlay)
 * and must not be overwritten by the app theme's view-group color.
 *
 * XML attributes (both default to `false`):
 * - `app:statusPaddingRequired="true"` — pads top by the status-bar height.
 * - `app:navigationPaddingRequired="true"` — pads bottom by the nav-bar height.
 *
 * @author Hamza417
 */
class InsetLinearLayout @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.InsetLinearLayout)
        val statusPaddingRequired = ta.getBoolean(
                R.styleable.InsetLinearLayout_statusPaddingRequired, false
        )
        val navigationPaddingRequired = ta.getBoolean(
                R.styleable.InsetLinearLayout_navigationPaddingRequired, false
        )
        ta.recycle()

        applySystemBarMargin(this, statusPaddingRequired, navigationPaddingRequired)
    }

    private fun applySystemBarMargin(viewGroup: ViewGroup, statusMarginRequired: Boolean, navigationMarginRequired: Boolean) {
        ViewCompat.setOnApplyWindowInsetsListener(viewGroup) { v: View?, windowInsets: WindowInsetsCompat? ->
            val insets: Insets = windowInsets!!.getInsets(WindowInsetsCompat.Type.systemBars())
            val layoutParams = viewGroup.layoutParams

            // Ensure the layout params support margins
            if (layoutParams is MarginLayoutParams) {
                val marginParams = layoutParams

                if (statusMarginRequired && navigationMarginRequired) {
                    marginParams.topMargin += insets.top
                    marginParams.bottomMargin += insets.bottom
                } else if (statusMarginRequired) {
                    marginParams.topMargin += insets.top
                } else if (navigationMarginRequired) {
                    marginParams.bottomMargin += insets.bottom
                }

                // Apply the updated layout parameters back to the view
                viewGroup.setLayoutParams(marginParams)
            } else {
                Log.w("SystemBarMargin", "View's LayoutParams must be MarginLayoutParams to apply margins.")
            }

            Log.d("Margin", "Insets applied: $insets")
            WindowInsetsCompat.CONSUMED
        }
    }
}

