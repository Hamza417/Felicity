package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences
import app.simple.felicity.manager.SharedPreferences.getSharedPreferences
import app.simple.felicity.preferences.UserInterfacePreferences.PLAYER_INTERFACE_DEFAULT
import app.simple.felicity.preferences.UserInterfacePreferences.PLAYER_INTERFACE_FADED

/**
 * Persisted preferences that control which interface variants are shown throughout the
 * application, including the home panel, the mini player margin, and the full-screen
 * player interface.
 *
 * @author Hamza417
 */
object UserInterfacePreferences {

    const val MARGIN_AROUND_MINIPLAYER = "margin_around_miniplayer"
    const val HOME_INTERFACE = "home_interface_"
    const val PLAYER_INTERFACE = "player_interface_"

    const val HOME_INTERFACE_DASHBOARD = 1
    const val HOME_INTERFACE_SPANNED = 2
    const val HOME_INTERFACE_ARTFLOW = 3
    const val HOME_INTERFACE_SIMPLE = 0

    const val PLAYER_INTERFACE_DEFAULT = 0
    const val PLAYER_INTERFACE_FADED = 1

    fun setMarginAroundMiniplayer(value: Boolean) {
        getSharedPreferences().edit { putBoolean(MARGIN_AROUND_MINIPLAYER, value) }
    }

    fun isMarginAroundMiniplayer(): Boolean {
        return getSharedPreferences().getBoolean(MARGIN_AROUND_MINIPLAYER, true)
    }

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

    /**
     * Returns the currently selected full-screen player interface identifier.
     * Defaults to [PLAYER_INTERFACE_DEFAULT] if the preference has not been set yet.
     */
    fun getPlayerInterface(): Int {
        return SharedPreferences.getSharedPreferences()
            .getInt(PLAYER_INTERFACE, PLAYER_INTERFACE_DEFAULT)
    }

    /**
     * Persists the selected full-screen player interface identifier.
     *
     * @param value one of [PLAYER_INTERFACE_DEFAULT] or [PLAYER_INTERFACE_FADED]
     */
    fun setPlayerInterface(value: Int) {
        SharedPreferences.getSharedPreferences()
            .edit {
                putInt(PLAYER_INTERFACE, value)
            }
    }
}