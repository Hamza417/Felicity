package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object GenresPreferences {

    const val GRID_SIZE = "genres_grid_size"
    const val SHOW_GENRE_COVERS = "show_genre_covers"
    const val GENRE_SORT_STYLE = "genre_sort"
    const val SORT_ORDER = "genre_sorting_style"

    // -------------------------------------------------------------------------------------------- //

    const val GRID_SIZE_ONE = 1
    const val GRID_SIZE_TWO = 2
    const val GRID_SIZE_THREE = 3
    const val GRID_SIZE_FOUR = 4
    const val GRID_SIZE_FIVE = 5
    const val GRID_SIZE_SIX = 6

    const val BY_NAME = 0

    const val ACCENDING = 0
    const val DESCENDING = 1

    // -------------------------------------------------------------------------------------------- //

    fun getGridSize(): Int {
        return SharedPreferences.getSharedPreferences().getInt(GRID_SIZE, GRID_SIZE_TWO)
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
        return SharedPreferences.getSharedPreferences().getInt(SORT_ORDER, ACCENDING)
    }

    fun setSortOrder(order: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(SORT_ORDER, order) }
    }
}