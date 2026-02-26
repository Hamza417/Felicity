package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.constants.CommonPreferencesConstants.ASCENDING
import app.simple.felicity.constants.CommonPreferencesConstants.BY_NAME
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.manager.SharedPreferences

object FolderHierarchyPreferences {

    const val GRID_SIZE_PORTRAIT = "folder_hierarchy_grid_size_portrait"
    const val GRID_SIZE_LANDSCAPE = "folder_hierarchy_grid_size_landscape"
    const val GRID_TYPE_PORTRAIT = "folder_hierarchy_grid_type_portrait"
    const val GRID_TYPE_LANDSCAPE = "folder_hierarchy_grid_type_landscape"
    const val SORT_STYLE = "folder_hierarchy_sort_style"
    const val SORT_ORDER = "folder_hierarchy_sort_order"

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
        return SharedPreferences.getSharedPreferences().getInt(SORT_STYLE, BY_NAME)
    }

    fun setSortStyle(sort: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(SORT_STYLE, sort) }
    }

    // -------------------------------------------------------------------------------------------- //

    fun getSortOrder(): Int {
        return SharedPreferences.getSharedPreferences().getInt(SORT_ORDER, ASCENDING)
    }

    fun setSortOrder(order: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(SORT_ORDER, order) }
    }
}

