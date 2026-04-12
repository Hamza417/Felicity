package app.simple.felicity.preferences

import androidx.core.content.edit
import app.simple.felicity.manager.SharedPreferences

object ConfigurationPreferences {

    private const val KEEP_SCREEN_ON = "keep_screen_on"

    const val LANGUAGE = "language_of_app"

    fun setKeepScreenOn(value: Boolean) {
        SharedPreferences.getSharedPreferences().edit { putBoolean(KEEP_SCREEN_ON, value) }
    }

    fun isKeepScreenOn(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(KEEP_SCREEN_ON, false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setAppLanguage(value: String) {
        SharedPreferences.getSharedPreferences().edit().putString(LANGUAGE, value).apply()
    }

    fun getAppLanguage(): String {
        return SharedPreferences.getSharedPreferences().getString(LANGUAGE, "default")!!
    }
}
