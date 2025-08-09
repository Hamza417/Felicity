package app.simple.felicity.popups

import SharedElementPopup
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.databinding.PopupGenreMenuBinding

class PopupGenreMenu(container: ViewGroup,
                     anchorView: View,
                     inflateBinding: (LayoutInflater, ViewGroup?, Boolean) -> PopupGenreMenuBinding,
                     onPopupInflated: (PopupGenreMenuBinding) -> Unit = {},
                     onDismiss: (() -> Unit)? = null)
    : SharedElementPopup<PopupGenreMenuBinding>(container, anchorView, inflateBinding, onPopupInflated, onDismiss)