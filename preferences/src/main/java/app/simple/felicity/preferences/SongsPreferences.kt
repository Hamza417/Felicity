package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.manager.SharedPreferences

object SongsPreferences {

    const val SONGS_INTERFACE = "songs_interface"
    const val SONG_SORT = "song_sort_"
    const val SORTING_STYLE = "song_sorting_style_"
    const val GRID_SIZE_PORTRAIT = "songs_grid_size_portrait"
    const val GRID_SIZE_LANDSCAPE = "songs_grid_size_landscape"
    const val GRID_TYPE = "songs_grid_type"

    // ----------------------------------------------------------------------------------------- //

    const val SONG_INTERFACE_FELICITY = "felicity"
    const val SONG_INTERFACE_FLOW = "flow"

    // ----------------------------------------------------------------------------------------- //

    fun getSongsInterface(): String {
        return SharedPreferences.getSharedPreferences()
            .getString(SONGS_INTERFACE, SONG_INTERFACE_FELICITY) ?: SONG_INTERFACE_FELICITY
    }

    fun setSongsInterface(value: String) {
        SharedPreferences.getSharedPreferences().edit {
            putString(SONGS_INTERFACE, value)
        }
    }

    // ----------------------------------------------------------------------------------------- //

    fun getSongSort(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(SONG_SORT, CommonPreferencesConstants.BY_TITLE)
    }

    fun setSongSort(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(SONG_SORT, value)
        }
    }

    // ----------------------------------------------------------------------------------------- //

    fun getSortingStyle(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(SORTING_STYLE, CommonPreferencesConstants.ASCENDING)
    }

    fun setSortingStyle(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(SORTING_STYLE, value)
        }
    }

    // ----------------------------------------------------------------------------------------- //

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

    // ----------------------------------------------------------------------------------------- //

    fun getGridType(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(GRID_TYPE, CommonPreferencesConstants.GRID_TYPE_LIST)
    }

    fun setGridType(type: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(GRID_TYPE, type) }
    }
}