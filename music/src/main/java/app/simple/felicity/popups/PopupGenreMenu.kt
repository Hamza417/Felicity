package app.simple.felicity.popups

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.databinding.PopupGenreMenuBinding
import app.simple.felicity.decorations.views.SharedElementPopup

class PopupGenreMenu(
        container: ViewGroup,
        anchorView: View,
        inflateBinding: (LayoutInflater, ViewGroup?, Boolean) -> PopupGenreMenuBinding,
        onPopupInflated: (PopupGenreMenuBinding, () -> Unit) -> Unit = { _, _ -> },
        onDismiss: (() -> Unit)? = null
) : SharedElementPopup<PopupGenreMenuBinding>(container, anchorView, inflateBinding, onPopupInflated, onDismiss) {
    override fun onViewCreated(binding: PopupGenreMenuBinding) {

    }
}