package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.manager.SharedPreferences

object ArtistPreferences {
    const val ARTIST_SORT = "artist_sort"
    const val SORTING_STYLE = "artist_sorting_style"

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
}