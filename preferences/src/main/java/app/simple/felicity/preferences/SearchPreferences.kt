package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.constants.CommonPreferencesConstants.toLayoutMode
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.manager.SharedPreferences

object SearchPreferences {

    const val SONG_SORT = "search_sort_"
    const val SORTING_STYLE = "search_sorting_style_"
    const val GRID_SIZE_PORTRAIT = "search_grid_size_portrait1"
    const val GRID_SIZE_LANDSCAPE = "search_grid_size_landscape1"

    // ----------------------------------------------------------------------------------------- //

    fun getSongSort(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(SONG_SORT, CommonPreferencesConstants.BY_TITLE)
    }

    fun setSongSort(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(SONG_SORT, value)
        }
    }

    // ----------------------------------------------------------------------------------------- //

    fun getSortingStyle(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(SORTING_STYLE, CommonPreferencesConstants.ASCENDING)
    }

    fun setSortingStyle(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(SORTING_STYLE, value)
        }
    }

    // ----------------------------------------------------------------------------------------- //

    fun getGridSize(): CommonPreferencesConstants.LayoutMode {
        return if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences()
                .getString(GRID_SIZE_PORTRAIT, CommonPreferencesConstants.LayoutMode.LIST_ONE.name)!!.toLayoutMode()
        } else {
            SharedPreferences.getSharedPreferences()
                .getString(GRID_SIZE_LANDSCAPE, CommonPreferencesConstants.LayoutMode.GRID_TWO.name)!!.toLayoutMode()
        }
    }

    fun setGridSize(mode: CommonPreferencesConstants.LayoutMode) {
        if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences().edit { putString(GRID_SIZE_PORTRAIT, mode.name) }
        } else {
            SharedPreferences.getSharedPreferences().edit { putString(GRID_SIZE_LANDSCAPE, mode.name) }
        }
    }
}