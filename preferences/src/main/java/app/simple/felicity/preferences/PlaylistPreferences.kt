package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.constants.CommonPreferencesConstants.toLayoutMode
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.manager.SharedPreferences

/**
 * Shared-preference keys and accessors for the Playlist panel.
 *
 * <p>Uses a {@code playlist_} key namespace so that playlist sort and grid settings
 * remain fully independent of the Songs, Favorites, and all other panel settings.
 * Sort preferences here govern the <em>global</em> playlist-list sort (i.e. how
 * playlists are ordered in the list view). Each individual playlist also stores its
 * own in-playlist sort in the {@link app.simple.felicity.repository.models.Playlist}
 * row itself.</p>
 *
 * @author Hamza417
 */
object PlaylistPreferences {

    const val SONG_SORT = "playlist_sort"
    const val SORTING_STYLE = "playlist_sorting_style"
    const val GRID_SIZE_PORTRAIT = "playlist_grid_size_portrait"
    const val GRID_SIZE_LANDSCAPE = "playlist_grid_size_landscape"

    /**
     * Returns the current sort field for songs inside a playlist
     * (defaults to [CommonPreferencesConstants.BY_TITLE]).
     */
    fun getSongSort(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(SONG_SORT, CommonPreferencesConstants.BY_TITLE)
    }

    /**
     * Persists the sort field for songs inside a playlist.
     *
     * @param value One of the {@code BY_*} constants from [CommonPreferencesConstants].
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
     * @param value [CommonPreferencesConstants.ASCENDING] or [CommonPreferencesConstants.DESCENDING].
     */
    fun setSortingStyle(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(SORTING_STYLE, value)
        }
    }

    /**
     * Returns the current [CommonPreferencesConstants.LayoutMode] for the active orientation.
     */
    fun getGridSize(): CommonPreferencesConstants.LayoutMode {
        return if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences()
                .getString(GRID_SIZE_PORTRAIT, CommonPreferencesConstants.LayoutMode.LIST_ONE.name)!!
                .toLayoutMode()
        } else {
            SharedPreferences.getSharedPreferences()
                .getString(GRID_SIZE_LANDSCAPE, CommonPreferencesConstants.LayoutMode.GRID_TWO.name)!!
                .toLayoutMode()
        }
    }

    /**
     * Persists the [CommonPreferencesConstants.LayoutMode] for the current orientation.
     *
     * @param mode The layout mode to save.
     */
    fun setGridSize(mode: CommonPreferencesConstants.LayoutMode) {
        if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences().edit { putString(GRID_SIZE_PORTRAIT, mode.name) }
        } else {
            SharedPreferences.getSharedPreferences().edit { putString(GRID_SIZE_LANDSCAPE, mode.name) }
        }
    }
}

