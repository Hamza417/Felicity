package app.simple.felicity.dialogs.recentlyadded

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants.LayoutMode
import app.simple.felicity.extensions.dialogs.BaseLayoutMenuDialog
import app.simple.felicity.preferences.RecentlyAddedPreferences

/**
 * Bottom-sheet menu dialog for the Recently Added panel containing the list style selector.
 *
 * @author Hamza417
 */
class RecentlyAddedMenu : BaseLayoutMenuDialog() {

    override fun getLayoutMode(): LayoutMode = RecentlyAddedPreferences.getGridSize()

    override fun setLayoutMode(mode: LayoutMode) {
        RecentlyAddedPreferences.setGridSize(mode)
    }

    companion object {
        private const val TAG = "RecentlyAddedMenu"

        fun newInstance(): RecentlyAddedMenu {
            return RecentlyAddedMenu().apply { arguments = Bundle() }
        }

        /**
         * Shows a [RecentlyAddedMenu] bottom-sheet from the given [FragmentManager].
         */
        fun FragmentManager.showRecentlyAddedMenu(): RecentlyAddedMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}
