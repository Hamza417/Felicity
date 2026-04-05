package app.simple.felicity.dialogs.albums

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants.LayoutMode
import app.simple.felicity.extensions.dialogs.BaseLayoutMenuDialog
import app.simple.felicity.preferences.AlbumPreferences

/**
 * Bottom-sheet menu dialog for the Albums panel containing the list style selector.
 *
 * @author Hamza417
 */
class AlbumsMenu : BaseLayoutMenuDialog() {

    override fun getLayoutMode(): LayoutMode = AlbumPreferences.getGridSize()

    override fun setLayoutMode(mode: LayoutMode) {
        AlbumPreferences.setGridSize(mode)
    }

    companion object {
        private const val TAG = "AlbumsMenu"

        fun newInstance(): AlbumsMenu {
            return AlbumsMenu().apply { arguments = Bundle() }
        }

        /**
         * Shows an [AlbumsMenu] bottom-sheet from the given [FragmentManager].
         */
        fun FragmentManager.showAlbumsMenu(): AlbumsMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}
