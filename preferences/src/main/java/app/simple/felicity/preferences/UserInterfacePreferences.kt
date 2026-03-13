package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences.getSharedPreferences

object UserInterfacePreferences {

    const val MARGIN_AROUND_MINIPLAYER = "margin_around_miniplayer"

    // ---------------------------------------------------------------------------------------------------------- //

    fun setMarginAroundMiniplayer(value: Boolean) {
        getSharedPreferences().edit { putBoolean(MARGIN_AROUND_MINIPLAYER, value) }
    }

    fun isMarginAroundMiniplayer(): Boolean {
        return getSharedPreferences().getBoolean(MARGIN_AROUND_MINIPLAYER, true)
    }
}