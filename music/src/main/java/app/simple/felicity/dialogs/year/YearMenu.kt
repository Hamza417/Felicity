package app.simple.felicity.dialogs.year

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants.LayoutMode
import app.simple.felicity.extensions.dialogs.BaseLayoutMenuDialog
import app.simple.felicity.preferences.YearPreferences

/**
 * Bottom-sheet menu dialog for the Year panel containing the list style selector.
 *
 * @author Hamza417
 */
class YearMenu : BaseLayoutMenuDialog() {

    override fun getLayoutMode(): LayoutMode = YearPreferences.getGridSize()

    override fun setLayoutMode(mode: LayoutMode) {
        YearPreferences.setGridSize(mode)
    }

    companion object {
        private const val TAG = "YearMenu"

        fun newInstance(): YearMenu {
            return YearMenu().apply { arguments = Bundle() }
        }

        /**
         * Shows a [YearMenu] bottom-sheet from the given [FragmentManager].
         */
        fun FragmentManager.showYearMenu(): YearMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}
