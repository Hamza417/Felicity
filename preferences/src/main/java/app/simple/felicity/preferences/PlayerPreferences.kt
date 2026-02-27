package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object PlayerPreferences {
    const val REPEAT_MODE = "repeat_mode"

    /**
     * Stereo pan/balance stored as a float in [-1 .. 1].
     * -1 = full left, 0 = centre (default), +1 = full right.
     * Applied via constant-power panning in the audio engine.
     */
    const val BALANCE = "player_balance"

    fun setRepeatMode(value: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(REPEAT_MODE, value) }
    }

    fun getRepeatMode(): Int {
        return SharedPreferences.getSharedPreferences().getInt(REPEAT_MODE, 0)
    }

    /**
     * Persist [pan] in [-1f .. 1f].
     * -1 = full left, 0 = centre, +1 = full right.
     */
    fun setBalance(pan: Float) {
        SharedPreferences.getSharedPreferences().edit {
            putFloat(BALANCE, pan.coerceIn(-1f, 1f))
        }
    }

    /** Returns the persisted pan value, defaulting to 0 (centre). */
    fun getBalance(): Float {
        return SharedPreferences.getSharedPreferences().getFloat(BALANCE, 0f)
    }
}