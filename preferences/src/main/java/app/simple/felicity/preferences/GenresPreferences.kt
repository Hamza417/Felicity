package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.constants.CommonPreferencesConstants.ASCENDING
import app.simple.felicity.constants.CommonPreferencesConstants.BY_NAME
import app.simple.felicity.constants.CommonPreferencesConstants.toLayoutMode
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.manager.SharedPreferences

object GenresPreferences {

    const val GRID_SIZE_PORTRAIT = "genres_grid_size_portrait1"
    const val GRID_SIZE_LANDSCAPE = "genres_grid_size_landscape1"
    const val SHOW_GENRE_COVERS = "show_genre_covers"
    const val GENRE_SORT_STYLE = "genre_sort"
    const val SORT_ORDER = "genre_sorting_style"

    // --------------------------------------------------------------------------------------------

    /**
     * Returns the current [CommonPreferencesConstants.LayoutMode] for the given orientation.
     */
    fun getGridSize(): CommonPreferencesConstants.LayoutMode {
        return if (AppOrientation.isLandscape()) {
            SharedPreferences.getSharedPreferences()
                .getString(GRID_SIZE_LANDSCAPE, CommonPreferencesConstants.LayoutMode.GRID_TWO.name)!!.toLayoutMode()
        } else {
            SharedPreferences.getSharedPreferences()
                .getString(GRID_SIZE_PORTRAIT, CommonPreferencesConstants.LayoutMode.LIST_ONE.name)!!.toLayoutMode()
        }
    }

    /**
     * Persists the [CommonPreferencesConstants.LayoutMode] for the current orientation.
     *
     * @param mode the layout mode to save
     */
    fun setGridSize(mode: CommonPreferencesConstants.LayoutMode) {
        if (AppOrientation.isLandscape()) {
            SharedPreferences.getSharedPreferences().edit { putString(GRID_SIZE_LANDSCAPE, mode.name) }
        } else {
            SharedPreferences.getSharedPreferences().edit { putString(GRID_SIZE_PORTRAIT, mode.name) }
        }
    }

    // --------------------------------------------------------------------------------------------

    fun isGenreCoversEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(SHOW_GENRE_COVERS, true)
    }

    fun setGenreCoversEnabled(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(SHOW_GENRE_COVERS, enabled) }
    }

    // --------------------------------------------------------------------------------------------

    fun getSortStyle(): Int {
        return SharedPreferences.getSharedPreferences().getInt(GENRE_SORT_STYLE, BY_NAME)
    }

    fun setSortStyle(sort: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(GENRE_SORT_STYLE, sort) }
    }

    // --------------------------------------------------------------------------------------------

    fun getSortOrder(): Int {
        return SharedPreferences.getSharedPreferences().getInt(SORT_ORDER, ASCENDING)
    }

    fun setSortOrder(order: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(SORT_ORDER, order) }
    }
}