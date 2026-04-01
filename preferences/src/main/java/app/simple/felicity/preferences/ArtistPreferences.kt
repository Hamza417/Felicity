package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.constants.CommonPreferencesConstants.toLayoutMode
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.manager.SharedPreferences

object ArtistPreferences {
    const val ARTIST_SORT = "artist_sort"
    const val SORTING_STYLE = "artist_sorting_style"

    const val GRID_SIZE_PORTRAIT = "artist_grid_size_portrait1"
    const val GRID_SIZE_LANDSCAPE = "artist_grid_size_landscape1"

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