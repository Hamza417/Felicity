package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.manager.SharedPreferences

object SearchPreferences {

    const val SONG_SORT = "search_sort_"
    const val SORTING_STYLE = "search_sorting_style_"
    const val GRID_SIZE_PORTRAIT = "search_grid_size_portrait"
    const val GRID_SIZE_LANDSCAPE = "search_grid_size_landscape"
    const val GRID_TYPE_PORTRAIT = "search_grid_type_portrait"
    const val GRID_TYPE_LANDSCAPE = "search_grid_type_landscape"

    // ----------------------------------------------------------------------------------------- //

    fun getSongSort(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(SongsPreferences.SONG_SORT, CommonPreferencesConstants.BY_TITLE)
    }

    fun setSongSort(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(SongsPreferences.SONG_SORT, value)
        }
    }

    // ----------------------------------------------------------------------------------------- //

    fun getSortingStyle(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(SongsPreferences.SORTING_STYLE, CommonPreferencesConstants.ASCENDING)
    }

    fun setSortingStyle(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(SongsPreferences.SORTING_STYLE, value)
        }
    }

    // ----------------------------------------------------------------------------------------- //

    fun getGridSize(): Int {
        return if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences()
                .getInt(SongsPreferences.GRID_SIZE_PORTRAIT, CommonPreferencesConstants.GRID_SIZE_ONE)
        } else {
            SharedPreferences.getSharedPreferences()
                .getInt(SongsPreferences.GRID_SIZE_LANDSCAPE, CommonPreferencesConstants.GRID_SIZE_TWO)
        }
    }

    fun setGridSize(size: Int) {
        if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences().edit { putInt(SongsPreferences.GRID_SIZE_PORTRAIT, size) }
        } else {
            SharedPreferences.getSharedPreferences().edit { putInt(SongsPreferences.GRID_SIZE_LANDSCAPE, size) }
        }
    }

    // ----------------------------------------------------------------------------------------- //

    fun getGridType(): Int {
        return if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences()
                .getInt(SongsPreferences.GRID_TYPE_PORTRAIT, CommonPreferencesConstants.GRID_TYPE_LIST)
        } else {
            SharedPreferences.getSharedPreferences()
                .getInt(SongsPreferences.GRID_TYPE_LANDSCAPE, CommonPreferencesConstants.GRID_TYPE_LIST)
        }
    }

    fun setGridType(type: Int) {
        if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences().edit { putInt(SongsPreferences.GRID_TYPE_PORTRAIT, type) }
        } else {
            SharedPreferences.getSharedPreferences().edit { putInt(SongsPreferences.GRID_TYPE_LANDSCAPE, type) }
        }
    }
}