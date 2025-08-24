package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object GenresPreferences {

    const val GRID_SIZE = "genres_grid_size"
    const val GRID_SPACING = "genres_grid_spacing"
    const val SHOW_GENRE_COVERS = "show_genre_covers"

    // -------------------------------------------------------------------------------------------- //

    const val GRID_SIZE_ONE = 1
    const val GRID_SIZE_TWO = 2
    const val GRID_SIZE_THREE = 3
    const val GRID_SIZE_FOUR = 4
    const val GRID_SIZE_FIVE = 5
    const val GRID_SIZE_SIX = 6

    // -------------------------------------------------------------------------------------------- //

    fun getGridSize(): Int {
        return SharedPreferences.getSharedPreferences().getInt(GRID_SIZE, GRID_SIZE_TWO)
    }

    fun setGridSize(size: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(GRID_SIZE, size) }
    }
}