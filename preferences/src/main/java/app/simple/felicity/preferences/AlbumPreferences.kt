package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.constants.CommonPreferencesConstants.ASCENDING
import app.simple.felicity.constants.CommonPreferencesConstants.BY_ALBUM_NAME
import app.simple.felicity.manager.SharedPreferences

object AlbumPreferences {
    const val ALBUM_SORT = "album_sort_"
    const val SORTING_STYLE = "_album_sorting_style__"
    const val GRID_SIZE_PORTRAIT = "album_grid_size"
    const val GRID_SIZE_LANDSCAPE = "album_grid_size_landscape"
    const val GRID_TYPE = "album_grid_type"

    // ----------------------------------------------------------------------------------------- //

    const val ALBUM_INTERFACE_DEFAULT = "default"
    const val ALBUM_INTERFACE_FLOW = "flow"

    // ----------------------------------------------------------------------------------------- //

    fun getAlbumSort(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(ALBUM_SORT, BY_ALBUM_NAME)
    }

    fun setAlbumSort(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(ALBUM_SORT, value)
        }
    }

    fun getSortingStyle(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(SORTING_STYLE, ASCENDING)
    }

    fun setSortingStyle(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(SORTING_STYLE, value)
        }
    }

    fun getGridSize(isLandscape: Boolean): Int {
        return if (isLandscape.not()) {
            SharedPreferences.getSharedPreferences()
                .getInt(GRID_SIZE_PORTRAIT, CommonPreferencesConstants.GRID_SIZE_ONE)
        } else {
            SharedPreferences.getSharedPreferences()
                .getInt(GRID_SIZE_LANDSCAPE, CommonPreferencesConstants.GRID_SIZE_TWO)
        }
    }

    fun setGridSize(size: Int, isLandscape: Boolean) {
        if (isLandscape.not()) {
            SharedPreferences.getSharedPreferences().edit { putInt(GRID_SIZE_PORTRAIT, size) }
        } else {
            SharedPreferences.getSharedPreferences().edit { putInt(GRID_SIZE_LANDSCAPE, size) }
        }
    }

    fun getGridType(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(GRID_TYPE, CommonPreferencesConstants.GRID_TYPE_LIST)
    }

    fun setGridType(type: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(GRID_TYPE, type) }
    }
}