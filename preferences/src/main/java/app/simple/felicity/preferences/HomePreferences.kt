package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object HomePreferences {

    const val HOME_INTERFACE = "home_interface_"
    const val HOME_INTERFACE_SPANNED = 2
    const val HOME_INTERFACE_CAROUSEL = 1
    const val HOME_INTERFACE_ARTFLOW = 3
    const val HOME_INTERFACE_SIMPLE = 0

    // ---------------------------------------------------------------------------------------- //

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