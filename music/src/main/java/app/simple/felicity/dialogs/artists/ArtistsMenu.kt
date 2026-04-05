package app.simple.felicity.dialogs.artists

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants.LayoutMode
import app.simple.felicity.extensions.dialogs.BaseLayoutMenuDialog
import app.simple.felicity.preferences.ArtistPreferences

/**
 * Bottom-sheet menu dialog for the Artists panel containing the list style selector.
 *
 * @author Hamza417
 */
class ArtistsMenu : BaseLayoutMenuDialog() {

    override fun getLayoutMode(): LayoutMode = ArtistPreferences.getGridSize()

    override fun setLayoutMode(mode: LayoutMode) {
        ArtistPreferences.setGridSize(mode)
    }

    companion object {
        private const val TAG = "ArtistsMenu"

        fun newInstance(): ArtistsMenu {
            return ArtistsMenu().apply { arguments = Bundle() }
        }

        /**
         * Shows an [ArtistsMenu] bottom-sheet from the given [FragmentManager].
         */
        fun FragmentManager.showArtistsMenu(): ArtistsMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}
