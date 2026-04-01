package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.constants.CommonPreferencesConstants.ASCENDING
import app.simple.felicity.constants.CommonPreferencesConstants.BY_YEAR
import app.simple.felicity.constants.CommonPreferencesConstants.toLayoutMode
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.manager.SharedPreferences

/**
 * Persisted display and sort preferences for the Year panel.
 *
 * @author Hamza417
 */
object YearPreferences {

    const val GRID_SIZE_PORTRAIT = "year_grid_size_portrait1"
    const val GRID_SIZE_LANDSCAPE = "year_grid_size_landscape1"
    const val YEAR_SORT_STYLE = "year_sort_style"
    const val SORT_ORDER = "year_sort_order"

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
        return SharedPreferences.getSharedPreferences().getInt(YEAR_SORT_STYLE, BY_YEAR)
    }

    fun setSortStyle(sort: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(YEAR_SORT_STYLE, sort) }
    }

    // --------------------------------------------------------------------------------------------

    fun getSortOrder(): Int {
        return SharedPreferences.getSharedPreferences().getInt(SORT_ORDER, ASCENDING)
    }

    fun setSortOrder(order: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(SORT_ORDER, order) }
    }
}
