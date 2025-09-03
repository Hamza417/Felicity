package app.simple.felicity.preferences

import android.annotation.SuppressLint
import android.os.Build
import androidx.core.content.edit
import app.simple.felicity.core.constants.ThemeConstants
import app.simple.felicity.manager.SharedPreferences.getSharedPreferences

object AppearancePreferences {

    const val APP_CORNER_RADIUS = "view_corner_radius"
    const val LIST_SPACING = "list_spacing"
    private const val LAST_LIGHT_THEME = "last_light_theme"
    private const val LAST_DARK_THEME = "last_dark_theme"
    private const val SHADOW_EFFECT = "shadow_effect"

    const val THEME = "current_app_theme"
    const val ACCENT_COLOR = "app_accent_color"
    const val APP_FONT = "type_face"

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
    @SuppressLint("UseKtx")
    fun setTheme(value: Int): Boolean {
        return getSharedPreferences().edit().putInt(THEME, value).commit()
    }

    fun getTheme(): Int {
        return getSharedPreferences().getInt(THEME, ThemeConstants.FOLLOW_SYSTEM)
    }

    fun migrateMaterialYouTheme() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            if (getTheme() == ThemeConstants.MATERIAL_YOU_LIGHT || getTheme() == ThemeConstants.MATERIAL_YOU_DARK) {
                setLastDarkTheme(ThemeConstants.MATERIAL_YOU_DARK)
                setLastLightTheme(ThemeConstants.MATERIAL_YOU_LIGHT)
                setTheme(ThemeConstants.FOLLOW_SYSTEM)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setLastDarkTheme(value: Int) {
        getSharedPreferences().edit { putInt(LAST_DARK_THEME, value) }
    }

    fun getLastDarkTheme(): Int {
        return getSharedPreferences().getInt(LAST_DARK_THEME, ThemeConstants.DARK_THEME)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setLastLightTheme(value: Int) {
        getSharedPreferences().edit { putInt(LAST_LIGHT_THEME, value) }
    }

    fun getLastLightTheme(): Int {
        return getSharedPreferences().getInt(LAST_LIGHT_THEME, ThemeConstants.LIGHT_THEME)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    @SuppressLint("UseKtx")
    fun setAppFont(font: String): Boolean {
        return getSharedPreferences().edit().putString(APP_FONT, font).commit()
    }

    fun getAppFont(): String {
        return getSharedPreferences().getString(APP_FONT, "notosans")!!
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setCornerRadius(radius: Float) {
        getSharedPreferences().edit {
            putFloat(APP_CORNER_RADIUS, if (radius < 1F) 1F else radius)
        }
    }

    fun getCornerRadius(): Float {
        return getSharedPreferences().getFloat(APP_CORNER_RADIUS, 20F)
    }

    // ---------------------------------------------------------------------------------------------------------- //

    fun setShadowEffect(boolean: Boolean) {
        getSharedPreferences().edit { putBoolean(SHADOW_EFFECT, boolean) }
    }

    fun isShadowEffectOn(): Boolean {
        return getSharedPreferences().getBoolean(SHADOW_EFFECT, true)
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
