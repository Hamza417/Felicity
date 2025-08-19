package app.simple.felicity.preferences

import androidx.core.content.edit

object SongsPreferences {

    const val SONGS_INTERFACE = "songs_interface"

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
}