package app.simple.felicity.theme.managers

import android.content.res.Configuration
import android.content.res.Resources
import android.view.Window
import androidx.core.view.WindowInsetsControllerCompat
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.theme.constants.ThemeConstants
import java.util.*

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
            ThemeConstants.LIGHT_THEME,
            ThemeConstants.SOAPSTONE,
            ThemeConstants.MATERIAL_YOU_LIGHT,
            ThemeConstants.HIGH_CONTRAST_LIGHT -> {
                return false
            }
            ThemeConstants.DARK_THEME,
            ThemeConstants.AMOLED,
            ThemeConstants.HIGH_CONTRAST,
            ThemeConstants.SLATE,
            ThemeConstants.OIL,
            ThemeConstants.MATERIAL_YOU_DARK -> {
                return true
            }
            ThemeConstants.FOLLOW_SYSTEM -> {
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
            ThemeConstants.DAY_NIGHT -> {
                val calendar = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                if (calendar < 7 || calendar > 18) {
                    return true
                } else if (calendar < 18 || calendar > 6) {
                    return false
                }
            }
        }

        return false
    }

    fun isFollowSystem(): Boolean {
        return AppearancePreferences.getTheme() == ThemeConstants.FOLLOW_SYSTEM
    }

    fun updateNavAndStatusColors(resources: Resources, window: Window) {
        if (isNightMode(resources)) {
            darkBars(window)
        } else {
            lightBars(window)
        }
    }
}