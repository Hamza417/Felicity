package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.constants.CommonPreferencesConstants.ASCENDING
import app.simple.felicity.constants.CommonPreferencesConstants.BY_YEAR
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.manager.SharedPreferences

object YearPreferences {

    const val GRID_SIZE_PORTRAIT = "year_grid_size_portrait"
    const val GRID_SIZE_LANDSCAPE = "year_grid_size_landscape"
    const val GRID_TYPE_PORTRAIT = "year_grid_type_portrait"
    const val GRID_TYPE_LANDSCAPE = "year_grid_type_landscape"
    const val YEAR_SORT_STYLE = "year_sort_style"
    const val SORT_ORDER = "year_sort_order"

    // -------------------------------------------------------------------------------------------- //

    fun getGridSize(): Int {
        return if (AppOrientation.isLandscape()) {
            SharedPreferences.getSharedPreferences().getInt(GRID_SIZE_LANDSCAPE, CommonPreferencesConstants.GRID_SIZE_TWO)
        } else {
            SharedPreferences.getSharedPreferences().getInt(GRID_SIZE_PORTRAIT, CommonPreferencesConstants.GRID_SIZE_ONE)
        }
    }

    fun setGridSize(size: Int) {
        if (AppOrientation.isLandscape()) {
            SharedPreferences.getSharedPreferences().edit { putInt(GRID_SIZE_LANDSCAPE, size) }
        } else {
            SharedPreferences.getSharedPreferences().edit { putInt(GRID_SIZE_PORTRAIT, size) }
        }
    }

    // -------------------------------------------------------------------------------------------- //

    fun getGridType(): Int {
        return if (AppOrientation.isLandscape()) {
            SharedPreferences.getSharedPreferences().getInt(GRID_TYPE_LANDSCAPE, CommonPreferencesConstants.GRID_TYPE_LIST)
        } else {
            SharedPreferences.getSharedPreferences().getInt(GRID_TYPE_PORTRAIT, CommonPreferencesConstants.GRID_TYPE_LIST)
        }
    }

    fun setGridType(type: Int) {
        if (AppOrientation.isLandscape()) {
            SharedPreferences.getSharedPreferences().edit { putInt(GRID_TYPE_LANDSCAPE, type) }
        } else {
            SharedPreferences.getSharedPreferences().edit { putInt(GRID_TYPE_PORTRAIT, type) }
        }
    }

    // -------------------------------------------------------------------------------------------- //

    fun getSortStyle(): Int {
        return SharedPreferences.getSharedPreferences().getInt(YEAR_SORT_STYLE, BY_YEAR)
    }

    fun setSortStyle(sort: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(YEAR_SORT_STYLE, sort) }
    }

    // -------------------------------------------------------------------------------------------- //

    fun getSortOrder(): Int {
        return SharedPreferences.getSharedPreferences().getInt(SORT_ORDER, ASCENDING)
    }

    fun setSortOrder(order: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(SORT_ORDER, order) }
    }
}

