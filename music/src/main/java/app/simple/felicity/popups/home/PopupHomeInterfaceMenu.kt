package app.simple.felicity.popups.home

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import app.simple.felicity.decorations.views.SharedScrollViewPopup

class PopupHomeInterfaceMenu(
        container: ViewGroup,
        anchorView: View,
        menuItems: List<Int>,
        menuIcons: List<Int>,
        onMenuItemClick: (itemResId: Int) -> Unit,
        onDismiss: (() -> Unit)? = null
) : SharedScrollViewPopup(container, anchorView, menuItems, menuIcons, onMenuItemClick, onDismiss) {
    override fun onPopupCreated(scrollView: NestedScrollView, contentLayout: LinearLayout) {

    }
}