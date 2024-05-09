package app.simple.felicity.preferences

object LayoutsPreferences {

    private const val CENTER_FLOATING_MENU = "center_floating_menu"

    //----------------------------------------------------------------------------------------------//

    fun setCenterFloatingMenu(center: Boolean) {
        SharedPreferences.getSharedPreferences().edit().putBoolean(CENTER_FLOATING_MENU, center).apply()
    }

    fun isCenterFloatingMenu(): Boolean {
        return SharedPreferences.getSharedPreferences().getBoolean(CENTER_FLOATING_MENU, false)
    }
}
