package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences
import app.simple.felicity.manager.SharedPreferences.getSharedPreferences

object UserInterfacePreferences {

    const val MARGIN_AROUND_MINIPLAYER = "margin_around_miniplayer"
    const val HOME_INTERFACE = "home_interface_"

    const val HOME_INTERFACE_DASHBOARD = 1
    const val HOME_INTERFACE_SPANNED = 2
    const val HOME_INTERFACE_ARTFLOW = 3
    const val HOME_INTERFACE_SIMPLE = 0

    // ---------------------------------------------------------------------------------------------------------- //

    fun setMarginAroundMiniplayer(value: Boolean) {
        getSharedPreferences().edit { putBoolean(MARGIN_AROUND_MINIPLAYER, value) }
    }

    fun isMarginAroundMiniplayer(): Boolean {
        return getSharedPreferences().getBoolean(MARGIN_AROUND_MINIPLAYER, true)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun getHomeInterface(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(HOME_INTERFACE, HOME_INTERFACE_SIMPLE)
    }

    fun setHomeInterface(value: Int) {
        SharedPreferences.getSharedPreferences()
            .edit {
                putInt(HOME_INTERFACE, value)
            }
    }
}