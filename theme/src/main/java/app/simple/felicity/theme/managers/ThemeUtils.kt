package app.simple.felicity.theme.managers

import android.content.res.Configuration
import android.content.res.Resources
import android.view.Window
import androidx.core.view.WindowInsetsControllerCompat
import app.simple.felicity.preferences.AppearancePreferences
import java.util.Calendar

object ThemeUtils {

    private fun lightBars(window: Window) {
        setStatusAndNavColors(window)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars =
            !AppearancePreferences.isAccentOnNavigationBar()
    }

    private fun darkBars(window: Window) {
        setStatusAndNavColors(window)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars =
            false
    }

    private fun setStatusAndNavColors(window: Window) {
        //        if (DevelopmentPreferences.get(DevelopmentPreferences.transparentStatus)) {
        //            window.statusBarColor = ThemeManager.theme.viewGroupTheme.background
        //        } else {
        //            window.statusBarColor = Color.TRANSPARENT
        //        }

        if (!AppearancePreferences.isAccentOnNavigationBar()) {
            window.navigationBarColor = ThemeManager.theme.viewGroupTheme.backgroundColor
        }
    }

    fun isNightMode(resources: Resources): Boolean {
        when (AppearancePreferences.getTheme()) {
            app.simple.felicity.core.constants.ThemeConstants.LIGHT_THEME,
            app.simple.felicity.core.constants.ThemeConstants.SOAPSTONE,
            app.simple.felicity.core.constants.ThemeConstants.MATERIAL_YOU_LIGHT,
            app.simple.felicity.core.constants.ThemeConstants.HIGH_CONTRAST_LIGHT -> {
                return false
            }

            app.simple.felicity.core.constants.ThemeConstants.DARK_THEME,
            app.simple.felicity.core.constants.ThemeConstants.AMOLED,
            app.simple.felicity.core.constants.ThemeConstants.HIGH_CONTRAST,
            app.simple.felicity.core.constants.ThemeConstants.SLATE,
            app.simple.felicity.core.constants.ThemeConstants.OIL,
            app.simple.felicity.core.constants.ThemeConstants.MATERIAL_YOU_DARK -> {
                return true
            }

            app.simple.felicity.core.constants.ThemeConstants.FOLLOW_SYSTEM -> {
                when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                    Configuration.UI_MODE_NIGHT_YES -> {
                        return true
                    }

                    Configuration.UI_MODE_NIGHT_NO -> {
                        return false
                    }

                    Configuration.UI_MODE_NIGHT_UNDEFINED -> {
                        return false
                    }
                }
            }

            app.simple.felicity.core.constants.ThemeConstants.DAY_NIGHT -> {
                val calendar = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                return calendar < 7 || calendar > 18
            }
        }

        return false
    }

    fun isFollowSystem(): Boolean {
        return AppearancePreferences.getTheme() == app.simple.felicity.core.constants.ThemeConstants.FOLLOW_SYSTEM
    }

    fun updateNavAndStatusColors(resources: Resources, window: Window) {
        if (isNightMode(resources)) {
            darkBars(window)
        } else {
            lightBars(window)
        }
    }

    fun makeBarIconsWhite(window: Window) {
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = false
    }

    fun makeBarIconsBlack(window: Window) {
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = true
    }
}
