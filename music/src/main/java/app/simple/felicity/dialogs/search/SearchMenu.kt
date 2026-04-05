package app.simple.felicity.dialogs.search

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants.LayoutMode
import app.simple.felicity.extensions.dialogs.BaseLayoutMenuDialog
import app.simple.felicity.preferences.SearchPreferences

/**
 * Bottom-sheet menu dialog for the Search panel containing the list style selector.
 *
 * @author Hamza417
 */
class SearchMenu : BaseLayoutMenuDialog() {

    override fun getLayoutMode(): LayoutMode = SearchPreferences.getGridSize()

    override fun setLayoutMode(mode: LayoutMode) {
        SearchPreferences.setGridSize(mode)
    }

    companion object {
        private const val TAG = "SearchMenu"

        fun newInstance(): SearchMenu {
            return SearchMenu().apply { arguments = Bundle() }
        }

        fun FragmentManager.showSearchMenu(): SearchMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}