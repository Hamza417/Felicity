package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.manager.SharedPreferences

object ArtistPreferences {
    const val ARTIST_SORT = "artist_sort"
    const val SORTING_STYLE = "artist_sorting_style"

    const val GRID_SIZE_PORTRAIT = "artist_grid_size"
    const val GRID_SIZE_LANDSCAPE = "artist_grid_size_landscape"
    const val GRID_TYPE_PORTRAIT = "artist_grid_type_portrait"
    const val GRID_TYPE_LANDSCAPE = "artist_grid_type_landscape"

    fun getArtistSort(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(ARTIST_SORT, CommonPreferencesConstants.BY_NAME)
    }

    fun setArtistSort(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(ARTIST_SORT, value)
        }
    }

    fun getSortingStyle(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(SORTING_STYLE, CommonPreferencesConstants.ASCENDING)
    }

    fun setSortingStyle(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(SORTING_STYLE, value)
        }
    }

    fun getGridSize(): Int {
        return if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences()
                .getInt(GRID_SIZE_PORTRAIT, CommonPreferencesConstants.GRID_SIZE_ONE)
        } else {
            SharedPreferences.getSharedPreferences()
                .getInt(GRID_SIZE_LANDSCAPE, CommonPreferencesConstants.GRID_SIZE_TWO)
        }
    }

    fun setGridSize(size: Int) {
        if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences().edit { putInt(GRID_SIZE_PORTRAIT, size) }
        } else {
            SharedPreferences.getSharedPreferences().edit { putInt(GRID_SIZE_LANDSCAPE, size) }
        }
    }

    fun getGridType(): Int {
        return if (AppOrientation.isLandscape()) {
            SharedPreferences.getSharedPreferences()
                .getInt(GRID_TYPE_LANDSCAPE, CommonPreferencesConstants.GRID_TYPE_LIST)
        } else {
            SharedPreferences.getSharedPreferences()
                .getInt(GRID_TYPE_PORTRAIT, CommonPreferencesConstants.GRID_TYPE_LIST)
        }
    }

    fun setGridType(type: Int) {
        if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences().edit { putInt(GRID_TYPE_PORTRAIT, type) }
        } else {
            SharedPreferences.getSharedPreferences().edit { putInt(GRID_TYPE_LANDSCAPE, type) }
        }
    }
}