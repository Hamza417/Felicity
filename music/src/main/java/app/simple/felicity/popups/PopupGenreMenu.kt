package app.simple.felicity.popups

import android.view.View
import android.view.ViewGroup
import app.simple.felicity.decorations.views.SharedElementPopup

class PopupGenreMenu(
        container: ViewGroup,
        anchorView: View,
        layoutResId: Int,
        onPopupInflated: (View) -> Unit = {},
        onDismiss: (() -> Unit)? = null)
    : SharedElementPopup(container, anchorView, layoutResId, onPopupInflated, onDismiss)