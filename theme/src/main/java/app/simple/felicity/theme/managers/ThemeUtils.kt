package app.simple.felicity.theme.managers

import android.content.res.Configuration
import android.content.res.Resources
import android.view.Window
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.simple.felicity.core.utils.CalendarUtils
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.theme.constants.ThemeConstants
import app.simple.felicity.theme.themes.dark.AMOLED
import app.simple.felicity.theme.themes.dark.DarkTheme
import app.simple.felicity.theme.themes.dark.HighContrastDark
import app.simple.felicity.theme.themes.dark.MaterialYouDark
import app.simple.felicity.theme.themes.dark.Oil
import app.simple.felicity.theme.themes.dark.Slate
import app.simple.felicity.theme.themes.light.HighContrastLight
import app.simple.felicity.theme.themes.light.LightTheme
import app.simple.felicity.theme.themes.light.MaterialYouLight
import app.simple.felicity.theme.themes.light.SoapStone
import java.util.Calendar

object ThemeUtils {

    private fun lightBars(window: Window) {
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = true
    }

    private fun darkBars(window: Window) {
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = false
    }

    fun setLightBars(lifecycleOwner: LifecycleOwner, window: Window, resources: Resources) {
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                makeBarIconsWhite(window)
            }

            override fun onPause(owner: LifecycleOwner) {
                super.onDestroy(owner)
                lifecycleOwner.lifecycle.removeObserver(this)
                setBarColors(resources, window)
            }
        })
    }

    fun setDarkBars(lifecycleOwner: LifecycleOwner, window: Window, resources: Resources) {
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                darkBars(window)
                WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
                WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = false
            }

            override fun onPause(owner: LifecycleOwner) {
                super.onDestroy(owner)
                lifecycleOwner.lifecycle.removeObserver(this)
                setBarColors(resources, window)
            }
        })
    }

    fun setAppTheme(resources: Resources) {
        when (AppearancePreferences.getTheme()) {
            ThemeConstants.LIGHT_THEME -> {
                ThemeManager.theme = LightTheme()
            }
            ThemeConstants.SOAPSTONE -> {
                ThemeManager.theme = SoapStone()
            }
            ThemeConstants.HIGH_CONTRAST_LIGHT -> {
                ThemeManager.theme = HighContrastLight()
            }
            ThemeConstants.DARK_THEME -> {
                ThemeManager.theme = DarkTheme()
            }
            ThemeConstants.AMOLED -> {
                ThemeManager.theme = AMOLED()
            }
            ThemeConstants.SLATE -> {
                ThemeManager.theme = Slate()
            }
            ThemeConstants.OIL -> {
                ThemeManager.theme = Oil()
            }
            ThemeConstants.HIGH_CONTRAST_DARK -> {
                ThemeManager.theme = HighContrastDark()
            }
            ThemeConstants.FOLLOW_SYSTEM -> {
                // AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                    Configuration.UI_MODE_NIGHT_YES -> {
                        when (AppearancePreferences.getLastDarkTheme()) {
                            ThemeConstants.DARK_THEME -> {
                                ThemeManager.theme = DarkTheme()
                            }
                            ThemeConstants.AMOLED -> {
                                ThemeManager.theme = AMOLED()
                            }
                            ThemeConstants.SLATE -> {
                                ThemeManager.theme = Slate()
                            }
                            ThemeConstants.HIGH_CONTRAST_DARK -> {
                                ThemeManager.theme = HighContrastDark()
                            }
                            ThemeConstants.OIL -> {
                                ThemeManager.theme = Oil()
                            }
                            ThemeConstants.MATERIAL_YOU_DARK -> {
                                ThemeManager.theme = MaterialYouDark()
                            }
                        }
                    }
                    Configuration.UI_MODE_NIGHT_NO -> {
                        when (AppearancePreferences.getLastLightTheme()) {
                            ThemeConstants.LIGHT_THEME -> {
                                ThemeManager.theme = LightTheme()
                            }
                            ThemeConstants.SOAPSTONE -> {
                                ThemeManager.theme = SoapStone()
                            }
                            ThemeConstants.HIGH_CONTRAST_LIGHT -> {
                                ThemeManager.theme = HighContrastLight()
                            }
                            ThemeConstants.MATERIAL_YOU_LIGHT -> {
                                ThemeManager.theme = MaterialYouLight()
                            }
                        }
                    }
                    Configuration.UI_MODE_NIGHT_UNDEFINED -> {
                        ThemeManager.theme = LightTheme()
                    }
                }
            }
            ThemeConstants.DAY_NIGHT -> {
                if (CalendarUtils.isDayOrNight()) {
                    when (AppearancePreferences.getLastLightTheme()) {
                        ThemeConstants.LIGHT_THEME -> {
                            ThemeManager.theme = LightTheme()
                        }
                        ThemeConstants.SOAPSTONE -> {
                            ThemeManager.theme = SoapStone()
                        }
                        ThemeConstants.HIGH_CONTRAST_LIGHT -> {
                            ThemeManager.theme = HighContrastLight()
                        }
                        ThemeConstants.MATERIAL_YOU_LIGHT -> {
                            ThemeManager.theme = MaterialYouLight()
                        }
                    }
                } else {
                    when (AppearancePreferences.getLastDarkTheme()) {
                        ThemeConstants.DARK_THEME -> {
                            ThemeManager.theme = DarkTheme()
                        }
                        ThemeConstants.AMOLED -> {
                            ThemeManager.theme = AMOLED()
                        }
                        ThemeConstants.SLATE -> {
                            ThemeManager.theme = Slate()
                        }
                        ThemeConstants.HIGH_CONTRAST_DARK -> {
                            ThemeManager.theme = HighContrastDark()
                        }
                        ThemeConstants.MATERIAL_YOU_DARK -> {
                            ThemeManager.theme = MaterialYouDark()
                        }
                        ThemeConstants.OIL -> {
                            ThemeManager.theme = Oil()
                        }
                    }
                }
            }
            ThemeConstants.MATERIAL_YOU_LIGHT -> {
                ThemeManager.theme = MaterialYouLight()
            }
            ThemeConstants.MATERIAL_YOU_DARK -> {
                ThemeManager.theme = MaterialYouDark()
            }
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
            ThemeConstants.HIGH_CONTRAST_DARK,
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
                return calendar < 7 || calendar > 18
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

    fun setBarColors(resources: Resources, window: Window) {
        when (AppearancePreferences.getTheme()) {
            ThemeConstants.LIGHT_THEME,
            ThemeConstants.SOAPSTONE,
            ThemeConstants.MATERIAL_YOU_LIGHT,
            ThemeConstants.HIGH_CONTRAST_LIGHT -> {
                lightBars(window)
            }
            ThemeConstants.DARK_THEME,
            ThemeConstants.AMOLED,
            ThemeConstants.HIGH_CONTRAST_DARK,
            ThemeConstants.SLATE,
            ThemeConstants.OIL,
            ThemeConstants.MATERIAL_YOU_DARK -> {
                darkBars(window)
            }
            ThemeConstants.FOLLOW_SYSTEM -> {
                when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                    Configuration.UI_MODE_NIGHT_YES -> {
                        darkBars(window)
                    }
                    Configuration.UI_MODE_NIGHT_NO -> {
                        lightBars(window)
                    }
                    Configuration.UI_MODE_NIGHT_UNDEFINED -> {
                        lightBars(window)
                    }
                }
            }
            ThemeConstants.DAY_NIGHT -> {
                if (CalendarUtils.isDayOrNight()) {
                    lightBars(window)
                } else {
                    darkBars(window)
                }
            }
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
