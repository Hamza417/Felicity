package app.simple.felicity.preferences

import androidx.core.content.edit

object HomePreferences {

    const val HOME_INTERFACE = "home_interface"
    const val HOME_INTERFACE_SPANNED = "spanned"
    const val HOME_INTERFACE_CAROUSEL = "carousel"
    const val HOME_INTERFACE_ARTFLOW = "artflow"
    const val HOME_INTERFACE_SIMPLE = "simple"

    // ---------------------------------------------------------------------------------------- //

    fun getHomeInterface(): String {
        return SharedPreferences.getSharedPreferences()
            .getString(HOME_INTERFACE, HOME_INTERFACE_SPANNED) ?: HOME_INTERFACE_SPANNED
    }

    fun setHomeInterface(value: String) {
        SharedPreferences.getSharedPreferences()
            .edit {
                putString(HOME_INTERFACE, value)
            }
    }
}