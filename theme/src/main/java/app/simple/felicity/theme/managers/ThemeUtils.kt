package app.simple.felicity.theme.managers

import android.content.res.Configuration
import android.content.res.Resources
import android.view.Window
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.shared.utils.CalendarUtils
import app.simple.felicity.theme.constants.ThemeConstants
import app.simple.felicity.theme.managers.ThemeUtils.isEffectiveAlbumArtTheme
import app.simple.felicity.theme.themes.dark.AMOLED
import app.simple.felicity.theme.themes.dark.AlbumArtDark
import app.simple.felicity.theme.themes.dark.DarkTheme
import app.simple.felicity.theme.themes.dark.HighContrastDark
import app.simple.felicity.theme.themes.dark.MaterialYouDark
import app.simple.felicity.theme.themes.dark.Oil
import app.simple.felicity.theme.themes.dark.Slate
import app.simple.felicity.theme.themes.light.AlbumArtLight
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
                            ThemeConstants.ALBUM_ART_DARK -> {
                                ThemeManager.theme = AlbumArtDark()
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
                            ThemeConstants.ALBUM_ART_LIGHT -> {
                                ThemeManager.theme = AlbumArtLight()
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
                        ThemeConstants.ALBUM_ART_LIGHT -> {
                            ThemeManager.theme = AlbumArtLight()
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
                        ThemeConstants.ALBUM_ART_DARK -> {
                            ThemeManager.theme = AlbumArtDark()
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
            ThemeConstants.ALBUM_ART_LIGHT -> {
                ThemeManager.theme = AlbumArtLight()
            }
            ThemeConstants.ALBUM_ART_DARK -> {
                ThemeManager.theme = AlbumArtDark()
            }
        }
    }

    fun isNightMode(resources: Resources): Boolean {
        when (AppearancePreferences.getTheme()) {
            ThemeConstants.LIGHT_THEME,
            ThemeConstants.SOAPSTONE,
            ThemeConstants.MATERIAL_YOU_LIGHT,
            ThemeConstants.HIGH_CONTRAST_LIGHT,
            ThemeConstants.ALBUM_ART_LIGHT -> {
                return false
            }

            ThemeConstants.DARK_THEME,
            ThemeConstants.AMOLED,
            ThemeConstants.HIGH_CONTRAST_DARK,
            ThemeConstants.SLATE,
            ThemeConstants.OIL,
            ThemeConstants.MATERIAL_YOU_DARK,
            ThemeConstants.ALBUM_ART_DARK -> {
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

    /**
     * Returns true if the album-art theme is the currently effective active theme.
     *
     * Handles direct [ThemeConstants.ALBUM_ART_LIGHT] or [ThemeConstants.ALBUM_ART_DARK]
     * selection, as well as [ThemeConstants.FOLLOW_SYSTEM] and [ThemeConstants.DAY_NIGHT]
     * modes where the user's chosen light or dark sub-theme may be an album-art variant.
     *
     * @param resources The current [Resources] instance used to resolve the system night-mode
     *                  flag when the theme is [ThemeConstants.FOLLOW_SYSTEM].
     * @return true if album-art colors should be applied.
     * @author Hamza417
     */
    fun isEffectiveAlbumArtTheme(resources: Resources): Boolean {
        return when (AppearancePreferences.getTheme()) {
            ThemeConstants.ALBUM_ART_LIGHT,
            ThemeConstants.ALBUM_ART_DARK -> true

            ThemeConstants.FOLLOW_SYSTEM -> {
                when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                    Configuration.UI_MODE_NIGHT_YES ->
                        AppearancePreferences.getLastDarkTheme() == ThemeConstants.ALBUM_ART_DARK
                    Configuration.UI_MODE_NIGHT_NO ->
                        AppearancePreferences.getLastLightTheme() == ThemeConstants.ALBUM_ART_LIGHT
                    else -> false
                }
            }

            ThemeConstants.DAY_NIGHT -> {
                if (CalendarUtils.isDayOrNight()) {
                    AppearancePreferences.getLastLightTheme() == ThemeConstants.ALBUM_ART_LIGHT
                } else {
                    AppearancePreferences.getLastDarkTheme() == ThemeConstants.ALBUM_ART_DARK
                }
            }

            else -> false
        }
    }

    /**
     * Returns true if the currently active album-art theme variant is the dark one.
     *
     * This is only meaningful when [isEffectiveAlbumArtTheme] returns true. The result
     * determines whether [app.simple.felicity.theme.themes.dark.AlbumArtDark] or
     * [app.simple.felicity.theme.themes.light.AlbumArtLight] is pushed into [ThemeManager].
     *
     * @param resources The current [Resources] instance.
     * @return true for the dark variant, false for the light variant.
     * @author Hamza417
     */
    fun isEffectiveAlbumArtDark(resources: Resources): Boolean {
        return when (AppearancePreferences.getTheme()) {
            ThemeConstants.ALBUM_ART_DARK -> true

            ThemeConstants.FOLLOW_SYSTEM -> {
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                        Configuration.UI_MODE_NIGHT_YES &&
                        AppearancePreferences.getLastDarkTheme() == ThemeConstants.ALBUM_ART_DARK
            }

            ThemeConstants.DAY_NIGHT -> {
                !CalendarUtils.isDayOrNight() &&
                        AppearancePreferences.getLastDarkTheme() == ThemeConstants.ALBUM_ART_DARK
            }

            else -> false
        }
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
            ThemeConstants.HIGH_CONTRAST_LIGHT,
            ThemeConstants.ALBUM_ART_LIGHT -> {
                lightBars(window)
            }
            ThemeConstants.DARK_THEME,
            ThemeConstants.AMOLED,
            ThemeConstants.HIGH_CONTRAST_DARK,
            ThemeConstants.SLATE,
            ThemeConstants.OIL,
            ThemeConstants.MATERIAL_YOU_DARK,
            ThemeConstants.ALBUM_ART_DARK -> {
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
