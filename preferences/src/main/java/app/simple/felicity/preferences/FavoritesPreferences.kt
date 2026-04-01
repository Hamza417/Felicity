package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.constants.CommonPreferencesConstants.toLayoutMode
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
    const val GRID_SIZE_PORTRAIT = "favorites_grid_size_portrait1"
    const val GRID_SIZE_LANDSCAPE = "favorites_grid_size_landscape1"

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
