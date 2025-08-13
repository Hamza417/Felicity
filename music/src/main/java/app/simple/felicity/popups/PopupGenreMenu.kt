package app.simple.felicity.popups

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import app.simple.felicity.decorations.views.SharedScrollViewPopup

class PopupGenreMenu(
        container: ViewGroup,
        anchorView: View,
        menuItems: List<Int>, // List of String resource IDs
        menuIcons: List<Int>,
        onMenuItemClick: (itemResId: Int) -> Unit,
        onDismiss: (() -> Unit)? = null
) : SharedScrollViewPopup(container, anchorView, menuItems, menuIcons, onMenuItemClick, onDismiss) {
    override fun onPopupCreated(scrollView: NestedScrollView, contentLayout: LinearLayout) {

    }
}
