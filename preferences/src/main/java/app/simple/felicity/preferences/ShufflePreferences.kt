package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object ShufflePreferences {

    const val SHUFFLE = "songs_shuffle"

    // --------------------------------------------------------------------------------------------- //

    fun isShuffleEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(SHUFFLE, false)
    }

    fun setShuffleEnabled(value: Boolean) {
        SharedPreferences.getSharedPreferences().edit {
            putBoolean(SHUFFLE, value)
        }
    }

    fun toggleShuffle() {
        setShuffleEnabled(!isShuffleEnabled())
    }
}
