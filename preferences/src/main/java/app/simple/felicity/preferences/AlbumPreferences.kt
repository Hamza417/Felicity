package app.simple.felicity.preferences

import androidx.core.content.edit

object AlbumPreferences {
    const val ALBUM_INTERFACE = "album_interface"
    const val ALBUM_SORT = "album_sort"
    const val SORTING_STYLE = "album_sorting_style"

    // ----------------------------------------------------------------------------------------- //

    const val ALBUM_INTERFACE_DEFAULT = "default"
    const val ALBUM_INTERFACE_FLOW = "flow"

    // ----------------------------------------------------------------------------------------- //

    const val BY_ALBUM_NAME = "album"
    const val BY_ARTIST = "artist"
    const val BY_NUMBER_OF_SONGS = "number_of_songs"
    const val BY_YEAR = "year"
    const val BY_FIRST_YEAR = "first_year"
    const val BY_LAST_YEAR = "last_year"

    const val ACCENDING = "ascending"
    const val DESCENDING = "descending"

    // ----------------------------------------------------------------------------------------- //

    fun getAlbumInterface(): String {
        return app.simple.felicity.manager.SharedPreferences.getSharedPreferences()
            .getString(ALBUM_INTERFACE, ALBUM_INTERFACE_DEFAULT) ?: ALBUM_INTERFACE_DEFAULT
    }

    fun setAlbumInterface(value: String) {
        app.simple.felicity.manager.SharedPreferences.getSharedPreferences().edit {
            putString(ALBUM_INTERFACE, value)
        }
    }

    fun getAlbumSort(): String {
        return app.simple.felicity.manager.SharedPreferences.getSharedPreferences()
            .getString(ALBUM_SORT, BY_ALBUM_NAME) ?: BY_ALBUM_NAME
    }

    fun setAlbumSort(value: String) {
        app.simple.felicity.manager.SharedPreferences.getSharedPreferences().edit {
            putString(ALBUM_SORT, value)
        }
    }

    fun getSortingStyle(): String {
        return app.simple.felicity.manager.SharedPreferences.getSharedPreferences()
            .getString(SORTING_STYLE, ACCENDING) ?: ACCENDING
    }

    fun setSortingStyle(value: String) {
        app.simple.felicity.manager.SharedPreferences.getSharedPreferences().edit {
            putString(SORTING_STYLE, value)
        }
    }
}