package app.simple.felicity.preferences

object MainPreferences {

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
}
