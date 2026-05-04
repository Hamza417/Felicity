package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object ShufflePreferences {

    const val SHUFFLE = "shuffle"
    const val NO_RESHUFFLE = "no_reshuffle"

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

    // --------------------------------------------------------------------------------------------- //

    fun isNoReshuffleEnabled(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(NO_RESHUFFLE, false)
    }

    fun setNoReshuffleEnabled(value: Boolean) {
        SharedPreferences.getSharedPreferences().edit {
            putBoolean(NO_RESHUFFLE, value)
        }
    }
}
