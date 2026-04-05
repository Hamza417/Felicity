package app.simple.felicity.dialogs.mostplayed

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants.LayoutMode
import app.simple.felicity.extensions.dialogs.BaseLayoutMenuDialog
import app.simple.felicity.preferences.MostPlayedPreferences

/**
 * Bottom-sheet menu dialog for the Most Played panel containing the list style selector.
 *
 * @author Hamza417
 */
class MostPlayedMenu : BaseLayoutMenuDialog() {

    override fun getLayoutMode(): LayoutMode = MostPlayedPreferences.getGridSize()

    override fun setLayoutMode(mode: LayoutMode) {
        MostPlayedPreferences.setGridSize(mode)
    }

    companion object {
        private const val TAG = "MostPlayedMenu"

        fun newInstance(): MostPlayedMenu {
            return MostPlayedMenu().apply { arguments = Bundle() }
        }

        /**
         * Shows a [MostPlayedMenu] bottom-sheet from the given [FragmentManager].
         */
        fun FragmentManager.showMostPlayedMenu(): MostPlayedMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}
