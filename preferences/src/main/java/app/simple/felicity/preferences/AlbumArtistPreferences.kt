package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.constants.CommonPreferencesConstants.toLayoutMode
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.manager.SharedPreferences

/**
 * Holds all the settings that control how the Album Artists panel
 * looks and behaves — sort order, layout style, and so on.
 *
 * Each key is unique so it never accidentally stomps on the regular
 * artist preferences (learned that the hard way — never again!).
 *
 * @author Hamza417
 */
object AlbumArtistPreferences {

    /** The key for storing which field we sort album artists by (name, albums, songs). */
    const val ALBUM_ARTIST_SORT = "album_artist_sort"

    /** The key for storing ascending vs descending order for the sort. */
    const val SORTING_STYLE = "album_artist_sorting_style"

    /** Grid size key for portrait orientation. */
    const val GRID_SIZE_PORTRAIT = "album_artist_grid_size_portrait1"

    /** Grid size key for landscape orientation. */
    const val GRID_SIZE_LANDSCAPE = "album_artist_grid_size_landscape1"

    /**
     * Returns the current sort field for the Album Artists panel.
     * Defaults to sorting by name if nothing has been set yet.
     */
    fun getAlbumArtistSort(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(ALBUM_ARTIST_SORT, CommonPreferencesConstants.BY_NAME)
    }

    /**
     * Saves the new sort field for the Album Artists panel.
     *
     * @param value one of the [CommonPreferencesConstants] sort constants
     */
    fun setAlbumArtistSort(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(ALBUM_ARTIST_SORT, value)
        }
    }

    /**
     * Returns the current sort direction — ascending or descending.
     * Defaults to ascending because that's what humans expect.
     */
    fun getSortingStyle(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(SORTING_STYLE, CommonPreferencesConstants.ASCENDING)
    }

    /**
     * Saves the sort direction for the Album Artists panel.
     *
     * @param value one of the [CommonPreferencesConstants] order constants
     */
    fun setSortingStyle(value: Int) {
        SharedPreferences.getSharedPreferences().edit {
            putInt(SORTING_STYLE, value)
        }
    }

    /**
     * Returns the layout mode (list, grid, labels) for the current screen orientation.
     * Portrait defaults to a single-column list; landscape defaults to a two-column grid.
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
     * Persists the layout mode for the current screen orientation.
     *
     * @param mode the [CommonPreferencesConstants.LayoutMode] the user just selected
     */
    fun setGridSize(mode: CommonPreferencesConstants.LayoutMode) {
        if (AppOrientation.isLandscape().not()) {
            SharedPreferences.getSharedPreferences().edit { putString(GRID_SIZE_PORTRAIT, mode.name) }
        } else {
            SharedPreferences.getSharedPreferences().edit { putString(GRID_SIZE_LANDSCAPE, mode.name) }
        }
    }
}

