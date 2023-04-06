package app.simple.felicity.preferences

object ConfigurationPreferences {

    private const val keepScreenOn = "keep_screen_on"
    const val language = "language_of_app"

    fun setKeepScreenOn(value: Boolean) {
        SharedPreferences.getSharedPreferences().edit().putBoolean(keepScreenOn, value).apply()
    }

    fun isKeepScreenOn(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(keepScreenOn, false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setAppLanguage(value: String) {
        SharedPreferences.getSharedPreferences().edit().putString(language, value).apply()
    }

    fun getAppLanguage(): String {
        return SharedPreferences.getSharedPreferences().getString(language, "default")!!
    }

    // ---------------------------------------------------------------------------------------------------------- //
}