package app.simple.felicity.dialogs.albumartists

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants.LayoutMode
import app.simple.felicity.extensions.dialogs.BaseLayoutMenuDialog
import app.simple.felicity.preferences.AlbumArtistPreferences

/**
 * Bottom-sheet menu dialog for the Album Artists panel, letting users switch
 * between list, grid, and label display styles. Just like the Artists one but
 * its own creature so the two panels don't share settings by accident.
 *
 * @author Hamza417
 */
class AlbumArtistsMenu : BaseLayoutMenuDialog() {

    override fun getLayoutMode(): LayoutMode = AlbumArtistPreferences.getGridSize()

    override fun setLayoutMode(mode: LayoutMode) {
        AlbumArtistPreferences.setGridSize(mode)
    }

    companion object {
        private const val TAG = "AlbumArtistsMenu"

        fun newInstance(): AlbumArtistsMenu {
            return AlbumArtistsMenu().apply { arguments = Bundle() }
        }

        /**
         * Shows an [AlbumArtistsMenu] bottom-sheet from the given [FragmentManager].
         */
        fun FragmentManager.showAlbumArtistsMenu(): AlbumArtistsMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}

