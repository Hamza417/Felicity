package app.simple.felicity.preferences

import androidx.core.content.edit

object SongsPreferences {

    const val SONGS_INTERFACE = "songs_interface"
    const val SONG_SORT = "song_sort"
    const val SORTING_STYLE = "song_sorting_style"

    // ----------------------------------------------------------------------------------------- //

    const val SONG_INTERFACE_FELICITY = "felicity"
    const val SONG_INTERFACE_FLOW = "flow"

    // ----------------------------------------------------------------------------------------- //

    const val BY_TITLE = "title"
    const val BY_ARTIST = "artist"
    const val BY_ALBUM = "album"
    const val PATH = "path"
    const val BY_DATE_ADDED = "date_added"
    const val BY_DATE_MODIFIED = "date_modified"
    const val BY_DURATION = "duration"
    const val BY_YEAR = "year"
    const val BY_TRACK_NUMBER = "track_number"
    const val BY_COMPOSER = "composer"

    // TODOs
    const val BY_DISC_NUMBER = "disc_number"
    const val BY_PLAY_COUNT = "play_count"
    const val BY_RATING = "rating"
    const val BY_FAVORITE = "favorite"

    const val ACCENDING = "ascending"
    const val DESCENDING = "descending"

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

    fun getSongSort(): String {
        return SharedPreferences.getSharedPreferences()
            .getString(SONG_SORT, BY_TITLE) ?: BY_TITLE
    }

    fun setSongSort(value: String) {
        SharedPreferences.getSharedPreferences().edit {
            putString(SONG_SORT, value)
        }
    }

    // ----------------------------------------------------------------------------------------- //

    fun getSortingStyle(): String {
        return SharedPreferences.getSharedPreferences()
            .getString(SORTING_STYLE, ACCENDING) ?: ACCENDING
    }

    fun setSortingStyle(value: String) {
        SharedPreferences.getSharedPreferences().edit {
            putString(SORTING_STYLE, value)
        }
    }
}