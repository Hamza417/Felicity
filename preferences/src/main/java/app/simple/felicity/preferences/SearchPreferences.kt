package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.constants.CommonPreferencesConstants.toLayoutMode
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.manager.SharedPreferences

object SearchPreferences {

    const val SONG_SORT = "search_sort_"
    const val SORTING_STYLE = "search_sorting_style_"
    const val GRID_SIZE_PORTRAIT = "search_grid_size_portrait1"
    const val GRID_SIZE_LANDSCAPE = "search_grid_size_landscape1"

    const val FILTER_SONGS = "search_filter_songs"
    const val FILTER_ALBUMS = "search_filter_albums"
    const val FILTER_ARTISTS = "search_filter_artists"
    const val FILTER_GENRES = "search_filter_genres"

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

    fun getGridSize(): CommonPreferencesConstants.LayoutMode {
        return if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences()
                .getString(GRID_SIZE_PORTRAIT, CommonPreferencesConstants.LayoutMode.LIST_ONE.name)!!.toLayoutMode()
        } else {
            SharedPreferences.getSharedPreferences()
                .getString(GRID_SIZE_LANDSCAPE, CommonPreferencesConstants.LayoutMode.GRID_TWO.name)!!.toLayoutMode()
        }
    }

    fun setGridSize(mode: CommonPreferencesConstants.LayoutMode) {
        if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences().edit { putString(GRID_SIZE_PORTRAIT, mode.name) }
        } else {
            SharedPreferences.getSharedPreferences().edit { putString(GRID_SIZE_LANDSCAPE, mode.name) }
        }
    }

    // ----------------------------------------------------------------------------------------- //

    fun isSongsEnabled(): Boolean =
        SharedPreferences.getSharedPreferences().getBoolean(FILTER_SONGS, true)

    fun setSongsEnabled(value: Boolean) =
        SharedPreferences.getSharedPreferences().edit { putBoolean(FILTER_SONGS, value) }

    fun isAlbumsEnabled(): Boolean =
        SharedPreferences.getSharedPreferences().getBoolean(FILTER_ALBUMS, true)

    fun setAlbumsEnabled(value: Boolean) =
        SharedPreferences.getSharedPreferences().edit { putBoolean(FILTER_ALBUMS, value) }

    fun isArtistsEnabled(): Boolean =
        SharedPreferences.getSharedPreferences().getBoolean(FILTER_ARTISTS, true)

    fun setArtistsEnabled(value: Boolean) =
        SharedPreferences.getSharedPreferences().edit { putBoolean(FILTER_ARTISTS, value) }

    fun isGenresEnabled(): Boolean =
        SharedPreferences.getSharedPreferences().getBoolean(FILTER_GENRES, true)

    fun setGenresEnabled(value: Boolean) =
        SharedPreferences.getSharedPreferences().edit { putBoolean(FILTER_GENRES, value) }
}