package app.simple.felicity.dialogs.recentlyplayed

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants.LayoutMode
import app.simple.felicity.extensions.dialogs.BaseLayoutMenuDialog
import app.simple.felicity.preferences.RecentlyPlayedPreferences

/**
 * Bottom-sheet menu dialog for the Recently Played panel containing the list style selector.
 *
 * @author Hamza417
 */
class RecentlyPlayedMenu : BaseLayoutMenuDialog() {

    override fun getLayoutMode(): LayoutMode = RecentlyPlayedPreferences.getGridSize()

    override fun setLayoutMode(mode: LayoutMode) {
        RecentlyPlayedPreferences.setGridSize(mode)
    }

    companion object {
        private const val TAG = "RecentlyPlayedMenu"

        fun newInstance(): RecentlyPlayedMenu {
            return RecentlyPlayedMenu().apply { arguments = Bundle() }
        }

        /**
         * Shows a [RecentlyPlayedMenu] bottom-sheet from the given [FragmentManager].
         */
        fun FragmentManager.showRecentlyPlayedMenu(): RecentlyPlayedMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}
