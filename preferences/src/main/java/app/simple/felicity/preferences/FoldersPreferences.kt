package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.constants.CommonPreferencesConstants.ASCENDING
import app.simple.felicity.constants.CommonPreferencesConstants.BY_NAME
import app.simple.felicity.constants.CommonPreferencesConstants.toLayoutMode
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.manager.SharedPreferences

/**
 * Persisted display and sort preferences for the Folders panel.
 *
 * @author Hamza417
 */
object FoldersPreferences {

    const val GRID_SIZE_PORTRAIT = "folders_grid_size_portrait1"
    const val GRID_SIZE_LANDSCAPE = "folders_grid_size_landscape1"
    const val FOLDER_SORT_STYLE = "folder_sort_style"
    const val SORT_ORDER = "folder_sort_order"

    /**
     * Returns the current [CommonPreferencesConstants.LayoutMode] for the given orientation.
     */
    fun getGridSize(): CommonPreferencesConstants.LayoutMode {
        return if (AppOrientation.isLandscape()) {
            SharedPreferences.getSharedPreferences()
                .getString(GRID_SIZE_LANDSCAPE, CommonPreferencesConstants.LayoutMode.GRID_TWO.name)!!.toLayoutMode()
        } else {
            SharedPreferences.getSharedPreferences()
                .getString(GRID_SIZE_PORTRAIT, CommonPreferencesConstants.LayoutMode.LIST_ONE.name)!!.toLayoutMode()
        }
    }

    /**
     * Persists the [CommonPreferencesConstants.LayoutMode] for the current orientation.
     *
     * @param mode the layout mode to save
     */
    fun setGridSize(mode: CommonPreferencesConstants.LayoutMode) {
        if (AppOrientation.isLandscape()) {
            SharedPreferences.getSharedPreferences().edit { putString(GRID_SIZE_LANDSCAPE, mode.name) }
        } else {
            SharedPreferences.getSharedPreferences().edit { putString(GRID_SIZE_PORTRAIT, mode.name) }
        }
    }

    // --------------------------------------------------------------------------------------------

    fun getSortStyle(): Int {
        return SharedPreferences.getSharedPreferences().getInt(FOLDER_SORT_STYLE, BY_NAME)
    }

    fun setSortStyle(sort: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(FOLDER_SORT_STYLE, sort) }
    }

    // --------------------------------------------------------------------------------------------

    fun getSortOrder(): Int {
        return SharedPreferences.getSharedPreferences().getInt(SORT_ORDER, ASCENDING)
    }

    fun setSortOrder(order: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(SORT_ORDER, order) }
    }
}
