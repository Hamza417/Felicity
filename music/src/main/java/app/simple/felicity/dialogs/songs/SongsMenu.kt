package app.simple.felicity.dialogs.songs

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants.LayoutMode
import app.simple.felicity.extensions.dialogs.BaseLayoutMenuDialog
import app.simple.felicity.preferences.SongsPreferences

/**
 * Bottom-sheet menu dialog for the Songs panel containing the list style selector.
 *
 * @author Hamza417
 */
class SongsMenu : BaseLayoutMenuDialog() {

    override fun getLayoutMode(): LayoutMode = SongsPreferences.getGridSize()

    override fun setLayoutMode(mode: LayoutMode) {
        SongsPreferences.setGridSize(mode)
    }

    companion object {
        private const val TAG = "SongsMenu"

        fun newInstance(): SongsMenu {
            return SongsMenu().apply { arguments = Bundle() }
        }

        fun FragmentManager.showSongsMenu(): SongsMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}