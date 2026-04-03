package app.simple.felicity.preferences

import android.content.Context
import app.simple.felicity.manager.SharedPreferences

object MainPreferences {

    const val APP_LABEL = "app_label"

    private const val DATA_LOADED = "data_loaded"
    private const val FLOATING_MENU_HEIGHT = "bottom_menu_height"

    //----------------------------------------------------------------------------------------------//

    fun setDataLoaded(dataLoaded: Boolean) {
        SharedPreferences.getSharedPreferences().edit().putBoolean(DATA_LOADED, dataLoaded).apply()
    }

    fun isDataLoaded(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(DATA_LOADED, false)
    }

    //----------------------------------------------------------------------------------------------//

    fun setFloatingMenuHeight(height: Int) {
        SharedPreferences.getSharedPreferences().edit().putInt(FLOATING_MENU_HEIGHT, height).apply()
    }

    fun getFloatingMenuHeight(): Int {
        return SharedPreferences.getSharedPreferences().getInt(FLOATING_MENU_HEIGHT, 0)
    }

    // ---------------------------------------------------------------------------------------------//

    fun setAppLabel(label: String) {
        SharedPreferences.getSharedPreferences().edit().putString(APP_LABEL, label).apply()
    }

    fun getAppLabel(context: Context): String {
        return SharedPreferences.getSharedPreferences()
            .getString(APP_LABEL, context.getString(R.string.app_name)) ?: ""
    }
}
