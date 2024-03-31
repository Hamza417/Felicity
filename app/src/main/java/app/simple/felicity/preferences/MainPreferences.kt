package app.simple.felicity.preferences

object MainPreferences {
    private const val DATA_LOADED = "data_loaded"

    //----------------------------------------------------------------------------------------------//

    fun setDataLoaded(dataLoaded: Boolean) {
        SharedPreferences.getSharedPreferences().edit().putBoolean(DATA_LOADED, dataLoaded).apply()
    }

    fun isDataLoaded(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(DATA_LOADED, false)
    }
}
