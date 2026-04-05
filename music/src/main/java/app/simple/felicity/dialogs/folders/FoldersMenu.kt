package app.simple.felicity.dialogs.folders

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants.LayoutMode
import app.simple.felicity.extensions.dialogs.BaseLayoutMenuDialog
import app.simple.felicity.preferences.FoldersPreferences

/**
 * Bottom-sheet menu dialog for the Folders panel containing the list style selector.
 *
 * @author Hamza417
 */
class FoldersMenu : BaseLayoutMenuDialog() {

    override fun getLayoutMode(): LayoutMode = FoldersPreferences.getGridSize()

    override fun setLayoutMode(mode: LayoutMode) {
        FoldersPreferences.setGridSize(mode)
    }

    companion object {
        private const val TAG = "FoldersMenu"

        fun newInstance(): FoldersMenu {
            return FoldersMenu().apply { arguments = Bundle() }
        }

        /**
         * Shows a [FoldersMenu] bottom-sheet from the given [FragmentManager].
         */
        fun FragmentManager.showFoldersMenu(): FoldersMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}
