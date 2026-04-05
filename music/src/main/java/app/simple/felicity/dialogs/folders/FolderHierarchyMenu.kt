package app.simple.felicity.dialogs.folders

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants.LayoutMode
import app.simple.felicity.extensions.dialogs.BaseLayoutMenuDialog
import app.simple.felicity.preferences.FolderHierarchyPreferences

/**
 * Bottom-sheet menu dialog for the Folders Hierarchy panel containing the list style selector.
 *
 * Restricts the available layout modes to list and grid variants only, since the
 * [app.simple.felicity.adapters.ui.lists.AdapterFolderHierarchy] does not support
 * label-style view types.
 *
 * @author Hamza417
 */
class FolderHierarchyMenu : BaseLayoutMenuDialog() {

    override fun getLayoutMode(): LayoutMode = FolderHierarchyPreferences.getLayoutMode()

    override fun setLayoutMode(mode: LayoutMode) {
        FolderHierarchyPreferences.setLayoutMode(mode)
    }

    override fun buildLayoutModes(isLandscape: Boolean): List<LayoutMode> {
        return if (isLandscape) {
            listOf(
                    LayoutMode.LIST_ONE,
                    LayoutMode.LIST_TWO,
                    LayoutMode.LIST_THREE,
                    LayoutMode.GRID_TWO,
                    LayoutMode.GRID_THREE,
                    LayoutMode.GRID_FOUR,
                    LayoutMode.GRID_FIVE,
                    LayoutMode.GRID_SIX,
            )
        } else {
            listOf(
                    LayoutMode.LIST_ONE,
                    LayoutMode.LIST_TWO,
                    LayoutMode.GRID_TWO,
                    LayoutMode.GRID_THREE,
                    LayoutMode.GRID_FOUR,
            )
        }
    }

    companion object {
        private const val TAG = "FolderHierarchyMenu"

        fun newInstance(): FolderHierarchyMenu {
            return FolderHierarchyMenu().apply { arguments = Bundle() }
        }

        /**
         * Shows a [FolderHierarchyMenu] bottom-sheet from the given [FragmentManager].
         */
        fun FragmentManager.showFolderHierarchyMenu(): FolderHierarchyMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}

