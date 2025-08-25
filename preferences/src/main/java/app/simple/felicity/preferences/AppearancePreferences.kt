package app.simple.felicity.preferences

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import app.simple.felicity.core.constants.ThemeConstants
import app.simple.felicity.manager.SharedPreferences.getSharedPreferences

object AppearancePreferences {

    const val APP_CORNER_RADIUS = "view_corner_radius"
    const val LIST_SPACING = "list_spacing"
    private const val ICON_SHADOWS = "icon_shadows"
    private const val LAST_LIGHT_THEME = "last_light_theme"
    private const val LAST_DARK_THEME = "last_dark_theme"
    private const val COLORED_ICON_SHADOWS = "icon_shadows_colored"
    private const val IS_MATERIAL_YOU_ACCENT = "is_material_you_accent"
    private const val ACCENT_COLOR_ON_BOTTOM_MENU = "accent_color_on_bottom_menu"

    const val IS_CUSTOM_COLOR = "is_custom_color"
    const val THEME = "current_app_theme"
    const val ACCENT_COLOR = "app_accent_color"
    private const val ACCENT_COLOR_LIGHT = "app_accent_color_light"
    const val APP_FONT = "type_face"
    const val ACCENT_ON_NAV = "accent_color_on_nav_bar"

    const val MAX_CORNER_RADIUS = 80F
    const val MAX_SPACING = 80F

    const val DEFAULT_CORNER_RADIUS = 20F
    const val DEFAULT_SPACING = 48F

    // ---------------------------------------------------------------------------------------------------------- //

    fun setAccentColorName(name: String) {
        getSharedPreferences().edit {
            putString(ACCENT_COLOR, name)
        }
    }

    fun getAccentColorName(): String? {
        return getSharedPreferences().getString(ACCENT_COLOR, null)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    /**
     * @param value for storing theme preferences
     * 0 - Light
     * 1 - Dark
     * 2 - AMOLED
     * 3 - System
     * 4 - Day/Night
     */
    fun setTheme(value: Int): Boolean {
        return getSharedPreferences().edit().putInt(THEME, value).commit()
    }

    fun getTheme(): Int {
        return getSharedPreferences().getInt(THEME, ThemeConstants.FOLLOW_SYSTEM)
    }

    fun migrateMaterialYouTheme() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            if (getTheme() == ThemeConstants.MATERIAL_YOU) {
                setLastDarkTheme(ThemeConstants.MATERIAL_YOU_DARK)
                setLastLightTheme(ThemeConstants.MATERIAL_YOU_LIGHT)
                setTheme(ThemeConstants.FOLLOW_SYSTEM)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setLastDarkTheme(value: Int) {
        getSharedPreferences().edit().putInt(LAST_DARK_THEME, value).apply()
    }

    fun getLastDarkTheme(): Int {
        return getSharedPreferences().getInt(LAST_DARK_THEME, ThemeConstants.DARK_THEME)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setLastLightTheme(value: Int) {
        getSharedPreferences().edit().putInt(LAST_LIGHT_THEME, value).apply()
    }

    fun getLastLightTheme(): Int {
        return getSharedPreferences().getInt(LAST_LIGHT_THEME, ThemeConstants.LIGHT_THEME)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setAppFont(font: String): Boolean {
        return getSharedPreferences().edit().putString(APP_FONT, font).commit()
    }

    fun getAppFont(): String {
        return getSharedPreferences().getString(APP_FONT, "notosans")!!
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setCornerRadius(radius: Float) {
        getSharedPreferences().edit().putFloat(APP_CORNER_RADIUS, if (radius < 1F) 1F else radius)
            .apply()
    }

    fun getCornerRadius(): Float {
        return getSharedPreferences().getFloat(APP_CORNER_RADIUS, 20F)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setIconShadows(boolean: Boolean) {
        getSharedPreferences().edit { putBoolean(ICON_SHADOWS, boolean) }
    }

    fun isIconShadowsOn(): Boolean {
        return getSharedPreferences().getBoolean(ICON_SHADOWS, true)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setAccentOnNavigationBar(boolean: Boolean) {
        getSharedPreferences().edit { putBoolean(ACCENT_ON_NAV, boolean) }
    }

    fun isAccentOnNavigationBar(): Boolean {
        return getSharedPreferences().getBoolean(ACCENT_ON_NAV, false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setColoredIconShadowsState(boolean: Boolean) {
        getSharedPreferences().edit { putBoolean(COLORED_ICON_SHADOWS, boolean) }
    }

    fun getColoredIconShadows(): Boolean {
        return getSharedPreferences().getBoolean(COLORED_ICON_SHADOWS, true)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    @RequiresApi(Build.VERSION_CODES.S)
    fun setMaterialYouAccent(boolean: Boolean) {
        getSharedPreferences().edit { putBoolean(IS_MATERIAL_YOU_ACCENT, boolean) }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun isMaterialYouAccent(): Boolean {
        return getSharedPreferences().getBoolean(IS_MATERIAL_YOU_ACCENT, false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setAccentColorOnBottomMenu(boolean: Boolean) {
        getSharedPreferences().edit { putBoolean(ACCENT_COLOR_ON_BOTTOM_MENU, boolean) }
    }

    fun isAccentColorOnBottomMenu(): Boolean {
        return getSharedPreferences().getBoolean(ACCENT_COLOR_ON_BOTTOM_MENU, false)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setListSpacing(spacing: Float) {
        getSharedPreferences().edit {
            putFloat(LIST_SPACING, spacing)
        }
    }

    fun getListSpacing(): Float {
        return getSharedPreferences().getFloat(LIST_SPACING, DEFAULT_SPACING)
    }
}
