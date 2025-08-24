package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object PlayerPreferences {

    private const val LAST_SONG_ID = "last_song_id"
    private const val LAST_SONG_POSITION = "last_song_position"
    private const val LAST_SONG_SEEK = "last_song_seek"

    // ------------------------------------------------------------------------------ //

    fun getLastSongId(): Long {
        return SharedPreferences.getSharedPreferences()
            .getLong(LAST_SONG_ID, -1L)
    }

    fun setLastSongId(songId: Long) {
        SharedPreferences.getSharedPreferences()
            .edit {
                putLong(LAST_SONG_ID, songId)
            }
    }

    // ------------------------------------------------------------------------------ //

    fun getLastSongPosition(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(LAST_SONG_POSITION, 0)
    }

    fun setLastSongPosition(position: Int) {
        SharedPreferences.getSharedPreferences()
            .edit {
                putInt(LAST_SONG_POSITION, position)
            }
    }

    // ------------------------------------------------------------------------------ //

    fun getLastSongSeek(): Long {
        return SharedPreferences.getSharedPreferences()
            .getLong(LAST_SONG_SEEK, 0L)
    }

    fun setLastSongSeek(seek: Long) {
        SharedPreferences.getSharedPreferences()
            .edit {
                putLong(LAST_SONG_SEEK, seek)
            }
    }
}