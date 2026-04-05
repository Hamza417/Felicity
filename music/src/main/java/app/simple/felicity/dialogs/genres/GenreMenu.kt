package app.simple.felicity.dialogs.genres

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants.LayoutMode
import app.simple.felicity.extensions.dialogs.BaseLayoutMenuDialog
import app.simple.felicity.preferences.GenresPreferences

/**
 * Bottom-sheet menu dialog for the Genres panel containing the list style selector
 * and the genre cover toggle.
 *
 * @author Hamza417
 */
class GenreMenu : BaseLayoutMenuDialog() {

    override fun getLayoutMode(): LayoutMode = GenresPreferences.getGridSize()

    override fun setLayoutMode(mode: LayoutMode) {
        GenresPreferences.setGridSize(mode)
    }

    companion object {
        private const val TAG = "GenreMenu"

        fun newInstance(): GenreMenu {
            return GenreMenu().apply { arguments = Bundle() }
        }

        fun FragmentManager.showGenreMenu(): GenreMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}