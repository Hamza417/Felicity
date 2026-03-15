package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object PlayerPreferences {
    const val REPEAT_MODE = "repeat_mode"

    fun setRepeatMode(value: Int) {
        SharedPreferences.getSharedPreferences().edit { putInt(REPEAT_MODE, value) }
    }

    fun getRepeatMode(): Int {
        return SharedPreferences.getSharedPreferences().getInt(REPEAT_MODE, 0)
    }
}