package app.simple.felicity.popups

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import app.simple.felicity.decorations.views.SharedScrollViewPopup

class PopupArtistMenu(
        container: ViewGroup,
        anchorView: View,
        menuItems: List<Int>, // List of String resource IDs
        onMenuItemClick: (itemResId: Int) -> Unit,
        onDismiss: (() -> Unit)? = null
) : SharedScrollViewPopup(container, anchorView, menuItems, onMenuItemClick, onDismiss) {
    override fun onPopupCreated(scrollView: NestedScrollView, contentLayout: LinearLayout) {

    }
}
