package app.simple.felicity.theme.managers

import android.os.Build
import app.simple.felicity.theme.accents.AlbumArt
import app.simple.felicity.theme.accents.Emerald
import app.simple.felicity.theme.accents.Felicity
import app.simple.felicity.theme.accents.GrapeFruit
import app.simple.felicity.theme.accents.Lavender
import app.simple.felicity.theme.accents.MaterialYouAccent
import app.simple.felicity.theme.accents.Midnight
import app.simple.felicity.theme.accents.Rose
import app.simple.felicity.theme.accents.Tangerine
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.models.Theme
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

object ThemeManager {
    private val listeners = mutableSetOf<ThemeChangedListener>()

    var theme: Theme = Theme()
        set(value) {
            val bool = field != value
            field = value
            listeners.forEach { listener -> listener.onThemeChanged(value, bool) }
        }

    var accent = Accent(0, 0, "Felicity")
        set(value) {
            field = value
            listeners.forEach { it.onAccentChanged(accent) }
        }

    fun addListener(listener: ThemeChangedListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ThemeChangedListener) {
        listeners.remove(listener)
    }

    fun getAccentByName(name: String): Accent {
        return when (name) {
            Felicity.IDENTIFIER -> Felicity()
            MaterialYouAccent.IDENTIFIER -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MaterialYouAccent()
                } else {
                    Felicity()
                }
            }
            GrapeFruit.IDENTIFIER -> GrapeFruit()
            Emerald.IDENTIFIER -> Emerald()
            Tangerine.IDENTIFIER -> Tangerine()
            Rose.IDENTIFIER -> Rose()
            Lavender.IDENTIFIER -> Lavender()
            Midnight.IDENTIFIER -> Midnight()
            else -> Felicity()
        }
    }

    fun updateAccentColor(name: String) {
        accent = getAccentByName(name)
    }

    fun getAllAccents(): List<Accent> {
        val list = mutableListOf<Accent>()

        list.add(Felicity())
        list.add(AlbumArt())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(MaterialYouAccent())
        }

        list.add(GrapeFruit())
        list.add(Emerald())
        list.add(Tangerine())
        list.add(Rose())
        list.add(Lavender())
        list.add(Midnight())

        return list
    }

    fun getLightThemes(): List<Theme> {
        return listOf(
                LightTheme(),
                SoapStone(),
                AlbumArtLight(),
                MaterialYouLight(),
                HighContrastLight()
        )
    }

    fun getDarkThemes(): List<Theme> {
        return listOf(
                DarkTheme(),
                AMOLED(),
                Oil(),
                Slate(),
                AlbumArtDark(),
                MaterialYouDark(),
                HighContrastDark()
        )
    }
}
