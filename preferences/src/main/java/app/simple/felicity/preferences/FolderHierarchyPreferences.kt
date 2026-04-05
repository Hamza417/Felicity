package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants.ASCENDING
import app.simple.felicity.constants.CommonPreferencesConstants.BY_NAME
import app.simple.felicity.constants.CommonPreferencesConstants.LayoutMode
import app.simple.felicity.constants.CommonPreferencesConstants.toLayoutMode
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.manager.SharedPreferences

/**
 * Shared preference accessors for the Folders Hierarchy panel.
 *
 * @author Hamza417
 */
object FolderHierarchyPreferences {

    const val LAYOUT_MODE_PORTRAIT = "folder_hierarchy_layout_mode_portrait"
    const val LAYOUT_MODE_LANDSCAPE = "folder_hierarchy_layout_mode_landscape"
    const val SORT_STYLE = "folder_hierarchy_sort_style"
    const val SORT_ORDER = "folder_hierarchy_sort_order"

    fun getLayoutMode(): LayoutMode {
        return if (AppOrientation.isLandscape()) {
            SharedPreferences.getSharedPreferences()
                .getString(LAYOUT_MODE_LANDSCAPE, LayoutMode.LIST_TWO.name)!!.toLayoutMode()
        } else {
            SharedPreferences.getSharedPreferences()
                .getString(LAYOUT_MODE_PORTRAIT, LayoutMode.LIST_ONE.name)!!.toLayoutMode()
        }
    }

    fun setLayoutMode(mode: LayoutMode) {
        if (AppOrientation.isLandscape()) {
            SharedPreferences.getSharedPreferences().edit { putString(LAYOUT_MODE_LANDSCAPE, mode.name) }
        } else {
            SharedPreferences.getSharedPreferences().edit { putString(LAYOUT_MODE_PORTRAIT, mode.name) }
        }
    }

    fun getSortStyle(): Int {
        return SharedPreferences.getSharedPreferences().getInt(SORT_STYLE, BY_NAME)
    }

    fun setSortStyle(sort: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(SORT_STYLE, sort) }
    }

    fun getSortOrder(): Int {
        return SharedPreferences.getSharedPreferences().getInt(SORT_ORDER, ASCENDING)
    }

    fun setSortOrder(order: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(SORT_ORDER, order) }
    }
}

