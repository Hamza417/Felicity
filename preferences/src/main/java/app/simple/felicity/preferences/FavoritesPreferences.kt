package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.manager.SharedPreferences

/**
 * Shared-preference keys and accessors for the Favorites panel.
 * Mirrors [SongsPreferences] but uses a `favorites_` key namespace to keep
 * the Favorites sort/grid settings fully independent from Songs.
 *
 * @author Hamza417
 */
object FavoritesPreferences {

    const val SONG_SORT = "favorites_song_sort"
    const val SORTING_STYLE = "favorites_sorting_style"
    const val GRID_SIZE_PORTRAIT = "favorites_grid_size_portrait"
    const val GRID_SIZE_LANDSCAPE = "favorites_grid_size_landscape"
    const val GRID_TYPE_PORTRAIT = "favorites_grid_type_portrait"
    const val GRID_TYPE_LANDSCAPE = "favorites_grid_type_landscape"

    /**
     * Returns the current sort field for Favorites (defaults to [CommonPreferencesConstants.BY_TITLE]).
     */
    fun getSongSort(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(SONG_SORT, CommonPreferencesConstants.BY_TITLE)
    }

    /**
     * Persists the sort field for Favorites.
     *
     * @param value one of the `BY_*` constants from [CommonPreferencesConstants]
     */
    fun setSongSort(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(SONG_SORT, value)
        }
    }

    /**
     * Returns the current sorting direction (ascending / descending).
     */
    fun getSortingStyle(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(SORTING_STYLE, CommonPreferencesConstants.ASCENDING)
    }

    /**
     * Persists the sorting direction.
     *
     * @param value [CommonPreferencesConstants.ASCENDING] or [CommonPreferencesConstants.DESCENDING]
     */
    fun setSortingStyle(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(SORTING_STYLE, value)
        }
    }

    /**
     * Returns the grid span count for the current orientation.
     */
    fun getGridSize(): Int {
        return if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences()
                .getInt(GRID_SIZE_PORTRAIT, CommonPreferencesConstants.GRID_SIZE_ONE)
        } else {
            SharedPreferences.getSharedPreferences()
                .getInt(GRID_SIZE_LANDSCAPE, CommonPreferencesConstants.GRID_SIZE_TWO)
        }
    }

    /**
     * Persists the grid span count for the current orientation.
     *
     * @param size one of the `GRID_SIZE_*` constants from [CommonPreferencesConstants]
     */
    fun setGridSize(size: Int) {
        if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences().edit { putInt(GRID_SIZE_PORTRAIT, size) }
        } else {
            SharedPreferences.getSharedPreferences().edit { putInt(GRID_SIZE_LANDSCAPE, size) }
        }
    }

    /**
     * Returns the grid type (list vs grid) for the current orientation.
     */
    fun getGridType(): Int {
        return if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences()
                .getInt(GRID_TYPE_PORTRAIT, CommonPreferencesConstants.GRID_TYPE_LIST)
        } else {
            SharedPreferences.getSharedPreferences()
                .getInt(GRID_TYPE_LANDSCAPE, CommonPreferencesConstants.GRID_TYPE_LIST)
        }
    }

    /**
     * Persists the grid type for the current orientation.
     *
     * @param type [CommonPreferencesConstants.GRID_TYPE_LIST] or [CommonPreferencesConstants.GRID_TYPE_GRID]
     */
    fun setGridType(type: Int) {
        if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences().edit { putInt(GRID_TYPE_PORTRAIT, type) }
        } else {
            SharedPreferences.getSharedPreferences().edit { putInt(GRID_TYPE_LANDSCAPE, type) }
        }
    }
}

