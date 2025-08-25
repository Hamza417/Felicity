package app.simple.felicity.theme.managers

import android.os.Build
import app.simple.felicity.theme.accents.Felicity
import app.simple.felicity.theme.accents.MaterialYouAccent
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme

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
            "Felicity" -> Felicity()
            "Material You" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MaterialYouAccent()
                } else {
                    Felicity()
                }
            }
            else -> Felicity()
        }
    }

    fun getAllAccents(): List<Accent> {
        val list = mutableListOf<Accent>()

        list.add(Felicity())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(MaterialYouAccent())
        }

        return list
    }
}
