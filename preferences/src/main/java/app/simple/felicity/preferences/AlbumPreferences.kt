package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.constants.CommonPreferencesConstants.ACCENDING
import app.simple.felicity.constants.CommonPreferencesConstants.BY_ALBUM_NAME
import app.simple.felicity.manager.SharedPreferences

object AlbumPreferences {
    const val ALBUM_SORT = "album_sort_"
    const val SORTING_STYLE = "album_sorting_style_"
    const val GRID_SIZE = "album_grid_size"

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
            .getInt(SORTING_STYLE, ACCENDING)
    }

    fun setSortingStyle(value: String) {
        SharedPreferences.getSharedPreferences().edit {
            putString(SORTING_STYLE, value)
        }
    }

    fun getGridSize(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(GRID_SIZE, CommonPreferencesConstants.GRID_SIZE_ONE)
    }

    fun setGridSize(size: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(GRID_SIZE, size) }
    }
}