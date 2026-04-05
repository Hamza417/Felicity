package app.simple.felicity.dialogs.favorites

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants.LayoutMode
import app.simple.felicity.extensions.dialogs.BaseLayoutMenuDialog
import app.simple.felicity.preferences.FavoritesPreferences

/**
 * Bottom-sheet menu dialog for the Favorites panel containing the list style selector.
 *
 * @author Hamza417
 */
class FavoritesMenu : BaseLayoutMenuDialog() {

    override fun getLayoutMode(): LayoutMode = FavoritesPreferences.getGridSize()

    override fun setLayoutMode(mode: LayoutMode) {
        FavoritesPreferences.setGridSize(mode)
    }

    companion object {
        private const val TAG = "FavoritesMenu"

        fun newInstance(): FavoritesMenu {
            return FavoritesMenu().apply { arguments = Bundle() }
        }

        /**
         * Shows a [FavoritesMenu] bottom-sheet from the given [FragmentManager].
         */
        fun FragmentManager.showFavoritesMenu(): FavoritesMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}
