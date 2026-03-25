package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object ListPreferences {

    const val ALBUM_ARTIST_OVER_ARTIST = "album_artist_over_artist"

    // ------------------------------------------------------------------------------------------------------ //

    fun isAlbumArtistOverArtist(): Boolean {
        return SharedPreferences.getSharedPreferences()
            .getBoolean(ALBUM_ARTIST_OVER_ARTIST, false)
    }

    fun setAlbumArtistOverArtist(enabled: Boolean) {
        SharedPreferences.getSharedPreferences().edit {
            putBoolean(ALBUM_ARTIST_OVER_ARTIST, enabled)
        }
    }
}