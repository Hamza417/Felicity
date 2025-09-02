package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.constants.CommonPreferencesConstants.ASCENDING
import app.simple.felicity.constants.CommonPreferencesConstants.BY_NAME
import app.simple.felicity.manager.SharedPreferences

object GenresPreferences {

    const val GRID_SIZE = "genres_grid_size"
    const val SHOW_GENRE_COVERS = "show_genre_covers"
    const val GENRE_SORT_STYLE = "genre_sort"
    const val SORT_ORDER = "genre_sorting_style"

    // -------------------------------------------------------------------------------------------- //

    fun getGridSize(): Int {
        return SharedPreferences.getSharedPreferences().getInt(GRID_SIZE, CommonPreferencesConstants.GRID_SIZE_ONE)
    }

    fun setGridSize(size: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(GRID_SIZE, size) }
    }

    // -------------------------------------------------------------------------------------------- //

    fun isGenreCoversEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(SHOW_GENRE_COVERS, true)
    }

    fun setGenreCoversEnabled(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(SHOW_GENRE_COVERS, enabled) }
    }

    // -------------------------------------------------------------------------------------------- //

    fun getSortStyle(): Int {
        return SharedPreferences.getSharedPreferences().getInt(GENRE_SORT_STYLE, BY_NAME)
    }

    fun setSortStyle(sort: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(GENRE_SORT_STYLE, sort) }
    }

    // -------------------------------------------------------------------------------------------- //

    fun getSortOrder(): Int {
        return SharedPreferences.getSharedPreferences().getInt(SORT_ORDER, ASCENDING)
    }

    fun setSortOrder(order: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(SORT_ORDER, order) }
    }
}