package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.constants.CommonPreferencesConstants.ASCENDING
import app.simple.felicity.constants.CommonPreferencesConstants.BY_ALBUM_NAME
import app.simple.felicity.constants.CommonPreferencesConstants.toLayoutMode
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.manager.SharedPreferences

object AlbumPreferences {
    const val ALBUM_SORT = "album_sort_"
    const val SORTING_STYLE = "_album_sorting_style__"
    const val GRID_SIZE_PORTRAIT = "album_grid_size_portrait1"
    const val GRID_SIZE_LANDSCAPE = "album_grid_size_landscape1"

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

    /**
     * Returns the current [CommonPreferencesConstants.LayoutMode] for the given orientation.
     */
    fun getGridSize(): CommonPreferencesConstants.LayoutMode {
        return if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences()
                .getString(GRID_SIZE_PORTRAIT, CommonPreferencesConstants.LayoutMode.LIST_ONE.name)!!.toLayoutMode()
        } else {
            SharedPreferences.getSharedPreferences()
                .getString(GRID_SIZE_LANDSCAPE, CommonPreferencesConstants.LayoutMode.GRID_TWO.name)!!.toLayoutMode()
        }
    }

    /**
     * Persists the [CommonPreferencesConstants.LayoutMode] for the current orientation.
     *
     * @param mode the layout mode to save
     */
    fun setGridSize(mode: CommonPreferencesConstants.LayoutMode) {
        if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences().edit { putString(GRID_SIZE_PORTRAIT, mode.name) }
        } else {
            SharedPreferences.getSharedPreferences().edit { putString(GRID_SIZE_LANDSCAPE, mode.name) }
        }
    }
}