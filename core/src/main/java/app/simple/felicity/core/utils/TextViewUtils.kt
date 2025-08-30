package app.simple.felicity.core.utils

import androidx.appcompat.widget.AppCompatTextView

object TextViewUtils {

    fun AppCompatTextView.setStartDrawable(resourceId: Int) {
        this.setCompoundDrawablesWithIntrinsicBounds(resourceId, 0, 0, 0)
    }
}